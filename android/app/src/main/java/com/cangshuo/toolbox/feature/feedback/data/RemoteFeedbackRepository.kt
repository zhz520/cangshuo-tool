package com.cangshuo.toolbox.feature.feedback.data

import com.cangshuo.toolbox.feature.auth.data.*
import com.cangshuo.toolbox.feature.auth.domain.AuthException
import com.cangshuo.toolbox.feature.feedback.domain.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import retrofit2.Response
import retrofit2.http.*

internal data class FeedbackDto(val id: Long, val type: String, val content: String, val contact: String?, val status: String,
    val reply: String?, val repliedAt: String?, val createdAt: String, val updatedAt: String) {
    override fun toString() = "FeedbackDto[redacted]"
}
internal interface FeedbackApi {
    @GET("feedback/my") suspend fun mine(@Header("Authorization") bearer: String): Response<AuthEnvelope<List<FeedbackDto>>>
    @POST("feedback") suspend fun submit(@Header("Authorization") bearer: String, @Body draft: FeedbackDraft): Response<AuthEnvelope<FeedbackDto>>
}
class RemoteFeedbackRepository internal constructor(private val api: FeedbackApi, override val users: StateFlow<Long?>,
    private val bearer: suspend (Long, Boolean) -> String) : FeedbackRepository {
    constructor(baseUrl: String, users: StateFlow<Long?>, bearer: suspend (Long, Boolean) -> String) :
        this(createRemoteApi(baseUrl, FeedbackApi::class.java, 262_144), users, bearer)
    override suspend fun mine(user: Long): List<FeedbackEntry> {
        val result = request(user, false) { api.mine(it) }
        require(result.size <= 50 && result.map { it.id }.distinct().size == result.size)
        return result.map(::entry)
    }
    override suspend fun submit(user: Long, draft: FeedbackDraft) = entry(request(user, true) { api.submit(it, draft) })
    private fun entry(value: FeedbackDto): FeedbackEntry {
        require(value.id > 0 && value.type in setOf("BUG", "SUGGESTION", "OTHER") && value.status in setOf("PENDING", "PROCESSING", "RESOLVED"))
        FeedbackPolicy.draft(value.type, value.content, value.contact.orEmpty())
        require(value.reply == null || value.reply.length <= 2000 && value.reply.none { it.isISOControl() && it != '\n' })
        Instant.parse(value.createdAt); Instant.parse(value.updatedAt); value.repliedAt?.let(Instant::parse)
        return FeedbackEntry(value.id, value.type, value.content, value.status, value.reply, value.createdAt)
    }
    private suspend fun <T> request(user: Long, writing: Boolean, call: suspend (String) -> Response<AuthEnvelope<T>>): T {
        if (users.value != user) throw FeedbackException(FeedbackFailure.LOGIN)
        try {
            var response = call(bearer(user, false))
            if (response.code() == 401) { response.errorBody()?.close(); response = call(bearer(user, true)) }
            response.errorBody()?.close()
            if (users.value != user) throw FeedbackException(FeedbackFailure.LOGIN)
            if (!response.isSuccessful) throw FeedbackException(when (response.code()) {
                401 -> FeedbackFailure.LOGIN; 429 -> FeedbackFailure.LIMITED; 400 -> FeedbackFailure.INPUT; else -> FeedbackFailure.SERVICE
            })
            val envelope = response.body() ?: throw FeedbackException(FeedbackFailure.SERVICE)
            if (envelope.code != 0) throw FeedbackException(FeedbackFailure.SERVICE)
            return envelope.data ?: throw FeedbackException(FeedbackFailure.SERVICE)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: FeedbackException) { throw error }
        catch (_: AuthException) { throw FeedbackException(FeedbackFailure.LOGIN) }
        catch (_: IOException) { throw FeedbackException(if (writing) FeedbackFailure.UNCERTAIN else FeedbackFailure.NETWORK) }
        catch (_: Exception) { throw FeedbackException(FeedbackFailure.SERVICE) }
    }
}
