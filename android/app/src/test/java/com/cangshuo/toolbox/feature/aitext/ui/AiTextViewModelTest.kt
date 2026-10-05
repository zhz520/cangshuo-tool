package com.cangshuo.toolbox.feature.aitext.ui

import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.aitext.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AiTextViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private class Repository : AiTextRepository {
        override val users = MutableStateFlow<Long?>(1L)
        val pending = CompletableDeferred<AiResult>()
        var calls = 0
        override suspend fun status(user: Long) = AiStatus(true,"Test","model",20,0,8000)
        override suspend fun execute(user: Long,input: AiInput): AiResult { calls++; return pending.await() }
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }
    private fun model(repository: Repository) = AiTextViewModel(AiTextUseCases(repository)).also { models.put("ai",it) }
    @Test fun consentAndBusyPreventDuplicatePaidCalls() = runTest {
        val repository=Repository(); val model=model(repository); runCurrent()
        model.input("hello"); model.execute(); runCurrent(); assertEquals(0,repository.calls)
        model.consent(true); model.execute(); runCurrent(); model.execute(); runCurrent(); assertEquals(1,repository.calls)
        repository.pending.complete(AiResult("answer",false,null,null)); runCurrent()
        assertEquals("answer",model.state.value.result?.text); assertFalse(model.state.value.busy)
    }
    @Test fun accountChangeErasesTextConsentAndPendingResult() = runTest {
        val repository=Repository(); val model=model(repository); runCurrent()
        model.input("private text"); model.consent(true); model.execute(); runCurrent()
        repository.users.value=2L; runCurrent(); repository.pending.complete(AiResult("old result",false,null,null)); runCurrent()
        assertEquals(2L,model.state.value.user); assertEquals("",model.state.value.input)
        assertFalse(model.state.value.consent); assertNull(model.state.value.result); assertFalse(model.state.value.busy)
    }
    @Test fun cancelAllowsEditingAndRejectsOldResult() = runTest {
        val repository=Repository(); val model=model(repository); runCurrent()
        model.input("hello"); model.consent(true); model.execute(); runCurrent(); model.cancel()
        model.input("new text"); repository.pending.complete(AiResult("old",false,null,null)); runCurrent()
        assertEquals("new text",model.state.value.input); assertNull(model.state.value.result); assertFalse(model.state.value.busy)
    }
}
