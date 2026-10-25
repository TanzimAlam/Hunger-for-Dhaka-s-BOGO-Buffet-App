package com.buffetbogo.hunter.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Tiny HTTP helper so the app needs no extra networking dependency. */
object Http {

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        request(url, "GET", headers, null)

    suspend fun postJson(url: String, headers: Map<String, String>, body: String): String =
        request(url, "POST", headers, body)

    private suspend fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "BuffetBogoHunter/1.0 (Android)")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code from ${URL(url).host}: ${text.take(160)}")
            }
            text
        } finally {
            connection.disconnect()
        }
    }
}
