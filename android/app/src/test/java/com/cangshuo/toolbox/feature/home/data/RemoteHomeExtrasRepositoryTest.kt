package com.cangshuo.toolbox.feature.home.data

import com.cangshuo.toolbox.feature.auth.data.AuthEnvelope
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RemoteHomeExtrasRepositoryTest {
    private class Api : HomeExtrasApi {
        var slots = listOf(RecommendationDto("first", "Title", null, "calculator", null, null))
        var notices = listOf(AnnouncementDto(1, "Title", "Line 1\nLine 2", "WARNING"))
        var status = 200
        override suspend fun recommendations(): Response<AuthEnvelope<List<RecommendationDto>>> =
            if (status == 200) Response.success(AuthEnvelope(0,"success",slots,"trace-1")) else Response.error(status,ResponseBody.create(null,""))
        override suspend fun announcements() = Response.success(AuthEnvelope(0,"success",notices,"trace-1"))
    }
    @Test fun publicResponsesMapPlainTextAndToolTargets() = runTest {
        val repository = RemoteHomeExtrasRepository(Api())
        assertEquals("calculator", repository.recommendations().single().toolCode)
        assertEquals("Line 1\nLine 2", repository.announcements().single().body)
    }
    @Test fun malformedAndDuplicateTargetsAreRejected() = runTest {
        val api = Api()
        val repository = RemoteHomeExtrasRepository(api)
        for (slots in listOf(api.slots + api.slots, listOf(api.slots.single().copy(linkUrl="https://example.com")),
            listOf(api.slots.single().copy(toolCode=null,linkUrl="http://example.com")),
            listOf(api.slots.single().copy(toolCode=null,linkUrl="https://user:pass@example.com")),
            List(21) { api.slots.single().copy(slotCode="slot_$it") })) {
            api.slots=slots
            try { repository.recommendations(); fail("Expected invalid response") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun announcementLimitsAndLevelsAreChecked() = runTest {
        val api=Api();val repository=RemoteHomeExtrasRepository(api)
        for (items in listOf(listOf(AnnouncementDto(0,"Title","Body","INFO")),
            listOf(AnnouncementDto(1,"Title","Body","UNKNOWN")),List(6){AnnouncementDto(it+1L,"Title","Body","INFO")})) {
            api.notices=items
            try { repository.announcements(); fail("Expected invalid response") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun httpFailureDoesNotBecomeAnEmptySuccess() = runTest {
        val api=Api();api.status=503
        try { RemoteHomeExtrasRepository(api).recommendations();fail("Expected failure") } catch (_: IllegalStateException) { }
    }
}
