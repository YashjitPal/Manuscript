package com.manuscript.core.net

import com.manuscript.core.json.NotionJson
import com.manuscript.core.json.o
import com.manuscript.core.json.s
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

const val NOTION_VERSION = "2026-03-11"
const val NOTION_BASE = "https://api.notion.com"

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

sealed class NotionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Api(
        val status: Int,
        val code: String,
        val apiMessage: String,
        val requestId: String? = null,
        val retryAfterSeconds: Long? = null,
    ) : NotionException(apiMessage)

    class Network(cause: Throwable) : NotionException(cause.message ?: "Network error", cause)
    class NotSignedIn : NotionException("Not signed in")
}

/** A short, human message for any error thrown by the API layer. */
fun Throwable.userMessage(): String = when (this) {
    is NotionException.Api -> when (code) {
        "unauthorized" -> "Your Notion token is invalid, expired or was revoked."
        "restricted_resource" -> "Your connection doesn't have permission for this."
        "object_not_found" -> "Not found. Make sure the page is shared with your connection."
        "rate_limited" -> "Notion is rate limiting requests. Try again in a moment."
        "service_overload", "service_unavailable" -> "Notion is temporarily unavailable. Try again shortly."
        "gateway_timeout" -> "Notion took too long to respond. Your change may still have been saved."
        "conflict_error" -> "Someone else edited this at the same time. Refresh and try again."
        "validation_error" -> apiMessage
        "internal_server_error" -> "Notion had an internal problem. Try again."
        else -> apiMessage.ifBlank { "Request failed ($status)" }
    }
    is NotionException.Network -> "You're offline or Notion can't be reached."
    is NotionException.NotSignedIn -> "Sign in to continue."
    else -> message ?: "Something went wrong"
}

val Throwable.isOffline: Boolean get() = this is NotionException.Network

/**
 * Executes Notion API requests with the headers, retries and error handling described in
 * Notion's request-limits guide.
 */
class NotionHttp(
    private val client: OkHttpClient,
    private val token: suspend () -> String?,
    private val refresh: suspend () -> Boolean = { false },
) {
    suspend fun get(path: String, query: Map<String, String?> = emptyMap()): JsonObject =
        execute("GET", url(path, query), null, idempotent = true)

    suspend fun post(path: String, body: JsonElement? = null, query: Map<String, String?> = emptyMap()): JsonObject =
        execute("POST", url(path, query), (body?.toString() ?: "{}").toRequestBody(JSON_TYPE), idempotent = false)

    suspend fun patch(path: String, body: JsonElement, query: Map<String, String?> = emptyMap()): JsonObject =
        execute("PATCH", url(path, query), body.toString().toRequestBody(JSON_TYPE), idempotent = false)

    suspend fun delete(path: String): JsonObject = execute("DELETE", url(path, emptyMap()), null, idempotent = true)

    suspend fun send(path: String, body: RequestBody): JsonObject =
        execute("POST", url(path, emptyMap()), body, idempotent = false)

    private fun url(path: String, query: Map<String, String?>): HttpUrl {
        val builder = "$NOTION_BASE$path".toHttpUrl().newBuilder()
        query.forEach { (k, v) -> if (v != null) builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private suspend fun execute(method: String, url: HttpUrl, body: RequestBody?, idempotent: Boolean): JsonObject {
        var attempt = 0
        var refreshed = false
        while (true) {
            val bearer = token() ?: throw NotionException.NotSignedIn()
            val request = Request.Builder()
                .url(url)
                .method(method, body)
                .header("Authorization", "Bearer $bearer")
                .header("Notion-Version", NOTION_VERSION)
                .build()
            val response = try {
                client.newCall(request).await()
            } catch (e: IOException) {
                if (idempotent && attempt < 2) {
                    delay(backoff(attempt++))
                    continue
                }
                throw NotionException.Network(e)
            }
            val (code, text, retryAfterHeader) = response.use {
                Triple(it.code, it.body.string(), it.header("Retry-After"))
            }
            if (code in 200..299) {
                return if (text.isBlank()) JsonObject(emptyMap())
                else NotionJson.parseToJsonElement(text) as? JsonObject ?: JsonObject(emptyMap())
            }
            val error = parseError(code, text, retryAfterHeader)
            val retryable = when {
                code == 429 -> !text.contains("public_api_request_blocked") && attempt < 4
                code == 529 -> attempt < 4
                code in setOf(500, 502, 503, 504) -> idempotent && attempt < 2
                else -> false
            }
            if (retryable) {
                val wait = error.retryAfterSeconds?.coerceAtMost(60)?.times(1000) ?: backoff(attempt)
                attempt++
                delay(wait)
                continue
            }
            if (code == 401 && !refreshed && refresh()) {
                refreshed = true
                continue
            }
            throw error
        }
    }

    private fun backoff(attempt: Int): Long = (500L shl attempt) + Random.nextLong(0, 400)

    private fun parseError(code: Int, text: String, retryAfter: String?): NotionException.Api {
        val json = runCatching { NotionJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
        val retry = retryAfter?.toLongOrNull() ?: json?.o("additional_data")?.s("retry_after")?.toLongOrNull()
        return NotionException.Api(
            status = code,
            code = json?.s("code") ?: "http_$code",
            apiMessage = json?.s("message") ?: text.take(200),
            requestId = json?.s("request_id"),
            retryAfterSeconds = retry,
        )
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
