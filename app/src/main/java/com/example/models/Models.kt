package com.example.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spoof_profiles")
data class SpoofProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileName: String,
    val brand: String,
    val model: String,
    val manufacturer: String,
    val product: String,
    val device: String,
    val board: String,
    val hardware: String,
    val androidVersion: String,
    val buildId: String,
    val fingerprint: String,
    val imei: String,
    val androidId: String,
    val wifiMac: String,
    val ssid: String,
    val simSerial: String,
    val simOperator: String
)

@Entity(tableName = "cloned_apps")
data class ClonedApp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val originalPackageName: String,
    val originalAppName: String,
    val clonedPackageName: String,
    val clonedVersion: String,
    val clonedApkPath: String,
    val signingKeyType: String, // "Default Debug", "Custom Release"
    val spoofProfileId: Long, // 0 for none, or references a SpoofProfile ID
    val creationTimeMs: Long,
    val status: String, // "COMPILING", "SIGNING", "SUCCESS", "FAILED"
    val errorDetails: String? = null,
    val sizeMb: Double
)

data class AppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val apkPath: String,
    val isSystemApp: Boolean,
    val sizeBytes: Long
)
