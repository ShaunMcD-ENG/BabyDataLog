package com.babydatalog.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class ApiResult<T>(val data: T?, val error: String?)

@Singleton
class SyncApiClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun registerDevice(
        serverUrl: String, deviceId: String, name: String, pairingCode: String
    ): ApiResult<RegisterResponse> = post(
        url = "$serverUrl/api/sync/devices",
        body = json.encodeToString(RegisterRequest(deviceId, name, pairingCode))
    ).parse { json.decodeFromString<RegisterResponse>(it) }

    suspend fun pollStatus(
        serverUrl: String, deviceId: String, pairingCode: String
    ): ApiResult<PollResponse> = get(
        url = "$serverUrl/api/sync/devices/$deviceId?pairingCode=$pairingCode"
    ).parse { json.decodeFromString<PollResponse>(it) }

    suspend fun push(
        serverUrl: String, apiKey: String, deviceId: String,
        table: String, records: JsonArray
    ): ApiResult<Unit> = post(
        url = "$serverUrl/api/sync",
        body = json.encodeToString(SyncPushRequest(deviceId, table, records)),
        apiKey = apiKey
    ).parse { }

    suspend fun pull(
        serverUrl: String, apiKey: String, deviceId: String, lastSyncMs: Long
    ): ApiResult<SyncPullResponse> = get(
        url = "$serverUrl/api/sync?deviceId=$deviceId&lastSyncMs=$lastSyncMs",
        apiKey = apiKey
    ).parse { json.decodeFromString<SyncPullResponse>(it) }

    // --- HTTP primitives ---

    private data class RawResult(val code: Int, val body: String)

    private fun <T> RawResult.parse(transform: (String) -> T): ApiResult<T> {
        if (code == -1) return ApiResult(null, body)
        return if (code in 200..299) {
            try {
                ApiResult(transform(body), null)
            } catch (e: Exception) {
                ApiResult(null, "Response parse error: ${e.message}")
            }
        } else {
            val msg = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull() ?: "HTTP $code"
            ApiResult(null, msg)
        }
    }

    private suspend fun post(url: String, body: String, apiKey: String? = null): RawResult =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                apiKey?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val text = (if (code < 400) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                conn.disconnect()
                RawResult(code, text)
            } catch (e: IOException) {
                RawResult(-1, e.message ?: "Network error")
            }
        }

    private suspend fun get(url: String, apiKey: String? = null): RawResult =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                apiKey?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                val code = conn.responseCode
                val text = (if (code < 400) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                conn.disconnect()
                RawResult(code, text)
            } catch (e: IOException) {
                RawResult(-1, e.message ?: "Network error")
            }
        }
}
