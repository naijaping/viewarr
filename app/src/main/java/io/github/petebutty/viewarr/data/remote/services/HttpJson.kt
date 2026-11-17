package io.github.petebutty.viewarr.data.remote.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class JsonResponse(val code: Int, val body: JsonElement?) {
    val ok get() = code in 200..299
}

/** Small JSON-over-HTTP helper for the third-party services (each has its own auth headers). */
@Singleton
class HttpJson @Inject constructor(shared: OkHttpClient, val json: Json) {

    /**
     * The shared client (same connections) with a limit on the whole call: the read timeout restarts with every byte,
     * so a slow server trickling a reply could hold a call for minutes. These are all small API replies.
     */
    private val client: OkHttpClient = shared.newBuilder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): JsonResponse =
        call(Request.Builder().url(url).get(), headers)

    suspend fun post(url: String, body: JsonElement? = null, headers: Map<String, String> = emptyMap()): JsonResponse {
        val payload = (body?.toString() ?: "").toRequestBody(JSON_TYPE)
        return call(Request.Builder().url(url).post(payload), headers)
    }

    suspend fun put(url: String, body: JsonElement, headers: Map<String, String> = emptyMap()): JsonResponse =
        call(Request.Builder().url(url).put(body.toString().toRequestBody(JSON_TYPE)), headers)

    suspend fun patch(url: String, body: JsonElement, headers: Map<String, String> = emptyMap()): JsonResponse =
        call(Request.Builder().url(url).patch(body.toString().toRequestBody(JSON_TYPE)), headers)

    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): JsonResponse =
        call(Request.Builder().url(url).delete(), headers)

    /** The reply as plain text (for services that may answer in XML, e.g. plex.tv's older account endpoints). */
    suspend fun text(method: String, url: String, body: JsonElement? = null, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val b = Request.Builder().url(url)
        val payload = (body?.toString() ?: "").toRequestBody(JSON_TYPE)
        when (method) { "POST" -> b.post(payload); "PUT" -> b.put(payload); "DELETE" -> b.delete(); else -> b.get() }
        headers.forEach { (k, v) -> b.header(k, v) }
        b.header("User-Agent", "Viewarr/0.7")
        return send(b.build()) { r -> r.code to r.body?.string().orEmpty() }
    }

    private suspend fun call(builder: Request.Builder, headers: Map<String, String>): JsonResponse {
        headers.forEach { (k, v) -> builder.header(k, v) }
        if ("Accept" !in headers) builder.header("Accept", "application/json")
        builder.header("User-Agent", "Viewarr/0.7")
        return send(builder.build()) { r ->
            val text = r.body?.string().orEmpty()
            JsonResponse(r.code, runCatching { json.parseToJsonElement(text) }.getOrNull())
        }
    }

    /**
     * Runs the request on an IO thread and reads the reply there, but the caller stops waiting the moment it's
     * cancelled, and the call is cancelled with it (connecting, waiting or reading). A blocking execute() inside
     * withContext ignored cancellation, so every withTimeoutOrNull around these only gave up when the socket did.
     * (Not enqueue(): OkHttp's queue allows 5 calls per server, which would have held up rows loading side by side.)
     */
    private suspend fun <T> send(request: Request, read: (Response) -> T): T {
        val call = client.newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            BLOCKING.execute { cont.resumeWith(runCatching { call.execute().use(read) }) }
        }
    }

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()
        val BLOCKING = Dispatchers.IO.asExecutor()
    }
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.str(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
fun JsonElement?.int(key: String): Int? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.intOrNull
fun JsonElement?.long(key: String): Long? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNull
fun JsonElement?.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)
fun JsonElement?.array(key: String): List<JsonElement> = runCatching { (this as JsonObject)[key]!!.jsonArray.toList() }.getOrDefault(emptyList())
fun JsonElement?.asArray(): List<JsonElement> = runCatching { this!!.jsonArray.toList() }.getOrDefault(emptyList())
