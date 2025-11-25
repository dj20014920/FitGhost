package com.fitghost.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object TimeProvider {
    
    /**
     * Fetches the current time from a reliable server (Google).
     * Throws an exception if the network request fails.
     * This prevents users from manipulating local device time.
     */
    suspend fun getNetworkTime(): Long = withContext(Dispatchers.IO) {
        try {
            // Using a lightweight Google endpoint to get the Date header
            val url = URL("https://clients3.google.com/generate_204")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000 // 5 seconds timeout
            connection.readTimeout = 5000
            connection.connect()
            
            val dateHeader = connection.getHeaderField("Date")
            connection.disconnect()
            
            if (dateHeader != null) {
                // Parse the Date header (RFC 1123 format)
                // Example: "Mon, 24 Nov 2025 10:00:00 GMT"
                val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                format.timeZone = TimeZone.getTimeZone("GMT")
                format.parse(dateHeader)?.time ?: throw Exception("Failed to parse server date")
            } else {
                throw Exception("Server did not return a Date header")
            }
        } catch (e: Exception) {
            // In a real production app, you might want a fallback or a more robust NTP solution.
            // For this requirement, we strictly enforce server time.
            throw Exception("Network time check failed: ${e.message}")
        }
    }
}
