package com.verifai.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Internal API client - handles all HTTP requests to VerifAI backend
 */
internal object ApiClient {
    
    private const val BASE_URL = "https://us-central1-gen-lang-client-0072619475.cloudfunctions.net"
    
    suspend fun registerToken(config: VerifAI.Config, userId: String, fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("userId", userId)
                put("fcmToken", fcmToken)
            }
            
            val response = post(config, "/registerToken", body)
            response.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun listDevices(config: VerifAI.Config, userId: String): List<VerifAI.Device> = withContext(Dispatchers.IO) {
        try {
            val response = get(config, "/listDevices?userId=${userId.encodeUrl()}")
            val devices = mutableListOf<VerifAI.Device>()
            
            val devicesArray = response.optJSONArray("devices")
            if (devicesArray != null) {
                for (i in 0 until devicesArray.length()) {
                    val d = devicesArray.getJSONObject(i)
                    devices.add(VerifAI.Device(
                        id = d.optString("id"),
                        type = if (d.optString("type") == "pc") VerifAI.DeviceType.PC else VerifAI.DeviceType.ANDROID,
                        name = d.optString("browser", d.optString("platform", "Unknown")),
                        createdAt = d.optLong("createdAt", 0),
                        lastUsed = if (d.has("lastLogin")) d.optLong("lastLogin") else null
                    ))
                }
            }
            
            devices
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun deleteDevice(config: VerifAI.Config, deviceId: String, type: VerifAI.DeviceType): Boolean = withContext(Dispatchers.IO) {
        try {
            val typeStr = if (type == VerifAI.DeviceType.PC) "pc" else "android"
            val response = delete(config, "/deleteDevice?deviceId=${deviceId.encodeUrl()}&type=$typeStr")
            response.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun getTrustScore(config: VerifAI.Config, deviceId: String, type: VerifAI.DeviceType): VerifAI.TrustScore? = withContext(Dispatchers.IO) {
        try {
            val typeStr = when (type) {
                VerifAI.DeviceType.PC -> "pc"
                VerifAI.DeviceType.ANDROID -> "android"
            }
            val response = get(config, "/getTrustScore?deviceId=${deviceId.encodeUrl()}&type=$typeStr")
            
            val level = when (response.optString("trustLevel")) {
                "very_high" -> VerifAI.TrustLevel.VERY_HIGH
                "high" -> VerifAI.TrustLevel.HIGH
                "medium" -> VerifAI.TrustLevel.MEDIUM
                else -> VerifAI.TrustLevel.BASELINE
            }
            
            VerifAI.TrustScore(
                level = level,
                score = response.optInt("trustScore", 50),
                loginCount = response.optInt("loginCount", 0),
                ageInDays = response.optInt("ageInDays", 0)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun approveDevice(config: VerifAI.Config, sessionId: String, type: VerifAI.DeviceType): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("sessionId", sessionId)
                put("type", if (type == VerifAI.DeviceType.PC) "pc" else "android")
                put("approvedBy", "VerifAI SDK")
            }
            
            val response = post(config, "/approveDevice", body)
            response.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun rejectDevice(config: VerifAI.Config, sessionId: String, type: VerifAI.DeviceType, reason: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("sessionId", sessionId)
                put("type", if (type == VerifAI.DeviceType.PC) "pc" else "android")
                put("reason", reason)
            }
            
            val response = post(config, "/rejectDevice", body)
            response.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== HTTP Helpers ====================
    
    private fun get(config: VerifAI.Config, endpoint: String): JSONObject {
        val url = URL("$BASE_URL$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = config.options.timeoutSeconds * 1000
        conn.readTimeout = config.options.timeoutSeconds * 1000
        
        val response = conn.inputStream.bufferedReader().readText()
        return JSONObject(response)
    }
    
    private fun post(config: VerifAI.Config, endpoint: String, body: JSONObject): JSONObject {
        val url = URL("$BASE_URL$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = config.options.timeoutSeconds * 1000
        conn.readTimeout = config.options.timeoutSeconds * 1000
        conn.doOutput = true
        
        conn.outputStream.bufferedWriter().use { it.write(body.toString()) }
        
        val response = conn.inputStream.bufferedReader().readText()
        return JSONObject(response)
    }
    
    private fun delete(config: VerifAI.Config, endpoint: String): JSONObject {
        val url = URL("$BASE_URL$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = config.options.timeoutSeconds * 1000
        conn.readTimeout = config.options.timeoutSeconds * 1000
        
        val response = conn.inputStream.bufferedReader().readText()
        return JSONObject(response)
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
