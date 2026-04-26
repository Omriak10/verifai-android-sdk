package com.verifai.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Internal network utilities
 */
internal object NetworkUtils {
    
    /**
     * Get public IP address for proximity verification
     */
    suspend fun getPublicIP(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.ipify.org?format=text")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            ""
        }
    }
}
