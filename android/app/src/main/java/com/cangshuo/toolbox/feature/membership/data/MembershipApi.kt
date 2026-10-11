package com.cangshuo.toolbox.feature.membership.data

import com.cangshuo.toolbox.feature.auth.data.createRemoteApi
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

data class MembershipDto(val plan: String = "FREE", val expiresAt: String? = null, val active: Boolean = false) {
    override fun toString() = "MembershipDto[redacted]"
}

internal data class MembershipEnvelope<T>(val code: Int, val message: String, val traceId: String, val data: T?) {
    override fun toString() = "MembershipEnvelope[redacted]"
}

internal interface MembershipApi {
    @GET("membership") suspend fun status(@Header("Authorization") bearer: String): Response<MembershipEnvelope<MembershipDto>>
}

/** Reads the signed-in account's membership; null means the status could not be loaded. */
class RemoteMembershipRepository(baseUrl: String, private val bearer: suspend () -> String) {
    private val api = createRemoteApi(baseUrl, MembershipApi::class.java, budget = 8_192)

    suspend fun status(): MembershipDto? = try {
        api.status(bearer()).takeIf { it.isSuccessful }?.body()?.data
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
