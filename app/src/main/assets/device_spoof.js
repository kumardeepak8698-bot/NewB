/**
 * DeviceCloner - Default Device Spoofing Script Template (Frida Gadget JS Hook)
 * Overrides native Android specifications to spoof hardware telemetry.
 */

Java.perform(function() {
    console.log("[+] DeviceCloner: Default spoofing template initialized");

    var Build = Java.use("android.os.Build");
    var BuildVersion = Java.use("android.os.Build${'$'}VERSION");
    var Secure = Java.use("android.provider.Settings${'$'}Secure");
    var TelephonyManager = Java.use("android.telephony.TelephonyManager");
    var WifiInfo = Java.use("android.net.wifi.WifiInfo");

    // Helper hook variable template
    var spoofModel = "Pixel 8 Pro";
    var spoofBrand = "Google";
    var spoofAndroidId = "3fa85f64d234a65b";
    var spoofImei = "359846201245672";
    var spoofMac = "4a:56:e9:12:ef:c3";

    // Modifying android.os.Build values
    try {
        var modelField = Build.class.getDeclaredField("MODEL");
        modelField.setAccessible(true);
        modelField.set(null, spoofModel);
    } catch (e) {
        Build.MODEL.value = spoofModel;
    }

    try {
        var brandField = Build.class.getDeclaredField("BRAND");
        brandField.setAccessible(true);
        brandField.set(null, spoofBrand);
    } catch (e) {
        Build.BRAND.value = spoofBrand;
    }

    // Settings Android_ID hook
    Secure.getString.implementation = function(contentResolver, name) {
        if (name === "android_id") {
            console.log("[Frida] Intercepted query for android_id. Returning spoofed ID.");
            return spoofAndroidId;
        }
        return this.getString(contentResolver, name);
    };

    console.log("[+] DeviceCloner Hooks applied successfully.");
});
