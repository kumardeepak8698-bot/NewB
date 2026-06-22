package com.example

import com.example.models.SpoofProfile
import kotlin.random.Random

object DeviceRandomizer {

    private val BRANDS = listOf("Samsung", "Google", "Xiaomi", "OnePlus", "Sony")
    
    private val MODELS_MAP = mapOf(
        "Samsung" to listOf("Galaxy S24 Ultra", "Galaxy Z Fold 6", "Galaxy A55"),
        "Google" to listOf("Pixel 8 Pro", "Pixel Fold", "Pixel 8a"),
        "Xiaomi" to listOf("Xiaomi 14 Pro", "Redmi Note 13 Pro", "Poco F6"),
        "OnePlus" to listOf("OnePlus 12", "OnePlus Nord 4", "OnePlus Open"),
        "Sony" to listOf("Xperia 1 VI", "Xperia 5 V", "Xperia 10 VI")
    )

    private val OPERATORS = listOf("Verizon", "T-Mobile", "AT&T", "Vodafone", "Jio", "Orange", "Telefonica")
    private val SSIDS = listOf("Home_Network_Ext", "Linksys_DualBand", "Starlink_Guest", "Office_Secure_WiFi", "Airport_Free_WiFi")

    fun generateRandomProfile(profileName: String): SpoofProfile {
        val brand = BRANDS[Random.nextInt(BRANDS.size)]
        val models = MODELS_MAP[brand] ?: listOf("Generic Phone")
        val model = models[Random.nextInt(models.size)]
        
        val manufacturer = brand
        val device = model.lowercase().replace(" ", "_")
        val product = device + "_global"
        val board = "msm8998" // Generic or modern chipset board
        val hardware = "qcom" // Qualcomm e.g.
        
        val androidVersions = listOf("11", "12", "13", "14")
        val androidVersion = androidVersions[Random.nextInt(androidVersions.size)]
        
        val buildId = generateBuildId(androidVersion)
        val fingerprint = "${brand.lowercase()}/${device}/${device}:${androidVersion}/${buildId}/${Random.nextInt(100000, 999999)}:user/release-keys"
        
        return SpoofProfile(
            profileName = profileName,
            brand = brand,
            model = model,
            manufacturer = manufacturer,
            product = product,
            device = device,
            board = board,
            hardware = hardware,
            androidVersion = androidVersion,
            buildId = buildId,
            fingerprint = fingerprint,
            imei = generateRandomImei(),
            androidId = generateRandomAndroidId(),
            wifiMac = generateRandomMac(),
            ssid = SSIDS[Random.nextInt(SSIDS.size)],
            simSerial = generateRandomSimSerial(),
            simOperator = OPERATORS[Random.nextInt(OPERATORS.size)]
        )
    }

    private fun generateBuildId(androidVersion: String): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val numbers = "0123456789"
        val first = chars[Random.nextInt(chars.length)]
        val second = chars[Random.nextInt(chars.length)]
        val third = numbers[Random.nextInt(numbers.length)]
        val fourth = numbers[Random.nextInt(numbers.length)]
        val fiveth = chars[Random.nextInt(chars.length)]
        return "$first$second$third$fourth$fiveth.240622.0${Random.nextInt(10, 99)}"
    }

    private fun generateRandomImei(): String {
        // IMEIs are 15 digits. Let's make a real looking one via Luhn algorithm or simple valid structure
        val sb = StringBuilder("3587") // Standard TAC
        while (sb.length < 14) {
            sb.append(Random.nextInt(10))
        }
        sb.append(calculateLuhnCheckDigit(sb.toString()))
        return sb.toString()
    }

    private fun generateRandomAndroidId(): String {
        val chars = "0123456789abcdef"
        return (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun generateRandomMac(): String {
        return (1..6).joinToString(":") { 
            String.format("%02x", Random.nextInt(256)) 
        }
    }

    private fun generateRandomSimSerial(): String {
        // Sim serial numbers are 19 digits starting with 89
        val sb = StringBuilder("89")
        while (sb.length < 19) {
            sb.append(Random.nextInt(10))
        }
        return sb.toString()
    }

    private fun calculateLuhnCheckDigit(number: String): Int {
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            var n = number[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) {
                    n = (n % 10) + 1
                }
            }
            sum += n
            alternate = !alternate
        }
        val check = (10 - (sum % 10)) % 10
        return check
    }
}
