package com.cangshuo.toolbox.feature.feedback.data

import com.cangshuo.toolbox.feature.auth.data.AuthEnvelope
import com.cangshuo.toolbox.feature.feedback.domain.*
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RemoteFeedbackRepositoryTest {
    private val value = FeedbackDto(1,"BUG","问题",null,"RESOLVED","回复","2026-10-06T00:00:00Z","2026-10-06T00:00:00Z","2026-10-06T00:00:00Z")
    private inner class Api : FeedbackApi {
        var calls = 0
        var unauthorized = false
        var io = false
        override suspend fun mine(bearer: String): Response<AuthEnvelope<List<FeedbackDto>>> {
            calls++
            if (unauthorized && calls == 1) return Response.error(401, ResponseBody.create(null,""))
            return Response.success(AuthEnvelope(0,"success",listOf(value),"trace"))
        }
        override suspend fun submit(bearer: String, draft: FeedbackDraft): Response<AuthEnvelope<FeedbackDto>> {
            calls++
            if (io) throw IOException("Failure")
            return Response.success(AuthEnvelope(0,"success",value,"trace"))
        }
    }
    @Test fun oneRefreshRetryShowsOwnReply() = runTest {
        val api = Api(); api.unauthorized = true
        val forces = mutableListOf<Boolean>()
        val repository = RemoteFeedbackRepository(api,MutableStateFlow(1L)) { _,force -> forces.add(force); "Bearer test" }
        assertEquals("回复", repository.mine(1).single().reply)
        assertEquals(listOf(false,true),forces);assertEquals(2,api.calls)
    }
    @Test fun accountChangeRejectsOldResponse() = runTest {
        val users = MutableStateFlow<Long?>(1)
        val repository = RemoteFeedbackRepository(Api(),users) { _,_ -> users.value=2; "Bearer test" }
        try { repository.mine(1);fail("Expected ownership rejection") }
        catch (error: FeedbackException) {assertEquals(FeedbackFailure.LOGIN,error.failure)}
    }
    @Test fun ambiguousSubmissionIsNeverAutomaticallyRetried() = runTest {
        val api=Api();api.io=true
        val repository=RemoteFeedbackRepository(api,MutableStateFlow(1L)) { _,_ -> "Bearer test" }
        try {repository.submit(1,FeedbackPolicy.draft("BUG","body",""));fail("Expected failure")}
        catch(error: FeedbackException){assertEquals(FeedbackFailure.UNCERTAIN,error.failure)}
        assertEquals(1,api.calls)
    }
}
