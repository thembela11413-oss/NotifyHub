package com.notifyhub.sync

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface SendResult {
    data object Success : SendResult
    data class Failure(val message: String, val retryable: Boolean) : SendResult
}

interface SyncTransport {
    suspend fun send(baseUrl: String, token: String?, events: List<EventPayload>, deviceId: String): SendResult
    suspend fun testConnection(baseUrl: String, token: String?): SendResult
}

/**
 * OkHttp transport. Endpoints are provisional: POST {base}/api/v1/events and GET {base}/api/v1/health.
 * Failure messages contain only status codes or exception class names, never notification content.
 */
class HttpSyncTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : SyncTransport {
    private val json = Json { encodeDefaults = true }

    override suspend fun send(baseUrl: String, token: String?, events: List<EventPayload>, deviceId: String): SendResult =
        execute {
            val body = json.encodeToString(EventBatch(deviceId, events))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            Request.Builder().url("$baseUrl/api/v1/events").post(body).auth(token).build()
        }

    override suspend fun testConnection(baseUrl: String, token: String?): SendResult =
        execute { Request.Builder().url("$baseUrl/api/v1/health").get().auth(token).build() }

    private fun Request.Builder.auth(token: String?) = apply {
        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
    }

    private suspend fun execute(build: () -> Request): SendResult = try {
        client.newCall(build()).await().use { r ->
            if (r.isSuccessful) SendResult.Success
            else SendResult.Failure("HTTP ${r.code}", retryable = r.code >= 500 || r.code == 408 || r.code == 429)
        }
    } catch (e: IOException) {
        SendResult.Failure("Network error (${e.javaClass.simpleName})", retryable = true)
    } catch (e: IllegalArgumentException) {
        SendResult.Failure("Invalid server URL", retryable = false)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}
