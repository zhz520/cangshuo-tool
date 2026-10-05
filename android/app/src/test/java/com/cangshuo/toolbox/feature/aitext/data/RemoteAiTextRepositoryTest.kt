package com.cangshuo.toolbox.feature.aitext.data

import com.cangshuo.toolbox.feature.auth.data.AuthEnvelope
import com.cangshuo.toolbox.feature.aitext.domain.*
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RemoteAiTextRepositoryTest {
    private class Api : AiApi {
        var calls=0;var status=200;var io=false
        override suspend fun status(bearer: String)=Response.success(AuthEnvelope(0,"success",AiStatus(false,"Test","",20,0,8000),"trace"))
        override suspend fun execute(bearer: String,input: AiInput): Response<AuthEnvelope<AiResultDto>> {
            calls++;if(io)throw IOException("Failure")
            if(status!=200)return Response.error(status,ResponseBody.create(null,"{\"code\":40005}"))
            return Response.success(AuthEnvelope(0,"success",AiResultDto("answer","model",true,null,2),"trace"))
        }
    }
    @Test fun disabledStatusAndTruncationArePreserved()=runTest {
        val repository=RemoteAiTextRepository(Api(),MutableStateFlow(1L)){_,_->"Bearer test"}
        assertFalse(repository.status(1).enabled)
        val result=repository.execute(1,AiInput("REWRITE","input",null))
        assertTrue(result.truncated);assertNull(result.inputTokens);assertEquals(2L,result.outputTokens)
    }
    @Test fun quotaCodeHasSpecificFailure()=runTest {
        val api=Api();api.status=429;val repository=RemoteAiTextRepository(api,MutableStateFlow(1L)){_,_->"Bearer test"}
        try{repository.execute(1,AiInput("REWRITE","input",null));fail("Expected quota failure")}
        catch(error: AiException){assertEquals(AiFailure.QUOTA,error.failure)}
    }
    @Test fun failedProviderCallIsNotAutomaticallyRetried()=runTest {
        val api=Api();api.io=true;val repository=RemoteAiTextRepository(api,MutableStateFlow(1L)){_,_->"Bearer test"}
        try{repository.execute(1,AiInput("REWRITE","input",null));fail("Expected network failure")}
        catch(error: AiException){assertEquals(AiFailure.NETWORK,error.failure)}
        assertEquals(1,api.calls)
    }
    @Test fun invalidInputDoesNotCallProvider()=runTest {
        val api=Api();val cases=AiTextUseCases(RemoteAiTextRepository(api,MutableStateFlow(1L)){_,_->"Bearer test"})
        for(text in listOf("", "x".repeat(8001),"x\u0000")) {
            try{cases.execute(1,"REWRITE",text,"en");fail("Expected input failure")}
            catch(error: AiException){assertEquals(AiFailure.INPUT,error.failure)}
        }
        assertEquals(0,api.calls)
    }
}
