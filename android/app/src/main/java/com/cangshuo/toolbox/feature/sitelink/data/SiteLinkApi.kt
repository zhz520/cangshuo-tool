package com.cangshuo.toolbox.feature.sitelink.data

import com.cangshuo.toolbox.feature.auth.data.createRemoteApi
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class SiteLinkDto(val id: Long = 0, val title: String = "", val url: String = "",
    val description: String = "", val createdAt: String = "") {
    override fun toString() = "SiteLinkDto[redacted]"
}

internal data class SiteLinkEnvelope<T>(val code: Int, val message: String, val traceId: String, val data: T?) {
    override fun toString() = "SiteLinkEnvelope[redacted]"
}

internal data class SiteLinkRequestDto(val title: String, val url: String, val description: String) {
    override fun toString() = "SiteLinkRequestDto[redacted]"
}

internal interface SiteLinkApi {
    @GET("site-links") suspend fun list(@Query("limit") limit: Int = 100): Response<SiteLinkEnvelope<List<SiteLinkDto>>>
    @POST("site-links") suspend fun submit(@Header("Authorization") bearer: String,
        @Body request: SiteLinkRequestDto): Response<SiteLinkEnvelope<SiteLinkDto>>
}

/** Approved navigation links plus authenticated submissions that wait for review. */
class RemoteSiteLinkRepository(baseUrl: String, private val bearer: suspend () -> String) {
    private val api = createRemoteApi(baseUrl, SiteLinkApi::class.java, budget = 65_536)

    suspend fun list(): List<SiteLinkDto>? = try {
        api.list().takeIf { it.isSuccessful }?.body()?.data
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { null }

    /** True when the server accepted the submission for review. */
    suspend fun submit(title: String, url: String, description: String): Boolean = try {
        api.submit(bearer(), SiteLinkRequestDto(title, url, description)).isSuccessful
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { false }
}
