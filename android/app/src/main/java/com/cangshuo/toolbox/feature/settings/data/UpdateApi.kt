package com.cangshuo.toolbox.feature.settings.data

import com.cangshuo.toolbox.feature.auth.data.createRemoteApi
import retrofit2.Response
import retrofit2.http.GET

data class AppVersionDto(val versionCode: Long = 0, val versionName: String = "",
    val url: String = "", val notes: String = "") {
    override fun toString() = "AppVersionDto[redacted]"
}

internal data class VersionEnvelope<T>(val code: Int, val message: String, val traceId: String, val data: T?) {
    override fun toString() = "VersionEnvelope[redacted]"
}

internal interface UpdateApi {
    @GET("app/version") suspend fun latest(): Response<VersionEnvelope<AppVersionDto>>
}

/** Reads the server-configured latest release; null means the check could not complete. */
class RemoteUpdateRepository(baseUrl: String) {
    private val api = createRemoteApi(baseUrl, UpdateApi::class.java, budget = 16_384)

    suspend fun latest(): AppVersionDto? = try {
        api.latest().takeIf { it.isSuccessful }?.body()?.data
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
