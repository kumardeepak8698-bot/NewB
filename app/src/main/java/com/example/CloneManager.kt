package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.ClonerDao
import com.example.models.AppInfo
import com.example.models.ClonedApp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CloneManager {

    private const val TAG = "CloneManager"

    /**
     * Scan and retrieve all installed non-system and system applications
     */
    suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val appsList = mutableListOf<AppInfo>()
        try {
            val pm = context.packageManager
            // Check flags depending on SDK version
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                
                // Exclude this app itself
                if (pkg.packageName == context.packageName) continue

                val appName = pm.getApplicationLabel(appInfo).toString()
                val packageName = pkg.packageName
                val versionName = pkg.versionName ?: "1.0"
                val apkPath = appInfo.sourceDir ?: ""
                
                // Determine if it was loaded from system image
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                if (apkPath.isNotEmpty()) {
                    val file = File(apkPath)
                    if (file.exists()) {
                        appsList.add(
                            AppInfo(
                                appName = appName,
                                packageName = packageName,
                                versionName = versionName,
                                apkPath = apkPath,
                                isSystemApp = isSystem,
                                sizeBytes = file.length()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing installed packages: ${e.message}", e)
        }
        // Group and return, placing user apps at the top
        appsList.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
    }

    /**
     * Executes the end-to-end app cloning process.
     * 1. Copies the base APK to local cache.
     * 2. Modifies manifest variables to clone the bundle structure.
     * 3. Regenerates manifest tables and signatures using Signer.
     * 4. Updates Room database telemetry.
     */
    suspend fun cloneApp(
        context: Context,
        app: AppInfo,
        targetPackageName: String,
        profileId: Long,
        dao: ClonerDao,
        onProgress: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress("Starting clone workflow for '${app.appName}'...")
        
        val record = ClonedApp(
            originalPackageName = app.packageName,
            originalAppName = app.appName,
            clonedPackageName = targetPackageName,
            clonedVersion = app.versionName,
            clonedApkPath = "",
            signingKeyType = "Self-Signed Keystore",
            spoofProfileId = profileId,
            creationTimeMs = System.currentTimeMillis(),
            status = "EXTRACTING",
            sizeMb = app.sizeBytes / (1024.0 * 1024.0)
        )
        
        val recordId = dao.insertClonedApp(record)
        var updatedRecord = record.copy(id = recordId)
        
        try {
            // Step 1: Copy base APK to secure local directory
            onProgress("Extracting original APK payload of: ${app.packageName}...")
            val outputDir = File(context.filesDir, "cloned_apks")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            val tempApkFile = File(outputDir, "temp_${System.currentTimeMillis()}.apk")
            val targetApkFile = File(outputDir, "${targetPackageName}_cloned.apk")
            
            // Delete potential stale file
            if (targetApkFile.exists()) {
                targetApkFile.delete()
            }

            // Copy file from sourceDir
            val sourceFile = File(app.apkPath)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(tempApkFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Step 2: Binary AndroidManifest modifier
            // For a highly resilient clone engine on Android, we perform binary AXML patching.
            // When we do simple cloning, we locate the original package name string inside the AXML pool
            // and change it to the target name.
            onProgress("Decompiling Manifest and compiling custom configuration attributes...")
            patchBinaryManifestPackageName(tempApkFile, app.packageName, targetPackageName)

            // Step 3: Run APK signer to rebuild signatures
            dao.updateClonedApp(updatedRecord.copy(status = "REBUILDING_SIGNATURES"))
            val signedSuccessfully = Signer.signApk(context, tempApkFile, targetApkFile) { signStatus ->
                onProgress(signStatus)
            }
            
            // Cleanup temp file
            if (tempApkFile.exists()) {
                tempApkFile.delete()
            }

            if (signedSuccessfully && targetApkFile.exists()) {
                onProgress("App successfully cloned! Preparing package signature logs...")
                updatedRecord = updatedRecord.copy(
                    clonedApkPath = targetApkFile.absolutePath,
                    status = "SUCCESS",
                    sizeMb = targetApkFile.length() / (1024.0 * 1024.0)
                )
                dao.updateClonedApp(updatedRecord)
                return@withContext true
            } else {
                onProgress("Failed signing APK. Check device resource restrictions.")
                dao.updateClonedApp(updatedRecord.copy(status = "FAILED", errorDetails = "Signing failed"))
                return@withContext false
            }
        } catch (e: Exception) {
            onProgress("Cloning error: ${e.message}")
            dao.updateClonedApp(updatedRecord.copy(status = "FAILED", errorDetails = e.message))
            return@withContext false
        }
    }

    /**
     * Patches the package name inside the compiled binary AndroidManifest.xml inside the APK zip.
     * It parses the ZIP looking for AndroidManifest.xml, scans the string-pool, patches occurrences
     * of original package name with cloned package name, and saves it.
     */
    private fun patchBinaryManifestPackageName(apkFile: File, oldPkg: String, newPkg: String) {
        try {
            Log.d(TAG, "Patching binary manifest pool from $oldPkg -> $newPkg")
            val tempPatcherFile = File(apkFile.parentFile, "patched_temp_${System.currentTimeMillis()}.apk")
            
            java.util.zip.ZipInputStream(FileInputStream(apkFile)).use { zis ->
                java.util.zip.ZipOutputStream(FileOutputStream(tempPatcherFile)).use { zos ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        // Skip existing signature files under META-INF
                        val isSigFile = name.startsWith("META-INF/") && (
                                name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC") || name.endsWith("MANIFEST.MF")
                                )
                        
                        if (!isSigFile) {
                            val newEntry = java.util.zip.ZipEntry(name)
                            zos.putNextEntry(newEntry)
                            
                            if (name == "AndroidManifest.xml") {
                                val originalBytes = zis.readBytes()
                                val patchedBytes = patchAxml(originalBytes, oldPkg, newPkg)
                                zos.write(patchedBytes)
                            } else {
                                zis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            
            // Delete original file and rename patched to original
            if (apkFile.exists()) {
                apkFile.delete()
            }
            tempPatcherFile.renameTo(apkFile)
            Log.d(TAG, "Manifest binary substitution is complete.")
        } catch (e: Exception) {
            Log.e(TAG, "Error patching manifest, running standard clone: ${e.message}", e)
        }
    }

    private fun patchAxml(axmlBytes: ByteArray, oldPkg: String, newPkg: String): ByteArray {
        // 1. Read file header
        val magic = readIntle(axmlBytes, 0)
        if (magic != 0x00080003) {
            Log.e(TAG, "Invalid binary XML magic")
            return axmlBytes
        }
        
        val stringPoolHeaderOffset = 8
        val chunkType = readIntle(axmlBytes, stringPoolHeaderOffset)
        val chunkSize = readIntle(axmlBytes, stringPoolHeaderOffset + 4)
        val stringCount = readIntle(axmlBytes, stringPoolHeaderOffset + 8)
        val styleCount = readIntle(axmlBytes, stringPoolHeaderOffset + 12)
        val flags = readIntle(axmlBytes, stringPoolHeaderOffset + 16)
        val stringsStart = readIntle(axmlBytes, stringPoolHeaderOffset + 20)
        val stylesStart = readIntle(axmlBytes, stringPoolHeaderOffset + 24)
        
        val isUtf8 = (flags and (1 shl 8)) != 0
        
        // 2. Extract all strings from current pool
        val strings = mutableListOf<String>()
        val offsetsOffset = stringPoolHeaderOffset + 28
        for (i in 0 until stringCount) {
            val offset = readIntle(axmlBytes, offsetsOffset + i * 4)
            val stringDataOffset = stringPoolHeaderOffset + stringsStart + offset
            
            val str = if (isUtf8) {
                // Read UTF-8
                // UTF-8 length chars: 1 or 2 bytes
                var u16len = axmlBytes[stringDataOffset].toInt() and 0xFF
                var curOffset = stringDataOffset + 1
                if ((u16len and 0x80) != 0) {
                    u16len = ((u16len and 0x7F) shl 8) or (axmlBytes[curOffset].toInt() and 0xFF)
                    curOffset++
                }
                
                // UTF-8 length bytes: 1 or 2 bytes
                var u8len = axmlBytes[curOffset].toInt() and 0xFF
                curOffset++
                if ((u8len and 0x80) != 0) {
                    u8len = ((u8len and 0x7F) shl 8) or (axmlBytes[curOffset].toInt() and 0xFF)
                    curOffset++
                }
                
                String(axmlBytes, curOffset, u8len, Charsets.UTF_8)
            } else {
                // Read UTF-16
                // Length in chars (2 or 4 bytes)
                var u16len = readShortle(axmlBytes, stringDataOffset)
                var curOffset = stringDataOffset + 2
                if ((u16len and 0x8000) != 0) {
                    u16len = ((u16len and 0x7FFF) shl 16) or readShortle(axmlBytes, curOffset)
                    curOffset += 2
                }
                // UTF-16 chars are 2 bytes each
                val charBytes = ByteArray(u16len * 2)
                System.arraycopy(axmlBytes, curOffset, charBytes, 0, u16len * 2)
                String(charBytes, Charsets.UTF_16LE)
            }
            strings.add(str)
        }
        
        // 3. Patch strings
        val patchedStrings = strings.map { s ->
            if (s == oldPkg) {
                newPkg
            } else if (s.startsWith("$oldPkg.")) {
                s.replace(oldPkg, newPkg)
            } else {
                s
            }
        }
        
        // 4. Rebuild String Pool data
        val stringDataBuffer = java.io.ByteArrayOutputStream()
        val newOffsets = IntArray(stringCount)
        
        for (i in 0 until stringCount) {
            newOffsets[i] = stringDataBuffer.size()
            val s = patchedStrings[i]
            
            if (isUtf8) {
                val sBytes = s.toByteArray(Charsets.UTF_8)
                val charLen = s.length
                val byteLen = sBytes.size
                
                // Write length description
                writeUtf8Len(stringDataBuffer, charLen)
                writeUtf8Len(stringDataBuffer, byteLen)
                
                // Write payload
                stringDataBuffer.write(sBytes)
                stringDataBuffer.write(0) // Null term
            } else {
                val charLen = s.length
                // Write length in chars
                writeUtf16Len(stringDataBuffer, charLen)
                
                // Write payload (UTF-16LE)
                val sBytes = s.toByteArray(Charsets.UTF_16LE)
                stringDataBuffer.write(sBytes)
                stringDataBuffer.write(0) // Null term (2 bytes)
                stringDataBuffer.write(0)
            }
        }
        
        // Align string pool data to 4-byte boundaries
        val dataSize = stringDataBuffer.size()
        val padding = (4 - (dataSize % 4)) % 4
        for (p in 0 until padding) {
            stringDataBuffer.write(0)
        }
        
        val newStringData = stringDataBuffer.toByteArray()
        
        // 5. Styles data (let's copy them exactly as they were if styleCount > 0)
        val styleDataBytes = if (styleCount > 0 && stylesStart > 0) {
            val styleStartOffset = stringPoolHeaderOffset + stylesStart
            val styleEndOffset = stringPoolHeaderOffset + chunkSize
            val stylesLength = styleEndOffset - styleStartOffset
            val b = ByteArray(stylesLength)
            System.arraycopy(axmlBytes, styleStartOffset, b, 0, stylesLength)
            b
        } else {
            ByteArray(0)
        }
        
        // 6. Build the new String Pool Header and Chunk
        val newStringsStart = 28 + stringCount * 4 + styleCount * 4
        val newStylesStart = if (styleCount > 0) newStringsStart + newStringData.size else 0
        val newChunkSize = newStringsStart + newStringData.size + styleDataBytes.size
        
        val outPoolStream = java.io.ByteArrayOutputStream()
        writeIntle(outPoolStream, chunkType)
        writeIntle(outPoolStream, newChunkSize)
        writeIntle(outPoolStream, stringCount)
        writeIntle(outPoolStream, styleCount)
        writeIntle(outPoolStream, flags)
        writeIntle(outPoolStream, newStringsStart)
        writeIntle(outPoolStream, newStylesStart)
        
        // Write new offsets
        for (off in newOffsets) {
            writeIntle(outPoolStream, off)
        }
        // Styles offsets (if any, copy them)
        if (styleCount > 0) {
            val stylesOffsetSource = stringPoolHeaderOffset + 28 + stringCount * 4
            outPoolStream.write(axmlBytes, stylesOffsetSource, styleCount * 4)
        }
        
        // Write string data payload
        outPoolStream.write(newStringData)
        // Write style data payload
        if (styleDataBytes.isNotEmpty()) {
            outPoolStream.write(styleDataBytes)
        }
        
        val newPoolBytes = outPoolStream.toByteArray()
        
        // 7. Assemble the full binary AXML bytes
        val finalStream = java.io.ByteArrayOutputStream()
        writeIntle(finalStream, magic)
        
        // Write the new file size (sum of file header size, new pool size, and the rest of the AXML file)
        val restOffset = stringPoolHeaderOffset + chunkSize
        val restLength = axmlBytes.size - restOffset
        val newFileSize = 8 + newPoolBytes.size + restLength
        writeIntle(finalStream, newFileSize)
        
        // Write new pool
        finalStream.write(newPoolBytes)
        // Write rest of the file
        finalStream.write(axmlBytes, restOffset, restLength)
        
        return finalStream.toByteArray()
    }

    private fun readIntle(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShortle(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun writeIntle(stream: java.io.OutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value ushr 8) and 0xFF)
        stream.write((value ushr 16) and 0xFF)
        stream.write((value ushr 24) and 0xFF)
    }

    private fun writeUtf8Len(stream: java.io.OutputStream, len: Int) {
        if (len > 0x7F) {
            stream.write(((len ushr 8) and 0x7F) or 0x80)
        }
        stream.write(len and 0xFF)
    }

    private fun writeUtf16Len(stream: java.io.OutputStream, len: Int) {
        if (len > 0x7FFF) {
            val high = ((len ushr 16) and 0x7FFF) or 0x8000
            stream.write(high and 0xFF)
            stream.write((high ushr 8) and 0xFF)
        }
        stream.write(len and 0xFF)
        stream.write((len ushr 8) and 0xFF)
    }

    /**
     * Standard APK installation prompt via Android PackageInstaller Client APIs
     */
    fun promptInstallClonedApk(context: Context, record: ClonedApp) {
        try {
            val apkFile = File(record.clonedApkPath)
            if (!apkFile.exists()) {
                Log.e(TAG, "APK path does not exist: ${record.clonedApkPath}")
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error prompting app install: ${e.message}", e)
        }
    }
}
