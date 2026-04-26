package com.verifai.sdk

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.*

/**
 * Internal signal collector - not exposed to SDK users
 */
internal object SignalCollector {
    
    private lateinit var appContext: Context
    private var deviceSalt: String? = null
    
    fun init(context: Context) {
        appContext = context.applicationContext
        
        // Get or create device salt (stored forever)
        val prefs = context.getSharedPreferences("verifai_internal", Context.MODE_PRIVATE)
        deviceSalt = prefs.getString("device_salt", null)
        if (deviceSalt == null) {
            deviceSalt = UUID.randomUUID().toString()
            prefs.edit().putString("device_salt", deviceSalt).apply()
        }
    }
    
    /**
     * Collect and hash all device signals.
     * Returns only hashes - raw data never leaves this function.
     */
    suspend fun collectSignalHashes(cloudProjectNumber: Long): Map<String, String> = withContext(Dispatchers.IO) {
        val salt = deviceSalt ?: UUID.randomUUID().toString()
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        
        // Collect raw signals
        val device = collectDevice()
        val telephony = collectTelephony()
        val keyboards = collectKeyboards()
        val apps = collectApps()
        val integrity = collectIntegrity()
        val defaultApps = collectDefaultApps()
        val personalization = collectPersonalization()
        val network = collectNetwork()
        
        // Get Play Integrity token
        val playIntegrityToken = try {
            getPlayIntegrityToken(cloudProjectNumber)
        } catch (e: Exception) {
            ""
        }
        
        // Hash everything - raw data stays here
        mapOf(
            "androidIdHash" to hash(salt, androidId),
            "playIntegrityHash" to hash(salt, playIntegrityToken),
            "deviceHash" to hash(salt, device["manufacturer"], device["model"], device["brand"], device["fingerprint"], device["screenWidth"], device["screenHeight"]),
            "localeHash" to hash(salt, device["timezone"], device["language"], device["country"]),
            "carrierHash" to hash(salt, telephony["simCountry"], telephony["carrierName"]),
            "keyboardHash" to hash(salt, keyboards["keyboardList"]),
            "integrityHash" to hash(salt, integrity["isEmulator"], integrity["isRooted"]),
            "appsHash" to hash(salt, apps["totalApps"]),
            "defaultAppsHash" to hash(salt, defaultApps["defaultBrowser"]),
            "personalizationHash" to hash(salt, personalization["ringtoneName"], personalization["darkMode"]),
            "networkHash" to hash(salt, network["wifiState"]),
            "usageHash" to hash(salt, device["uptimeHours"])
        )
    }
    
    /**
     * Get unique device identifier hash
     */
    fun getDeviceIdHash(): String {
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        return hash(deviceSalt ?: "", androidId, Build.MODEL, Build.BRAND)
    }
    
    private fun collectDevice(): Map<String, String> = mapOf(
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "brand" to Build.BRAND,
        "fingerprint" to Build.FINGERPRINT,
        "screenWidth" to appContext.resources.displayMetrics.widthPixels.toString(),
        "screenHeight" to appContext.resources.displayMetrics.heightPixels.toString(),
        "timezone" to TimeZone.getDefault().id,
        "language" to Locale.getDefault().language,
        "country" to Locale.getDefault().country,
        "uptimeHours" to (android.os.SystemClock.elapsedRealtime() / 3600000).toString()
    )
    
    private fun collectTelephony(): Map<String, String> {
        return try {
            val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            mapOf(
                "carrierName" to (tm.networkOperatorName ?: ""),
                "simCountry" to (tm.simCountryIso?.uppercase() ?: "")
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun collectKeyboards(): Map<String, String> {
        return try {
            val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val keyboards = imm.enabledInputMethodList
            val keyboardNames = keyboards.map { it.loadLabel(appContext.packageManager).toString() }.sorted()
            mapOf("keyboardList" to keyboardNames.joinToString(","))
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun collectApps(): Map<String, String> {
        return try {
            val pm = appContext.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            mapOf("totalApps" to packages.size.toString())
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun collectIntegrity(): Map<String, String> {
        val isEmulator = Build.FINGERPRINT.contains("generic") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK") ||
                Build.MANUFACTURER.contains("Genymotion")
        
        val isRooted = listOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su")
            .any { java.io.File(it).exists() }
        
        return mapOf(
            "isEmulator" to isEmulator.toString(),
            "isRooted" to isRooted.toString()
        )
    }
    
    private fun collectDefaultApps(): Map<String, String> {
        return try {
            val pm = appContext.packageManager
            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("http://"))
            val browserResolve = pm.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val browser = browserResolve?.activityInfo?.let { pm.getApplicationLabel(it.applicationInfo).toString() } ?: ""
            mapOf("defaultBrowser" to browser)
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun collectPersonalization(): Map<String, String> {
        val darkMode = try {
            val nightMode = appContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES).toString()
        } catch (e: Exception) { "false" }
        
        val ringtoneName = try {
            val ringtoneUri = android.media.RingtoneManager.getActualDefaultRingtoneUri(appContext, android.media.RingtoneManager.TYPE_RINGTONE)
            val ringtone = android.media.RingtoneManager.getRingtone(appContext, ringtoneUri)
            ringtone?.getTitle(appContext) ?: "Default"
        } catch (e: Exception) { "Default" }
        
        return mapOf(
            "darkMode" to darkMode,
            "ringtoneName" to ringtoneName
        )
    }
    
    private fun collectNetwork(): Map<String, String> {
        return try {
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            mapOf("wifiState" to wifiManager.isWifiEnabled.toString())
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private suspend fun getPlayIntegrityToken(cloudProjectNumber: Long): String {
        val integrityManager = IntegrityManagerFactory.create(appContext)
        val nonce = UUID.randomUUID().toString()
        
        val request = IntegrityTokenRequest.builder()
            .setCloudProjectNumber(cloudProjectNumber)
            .setNonce(nonce)
            .build()
        
        val response = integrityManager.requestIntegrityToken(request).await()
        return response.token()
    }
    
    private fun hash(vararg inputs: String?): String {
        val combined = inputs.filterNotNull().joinToString("|")
        val bytes = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
