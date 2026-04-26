package com.verifai.sdk

import android.content.Context
import kotlinx.coroutines.*

/**
 * VerifAI SDK - Zero-Knowledge Device Trust
 * 
 * Usage:
 * ```
 * // Initialize once in Application or MainActivity
 * VerifAI.init(context, "vf_live_your_api_key")
 * 
 * // Register device (first login)
 * val result = VerifAI.register("user@email.com")
 * if (result.success) { /* proceed */ }
 * 
 * // Verify device (subsequent logins)
 * val result = VerifAI.verify("user@email.com")
 * when (result.status) {
 *     VerifAI.Status.TRUSTED -> { /* allow access */ }
 *     VerifAI.Status.NEW_DEVICE -> { /* show approval UI */ }
 *     VerifAI.Status.REJECTED -> { /* block access */ }
 * }
 * ```
 */
object VerifAI {
    
    private lateinit var config: Config
    private var isInitialized = false
    
    /**
     * Initialize the SDK. Call once at app startup.
     * 
     * @param context Application context
     * @param apiKey Your VerifAI API key (starts with vf_live_)
     * @param options Optional configuration
     */
    fun init(context: Context, apiKey: String, options: Options = Options()) {
        require(apiKey.startsWith("vf_live_") || apiKey.startsWith("vf_test_")) {
            "Invalid API key format. Must start with vf_live_ or vf_test_"
        }
        
        config = Config(
            context = context.applicationContext,
            apiKey = apiKey,
            options = options
        )
        
        SignalCollector.init(context.applicationContext)
        isInitialized = true
    }
    
    /**
     * Register a new device for the user.
     * Call this on first login / sign up.
     * 
     * @param userId User's email address
     * @return RegistrationResult with success status and device ID
     */
    suspend fun register(userId: String): RegistrationResult {
        checkInitialized()
        return DeviceManager.register(config, userId)
    }
    
    /**
     * Verify the current device against stored profile.
     * Call this on every login.
     * 
     * @param userId User's email address
     * @return VerificationResult with status and trust score
     */
    suspend fun verify(userId: String): VerificationResult {
        checkInitialized()
        return DeviceManager.verify(config, userId)
    }
    
    /**
     * Get trust score for the current device.
     * 
     * @param userId User's email address
     * @return TrustScore with level and numeric score
     */
    suspend fun getTrustScore(userId: String): TrustScore {
        checkInitialized()
        return DeviceManager.getTrustScore(config, userId)
    }
    
    /**
     * Register FCM token for push notifications.
     * Required for approving logins from other devices.
     * 
     * @param userId User's email address
     * @param fcmToken Firebase Cloud Messaging token
     */
    suspend fun registerPushToken(userId: String, fcmToken: String): Boolean {
        checkInitialized()
        return ApiClient.registerToken(config, userId, fcmToken)
    }
    
    /**
     * List all trusted devices for the user.
     * 
     * @param userId User's email address
     * @return List of trusted devices
     */
    suspend fun listDevices(userId: String): List<Device> {
        checkInitialized()
        return ApiClient.listDevices(config, userId)
    }
    
    /**
     * Remove a trusted device.
     * 
     * @param deviceId Device ID to remove
     * @param type Device type (pc or android)
     */
    suspend fun removeDevice(deviceId: String, type: DeviceType = DeviceType.ANDROID): Boolean {
        checkInitialized()
        return ApiClient.deleteDevice(config, deviceId, type)
    }
    
    /**
     * Handle incoming push notification for device approval.
     * Call this from your FirebaseMessagingService.
     * 
     * @param data FCM message data
     * @return ApprovalRequest if this is an approval request, null otherwise
     */
    fun handlePushNotification(data: Map<String, String>): ApprovalRequest? {
        val type = data["type"] ?: return null
        
        return when (type) {
            "pc_approval", "android_approval" -> {
                ApprovalRequest(
                    sessionId = data["sessionId"] ?: return null,
                    type = if (type == "pc_approval") DeviceType.PC else DeviceType.ANDROID,
                    deviceInfo = data["browser"] ?: data["deviceModel"] ?: "Unknown",
                    publicIP = data["publicIP"] ?: ""
                )
            }
            else -> null
        }
    }
    
    /**
     * Approve a pending device request.
     * 
     * @param sessionId Session ID from ApprovalRequest
     * @param type Device type
     */
    suspend fun approveDevice(sessionId: String, type: DeviceType = DeviceType.PC): Boolean {
        checkInitialized()
        return ApiClient.approveDevice(config, sessionId, type)
    }
    
    /**
     * Reject a pending device request.
     * 
     * @param sessionId Session ID from ApprovalRequest
     * @param type Device type
     * @param reason Rejection reason
     */
    suspend fun rejectDevice(sessionId: String, type: DeviceType = DeviceType.PC, reason: String = "rejected_by_user"): Boolean {
        checkInitialized()
        return ApiClient.rejectDevice(config, sessionId, type, reason)
    }
    
    /**
     * Check if this device is on the same network as a given IP.
     * Used for proximity verification.
     * 
     * @param remoteIP The IP to compare against
     * @return true if on same network
     */
    suspend fun isSameNetwork(remoteIP: String): Boolean {
        val myIP = NetworkUtils.getPublicIP()
        return myIP == remoteIP
    }
    
    private fun checkInitialized() {
        check(isInitialized) { "VerifAI SDK not initialized. Call VerifAI.init() first." }
    }
    
    // ==================== Public Data Classes ====================
    
    enum class Status {
        TRUSTED,        // Device is trusted, allow access
        NEW_DEVICE,     // New device, needs approval from primary
        PENDING,        // Waiting for approval
        REJECTED,       // Device rejected
        ERROR           // Something went wrong
    }
    
    enum class TrustLevel {
        BASELINE,       // First login, score 50
        MEDIUM,         // 2-4 logins, score 70
        HIGH,           // 5-9 logins, score 85
        VERY_HIGH       // 10+ logins, score 95
    }
    
    enum class DeviceType {
        ANDROID,
        PC
    }
    
    data class Options(
        val enablePlayIntegrity: Boolean = true,
        val timeoutSeconds: Int = 30
    )
    
    data class RegistrationResult(
        val success: Boolean,
        val deviceId: String? = null,
        val error: String? = null
    )
    
    data class VerificationResult(
        val status: Status,
        val trustScore: Int = 0,
        val trustLevel: TrustLevel = TrustLevel.BASELINE,
        val deviceId: String? = null,
        val sessionId: String? = null,  // For NEW_DEVICE status
        val error: String? = null
    )
    
    data class TrustScore(
        val level: TrustLevel,
        val score: Int,
        val loginCount: Int,
        val ageInDays: Int
    )
    
    data class Device(
        val id: String,
        val type: DeviceType,
        val name: String,
        val createdAt: Long,
        val lastUsed: Long?
    )
    
    data class ApprovalRequest(
        val sessionId: String,
        val type: DeviceType,
        val deviceInfo: String,
        val publicIP: String
    )
    
    internal data class Config(
        val context: Context,
        val apiKey: String,
        val options: Options
    )
}
