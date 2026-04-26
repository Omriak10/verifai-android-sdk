package com.verifai.sdk

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Internal device manager - handles registration and verification flow
 */
internal object DeviceManager {
    
    private const val CLOUD_PROJECT_NUMBER = 71845524469L
    private const val DATABASE_NAME = "zkbank"
    
    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(DATABASE_NAME)
    }
    
    /**
     * Register device - stores signal hashes in Firebase
     */
    suspend fun register(config: VerifAI.Config, userId: String): VerifAI.RegistrationResult = withContext(Dispatchers.IO) {
        try {
            val emailKey = userId.toEmailKey()
            
            // Collect all signal hashes
            val signalHashes = SignalCollector.collectSignalHashes(CLOUD_PROJECT_NUMBER)
            val deviceIdHash = SignalCollector.getDeviceIdHash()
            val masterHash = computeMasterHash(signalHashes, deviceIdHash)
            
            // Store in Firebase
            val certData = hashMapOf(
                "email" to userId,
                "masterHash" to masterHash,
                "deviceIdHash" to deviceIdHash.take(16),
                "hashes" to signalHashes,
                "loginCount" to 1,
                "firstLogin" to System.currentTimeMillis(),
                "lastLogin" to System.currentTimeMillis(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            
            db.collection("zk_certificates")
                .document(emailKey)
                .set(certData)
                .await()
            
            // Store locally
            val prefs = config.context.getSharedPreferences("verifai_device", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("certificate", masterHash)
                .putString("device_id", deviceIdHash.take(16))
                .putInt("login_count", 1)
                .putLong("first_login", System.currentTimeMillis())
                .apply()
            
            VerifAI.RegistrationResult(
                success = true,
                deviceId = deviceIdHash.take(16)
            )
            
        } catch (e: Exception) {
            VerifAI.RegistrationResult(
                success = false,
                error = e.message
            )
        }
    }
    
    /**
     * Verify device - compares current signals against stored profile
     */
    suspend fun verify(config: VerifAI.Config, userId: String): VerifAI.VerificationResult = withContext(Dispatchers.IO) {
        try {
            val emailKey = userId.toEmailKey()
            val deviceIdHash = SignalCollector.getDeviceIdHash()
            
            // Check for existing certificate
            val zkDoc = db.collection("zk_certificates").document(emailKey).get().await()
            
            if (!zkDoc.exists()) {
                // No baseline - this is first login, register
                val registerResult = register(config, userId)
                return@withContext if (registerResult.success) {
                    VerifAI.VerificationResult(
                        status = VerifAI.Status.TRUSTED,
                        trustScore = 50,
                        trustLevel = VerifAI.TrustLevel.BASELINE,
                        deviceId = registerResult.deviceId
                    )
                } else {
                    VerifAI.VerificationResult(
                        status = VerifAI.Status.ERROR,
                        error = registerResult.error
                    )
                }
            }
            
            // Get stored hashes
            val storedDeviceId = zkDoc.getString("deviceIdHash") ?: ""
            val storedHashes = zkDoc.get("hashes") as? Map<String, String> ?: emptyMap()
            val loginCount = zkDoc.getLong("loginCount")?.toInt() ?: 0
            val firstLogin = zkDoc.getLong("firstLogin") ?: System.currentTimeMillis()
            
            // Check if same device
            if (deviceIdHash.take(16) == storedDeviceId) {
                // Same device - update login count and verify
                val currentHashes = SignalCollector.collectSignalHashes(CLOUD_PROJECT_NUMBER)
                val matchScore = compareHashes(storedHashes, currentHashes)
                
                if (matchScore >= 60) {
                    // Trusted - update login count
                    val newLoginCount = loginCount + 1
                    db.collection("zk_certificates").document(emailKey).update(
                        mapOf(
                            "loginCount" to newLoginCount,
                            "lastLogin" to System.currentTimeMillis()
                        )
                    ).await()
                    
                    val trustLevel = when {
                        newLoginCount >= 10 -> VerifAI.TrustLevel.VERY_HIGH
                        newLoginCount >= 5 -> VerifAI.TrustLevel.HIGH
                        newLoginCount >= 2 -> VerifAI.TrustLevel.MEDIUM
                        else -> VerifAI.TrustLevel.BASELINE
                    }
                    
                    val trustScore = when (trustLevel) {
                        VerifAI.TrustLevel.VERY_HIGH -> 95
                        VerifAI.TrustLevel.HIGH -> 85
                        VerifAI.TrustLevel.MEDIUM -> 70
                        VerifAI.TrustLevel.BASELINE -> 50
                    }
                    
                    return@withContext VerifAI.VerificationResult(
                        status = VerifAI.Status.TRUSTED,
                        trustScore = trustScore,
                        trustLevel = trustLevel,
                        deviceId = storedDeviceId
                    )
                } else {
                    // Signals don't match - suspicious
                    return@withContext VerifAI.VerificationResult(
                        status = VerifAI.Status.REJECTED,
                        error = "Device signals have changed significantly"
                    )
                }
            }
            
            // Different device - check if it's already approved as secondary
            val secondaryDoc = db.collection("android_certificates")
                .document("${emailKey}_${deviceIdHash.take(16)}")
                .get()
                .await()
            
            if (secondaryDoc.exists()) {
                // Already approved secondary device
                val secLoginCount = secondaryDoc.getLong("loginCount")?.toInt() ?: 0
                val newLoginCount = secLoginCount + 1
                
                db.collection("android_certificates")
                    .document("${emailKey}_${deviceIdHash.take(16)}")
                    .update("loginCount", newLoginCount, "lastLogin", System.currentTimeMillis())
                    .await()
                
                val trustLevel = when {
                    newLoginCount >= 10 -> VerifAI.TrustLevel.VERY_HIGH
                    newLoginCount >= 5 -> VerifAI.TrustLevel.HIGH
                    newLoginCount >= 2 -> VerifAI.TrustLevel.MEDIUM
                    else -> VerifAI.TrustLevel.BASELINE
                }
                
                return@withContext VerifAI.VerificationResult(
                    status = VerifAI.Status.TRUSTED,
                    trustScore = when (trustLevel) {
                        VerifAI.TrustLevel.VERY_HIGH -> 95
                        VerifAI.TrustLevel.HIGH -> 85
                        VerifAI.TrustLevel.MEDIUM -> 70
                        VerifAI.TrustLevel.BASELINE -> 50
                    },
                    trustLevel = trustLevel,
                    deviceId = deviceIdHash.take(16)
                )
            }
            
            // New device - needs approval
            val sessionId = java.util.UUID.randomUUID().toString().take(8)
            val publicIP = NetworkUtils.getPublicIP()
            val currentHashes = SignalCollector.collectSignalHashes(CLOUD_PROJECT_NUMBER)
            
            val approvalData = hashMapOf(
                "email" to userId,
                "sessionId" to sessionId,
                "deviceModel" to android.os.Build.MODEL,
                "deviceBrand" to android.os.Build.BRAND,
                "publicIP" to publicIP,
                "status" to "pending",
                "requestedAt" to System.currentTimeMillis(),
                "currentHashes" to currentHashes,
                "deviceIdHash" to deviceIdHash.take(16)
            )
            
            db.collection("android_approval_requests")
                .document(sessionId)
                .set(approvalData)
                .await()
            
            VerifAI.VerificationResult(
                status = VerifAI.Status.NEW_DEVICE,
                sessionId = sessionId,
                deviceId = deviceIdHash.take(16)
            )
            
        } catch (e: Exception) {
            VerifAI.VerificationResult(
                status = VerifAI.Status.ERROR,
                error = e.message
            )
        }
    }
    
    /**
     * Get trust score for current device
     */
    suspend fun getTrustScore(config: VerifAI.Config, userId: String): VerifAI.TrustScore = withContext(Dispatchers.IO) {
        try {
            val emailKey = userId.toEmailKey()
            val deviceIdHash = SignalCollector.getDeviceIdHash().take(16)
            
            // Check primary certificate
            val zkDoc = db.collection("zk_certificates").document(emailKey).get().await()
            if (zkDoc.exists() && zkDoc.getString("deviceIdHash") == deviceIdHash) {
                val loginCount = zkDoc.getLong("loginCount")?.toInt() ?: 0
                val firstLogin = zkDoc.getLong("firstLogin") ?: System.currentTimeMillis()
                val ageInDays = ((System.currentTimeMillis() - firstLogin) / (1000 * 60 * 60 * 24)).toInt()
                
                val level = when {
                    loginCount >= 10 -> VerifAI.TrustLevel.VERY_HIGH
                    loginCount >= 5 -> VerifAI.TrustLevel.HIGH
                    loginCount >= 2 -> VerifAI.TrustLevel.MEDIUM
                    else -> VerifAI.TrustLevel.BASELINE
                }
                
                var score = when (level) {
                    VerifAI.TrustLevel.VERY_HIGH -> 95
                    VerifAI.TrustLevel.HIGH -> 85
                    VerifAI.TrustLevel.MEDIUM -> 70
                    VerifAI.TrustLevel.BASELINE -> 50
                }
                
                if (ageInDays > 30) score = minOf(100, score + 5)
                
                return@withContext VerifAI.TrustScore(level, score, loginCount, ageInDays)
            }
            
            // Check secondary certificate
            val secDoc = db.collection("android_certificates")
                .document("${emailKey}_$deviceIdHash")
                .get()
                .await()
            
            if (secDoc.exists()) {
                val loginCount = secDoc.getLong("loginCount")?.toInt() ?: 0
                val firstLogin = secDoc.getLong("firstLogin") ?: System.currentTimeMillis()
                val ageInDays = ((System.currentTimeMillis() - firstLogin) / (1000 * 60 * 60 * 24)).toInt()
                
                val level = when {
                    loginCount >= 10 -> VerifAI.TrustLevel.VERY_HIGH
                    loginCount >= 5 -> VerifAI.TrustLevel.HIGH
                    loginCount >= 2 -> VerifAI.TrustLevel.MEDIUM
                    else -> VerifAI.TrustLevel.BASELINE
                }
                
                var score = when (level) {
                    VerifAI.TrustLevel.VERY_HIGH -> 95
                    VerifAI.TrustLevel.HIGH -> 85
                    VerifAI.TrustLevel.MEDIUM -> 70
                    VerifAI.TrustLevel.BASELINE -> 50
                }
                
                if (ageInDays > 30) score = minOf(100, score + 5)
                
                return@withContext VerifAI.TrustScore(level, score, loginCount, ageInDays)
            }
            
            // Not registered
            VerifAI.TrustScore(VerifAI.TrustLevel.BASELINE, 0, 0, 0)
            
        } catch (e: Exception) {
            VerifAI.TrustScore(VerifAI.TrustLevel.BASELINE, 0, 0, 0)
        }
    }
    
    // ==================== Helpers ====================
    
    private fun String.toEmailKey(): String {
        return this.lowercase().replace(".", "_").replace("@", "_at_")
    }
    
    private fun computeMasterHash(hashes: Map<String, String>, deviceId: String): String {
        val combined = hashes.values.sorted().joinToString("|") + "|" + deviceId
        val bytes = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun compareHashes(stored: Map<String, String>, current: Map<String, String>): Int {
        var matchCount = 0
        var totalCount = 0
        
        for ((key, storedValue) in stored) {
            val currentValue = current[key]
            if (currentValue != null) {
                totalCount++
                if (storedValue == currentValue) {
                    matchCount++
                }
            }
        }
        
        return if (totalCount == 0) 0 else (matchCount * 100) / totalCount
    }
}
