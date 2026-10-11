package com.cangshuo.toolbox.feature.popular.data

import com.cangshuo.toolbox.feature.auth.data.createRemoteApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal data class PopularToolDto(val code: String, val name: String, val description: String? = null,
    val icon: String? = null, val useCount: Long = 0)

internal data class UsageEnvelope<T>(val code: Int, val message: String, val traceId: String, val data: T?) {
    override fun toString() = "UsageEnvelope[redacted]"
}

internal interface ToolUsageApi {
    @POST("tools/{code}/usage") suspend fun record(@Path("code") code: String): Response<UsageEnvelope<Unit>>
    @GET("tools/popular") suspend fun popular(@Query("limit") limit: Int): Response<UsageEnvelope<List<PopularToolDto>>>
}

/**
 * Anonymous usage reporting plus the aggregated popular list. Reads are cached for a few minutes
 * and every failure keeps the previous snapshot, so the home page never blocks on this data.
 */
internal class RemoteToolUsageRepository(baseUrl: String) {
    private val api = createRemoteApi(baseUrl, ToolUsageApi::class.java, budget = 32_768)
    private val lock = Mutex()
    private var fetchedAt = 0L
    private var cached: List<PopularToolDto> = emptyList()

    /** Fire and forget: an offline device or a missing endpoint must stay silent. */
    suspend fun record(code: String) {
        try { api.record(code) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }

    suspend fun popular(limit: Int = 8): List<PopularToolDto> = lock.withLock {
        val now = System.currentTimeMillis()
        if (now - fetchedAt < CACHE_MILLIS) return@withLock cached
        val data = try {
            api.popular(limit).takeIf { it.isSuccessful }?.body()?.data
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        // Success is cached for five minutes; failures retry after a thirty second backoff.
        fetchedAt = if (data != null) now else now - CACHE_MILLIS + FAILURE_BACKOFF_MILLIS
        if (data != null) cached = data
        cached
    }

    private companion object {
        const val CACHE_MILLIS = 5 * 60_000L
        const val FAILURE_BACKOFF_MILLIS = 30_000L
    }
}
