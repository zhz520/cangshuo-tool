package com.cangshuo.toolbox.feature.auth.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.cangshuo.toolbox.feature.auth.domain.AvatarRepository
import com.cangshuo.toolbox.feature.auth.domain.QqAvatarPolicy
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Optional local avatar preference. Never forwards account credentials to the QQ image host. */
class QqAvatarRepository(context: Context, scope: CoroutineScope) : AvatarRepository {
    private val storage = DataStoreFactory.create(
        serializer = AvatarPreferences,
        scope = scope,
        produceFile = { java.io.File(context.filesDir, "qq-avatar.preferences") },
    )
    private val client = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private val cache = android.util.LruCache<String, Bitmap>(4)

    override fun observeQq(userId: Long) = storage.data.map { it[userId.toString()] }

    override suspend fun saveQq(userId: Long, number: String) {
        storage.updateData { values ->
            // An explicit blank overrides QQ-email detection, so clearing the field really resets it.
            values + (userId.toString() to number)
        }
    }

    override suspend fun load(number: String): Bitmap = withContext(Dispatchers.IO) {
        cache.get(number)?.let { return@withContext it }
        val request = Request.Builder().url(QqAvatarPolicy.url(number)).build()
        val response = client.newCall(request).await()
        response.use {
            if (!it.isSuccessful) throw IOException("Avatar unavailable")
            val body = it.body() ?: throw IOException("Avatar unavailable")
            val limit = 1_048_576L
            if (body.contentLength() > limit) throw IOException("Avatar too large")
            val source = body.source()
            source.request(limit + 1)
            if (source.buffer.size > limit) throw IOException("Avatar too large")
            val bytes = source.buffer.readByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..2048 || bounds.outHeight !in 1..2048) throw IOException("Invalid avatar")
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize > 192 || bounds.outHeight / inSampleSize > 192) inSampleSize *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("Invalid avatar")
            coroutineContext.ensureActive()
            cache.put(number, bitmap)
            bitmap
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
            }
        })
    }
}

private object AvatarPreferences : Serializer<Map<String, String>> {
    override val defaultValue = emptyMap<String, String>()
    override suspend fun readFrom(input: InputStream): Map<String, String> = try {
        val bytes = java.io.ByteArrayOutputStream().use { buffer ->
            val chunk = ByteArray(4096)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                buffer.write(chunk, 0, count)
                if (buffer.size() > 65_536) throw IOException("Avatar preference too large")
            }
            buffer.toByteArray()
        }
        if (bytes.size > 65_536) throw IOException("Avatar preference too large")
        val properties = Properties().apply { bytes.inputStream().use(::load) }
        properties.stringPropertyNames().associateWith { properties.getProperty(it) }
            .filter { (key, value) -> key.toLongOrNull()?.let { it > 0 } == true && (value.isEmpty() || QqAvatarPolicy.valid(value)) }
    } catch (error: IOException) { throw CorruptionException("Invalid avatar preferences", error) }
      catch (error: IllegalArgumentException) { throw CorruptionException("Invalid avatar preferences", error) }
    override suspend fun writeTo(t: Map<String, String>, output: OutputStream) {
        Properties().apply { putAll(t) }.store(output, null)
    }
}
