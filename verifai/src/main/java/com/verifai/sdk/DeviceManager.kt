package com.verifai.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Internal device manager - handles registration and verification flow.
 * All operations go through the REST API (ApiClient) — no direct Firestore access.
 */
internal object DeviceManager {
    
    private const val CLOUD_PROJECT_NUMBER = 71845524469L
    
    /**
     * Register device - collects signal hashes and sends to API
     */
    suspend fun register(config: VerifAI.Config, userId: String): VerifAI.RegistrationResult = withContext(Dispatchers.IO) {
        try {
            val signalHashes = SignalCollector.collectSignalHashes(CLOUD_PROJECT_NUMBER)
            val deviceIdHash = SignalCollector.getDeviceIdHash()
            
            val body = JSONObject().apply {
                put("userId", userId)
                put("deviceIdHash", deviceIdHash)
                put("signalHashes", JSONObject(signalHashes))
                put("deviceModel", android.os.Build.MODEL)
                put("deviceBrand", android.os.Build.BRAND)
            }
            
            val response = ApiClient.post(config, "/registerDevice", body)
            val success = response.optBoolean("success", false)
            val deviceId = response.optString("deviceId", deviceIdHash.take(16))
            
            if (success) {
                // Store locally
                val prefs = config.context.getSharedPreferences("verifai_device", android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("certificate", response.optString("masterHash", ""))
                    .putString("device_id", deviceId)
                    .putInt("login_count", 1)
                    .putLong("first_login", System.currentTimeMillis())
                    .apply()
            }
            
            VerifAI.RegistrationResult(
                success = success,
                deviceId = deviceId,
                error = if (!success) response.optString("error", "Registration failed") else null
            )
            
        } catch (e: Exception) {
            VerifAI.RegistrationResult(
                success = false,
                error = e.message
            )
        }
    }
    
    /**
     * Verify device - sends signal hashes to API for comparison
     */
    suspend fun verify(config: VerifAI.Config, userId: String): VerifAI.VerificationResult = withContext(Dispatchers.IO) {
        try {
            val signalHashes = SignalCollector.collectSignalHashes(CLOUD_PROJECT_NUMBER)
            val deviceIdHash = SignalCollector.getDeviceIdHash()
            val publicIP = NetworkUtils.getPublicIP()
            
            val body = JSONObject().apply {
                put("userId", userId)
                put("deviceIdHash", deviceIdHash)
                put("signalHashes", JSONObject(signalHashes))
                put("publicIP", publicIP)
                put("deviceModel", android.os.Build.MODEL)
                put("deviceBrand", android.os.Build.BRAND)
            }
            
            val response = ApiClient.post(config, "/verifyDevice", body)
            val status = response.optString("status", "ERROR")
            
            val verifaiStatus = when (status) {
                "TRUSTED" -> VerifAI.Status.TRUSTED
                "NEW_DEVICE" -> VerifAI.Status.NEW_DEVICE
                "PENDING" -> VerifAI.Status.PENDING
                "REJECTED" -> VerifAI.Status.REJECTED
                else -> VerifAI.Status.ERROR
            }
            
            val trustLevelStr = response.optString("trustLevel", "BASELINE")
            val trustLevel = when (trustLevelStr) {
                "VERY_HIGH" -> VerifAI.TrustLevel.VERY_HIGH
                "HIGH" -> VerifAI.TrustLevel.HIGH
                "MEDIUM" -> VerifAI.TrustLevel.MEDIUM
                else -> VerifAI.TrustLevel.BASELINE
            }
            
            // Update local storage on trusted
            if (verifaiStatus == VerifAI.Status.TRUSTED) {
                val prefs = config.context.getSharedPreferences("verifai_device", android.content.Context.MODE_PRIVATE)
                val count = prefs.getInt("login_count", 0) + 1
                prefs.edit()
                    .putString("device_id", response.optString("deviceId", deviceIdHash.take(16)))
                    .putInt("login_count", count)
                    .putLong("last_login", System.currentTimeMillis())
                    .apply()
            }
            
            VerifAI.VerificationResult(
                status = verifaiStatus,
                trustScore = response.optInt("trustScore", 0),
                trustLevel = trustLevel,
                deviceId = response.optString("deviceId", deviceIdHash.take(16)),
                sessionId = response.optString("sessionId", null),
                error = response.optString("error", null)
            )
            
        } catch (e: Exception) {
            VerifAI.VerificationResult(
                status = VerifAI.Status.ERROR,
                error = e.message
            )
        }
    }
    
    /**
     * Get trust score - now via REST API
     */
    suspend fun getTrustScore(config: VerifAI.Config, userId: String): VerifAI.TrustScore = withContext(Dispatchers.IO) {
        try {
            val deviceIdHash = SignalCollector.getDeviceIdHash().take(16)
            val response = ApiClient.get(config, "/getTrustScore?userId=${userId.encodeUrl()}&deviceId=${deviceIdHash.encodeUrl()}&type=android")
            
            val level = when (response.optString("level", response.optString("trustLevel", "BASELINE"))) {
                "VERY_HIGH", "very_high" -> VerifAI.TrustLevel.VERY_HIGH
                "HIGH", "high" -> VerifAI.TrustLevel.HIGH
                "MEDIUM", "medium" -> VerifAI.TrustLevel.MEDIUM
                else -> VerifAI.TrustLevel.BASELINE
            }
            
            VerifAI.TrustScore(
                level = level,
                score = response.optInt("score", response.optInt("trustScore", 0)),
                loginCount = response.optInt("loginCount", 0),
                ageInDays = response.optInt("ageInDays", 0)
            )
        } catch (e: Exception) {
            VerifAI.TrustScore(VerifAI.TrustLevel.BASELINE, 0, 0, 0)
        }
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
