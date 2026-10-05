package com.cangshuo.toolbox.feature.aitext.data

import com.cangshuo.toolbox.feature.auth.data.*
import com.cangshuo.toolbox.feature.auth.domain.AuthException
import com.cangshuo.toolbox.feature.aitext.domain.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import retrofit2.Response
import retrofit2.http.*

internal data class AiResultDto(val text: String, val model: String, val truncated: Boolean, val inputTokens: Long?, val outputTokens: Long?) {
    override fun toString() = "AiResultDto[redacted]"
}
internal interface AiApi {
    @GET("tools/ai-text/status") suspend fun status(@Header("Authorization") bearer: String): Response<AuthEnvelope<AiStatus>>
    @POST("tools/ai-text") suspend fun execute(@Header("Authorization") bearer: String, @Body input: AiInput): Response<AuthEnvelope<AiResultDto>>
}
class RemoteAiTextRepository internal constructor(private val api: AiApi, override val users: StateFlow<Long?>,
    private val bearer: suspend (Long,Boolean) -> String) : AiTextRepository {
    constructor(baseUrl: String, users: StateFlow<Long?>, bearer: suspend (Long,Boolean) -> String) :
        this(createRemoteApi(baseUrl,AiApi::class.java,262_144,70),users,bearer)
    override suspend fun status(user: Long): AiStatus {
        val value=request(user) {api.status(it)}
        if (!(value.dailyLimit in 1..1000 && value.usedToday >= 0 && value.maxInputChars==8000 &&
            value.providerName.length in 1..64 && value.model.length<=128 &&
            value.providerName.none { it.isISOControl() } && value.model.none { it.isISOControl() })) throw AiException(AiFailure.SERVICE)
        return value
    }
    override suspend fun execute(user: Long, input: AiInput): AiResult {
        val value=request(user) {api.execute(it,input)}
        if (!(value.text.isNotBlank() && value.text.length<=16000 && value.model.length<=128 &&
            (value.inputTokens==null || value.inputTokens>=0) && (value.outputTokens==null || value.outputTokens>=0))) throw AiException(AiFailure.SERVICE)
        return AiResult(value.text,value.truncated,value.inputTokens,value.outputTokens)
    }
    private suspend fun <T> request(user: Long, call: suspend (String)->Response<AuthEnvelope<T>>): T {
        if(users.value!=user)throw AiException(AiFailure.LOGIN)
        try {
            var response=call(bearer(user,false))
            if(response.code()==401){response.errorBody()?.close();response=call(bearer(user,true))}
            if(users.value!=user){response.errorBody()?.close();throw AiException(AiFailure.LOGIN)}
            if(!response.isSuccessful){
                val status=response.code()
                val error=response.errorBody()?.use {it.string()}.orEmpty()
                val quota=status==429 && Regex("\"code\"\\s*:\\s*40005\\b").containsMatchIn(error)
                throw AiException(when{quota->AiFailure.QUOTA;status==401->AiFailure.LOGIN;status==429->AiFailure.LIMITED;
                    status==503->AiFailure.DISABLED;status==400->AiFailure.INPUT;else->AiFailure.SERVICE})
            }
            val envelope=response.body()?:throw AiException(AiFailure.SERVICE)
            if(envelope.code!=0)throw AiException(AiFailure.SERVICE)
            return envelope.data?:throw AiException(AiFailure.SERVICE)
        }catch(cancelled: CancellationException){throw cancelled}
        catch(error: AiException){throw error}
        catch(_: AuthException){throw AiException(AiFailure.LOGIN)}
        catch(_: IOException){throw AiException(AiFailure.NETWORK)}
        catch(_: Exception){throw AiException(AiFailure.SERVICE)}
    }
}
