package com.cangshuo.toolbox.feature.webtools.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RequestWebToolCatalogSyncUseCaseTest {
    @Test
    fun firstRequestRestoresOnceBeforeNetworkAndLaterRequestsNeverRestoreOldCache() = runTest {
        val events = mutableListOf<String>()
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { events += "restore"; true }, { events += "network"; true }) { testScheduler.currentTime }
        assertEquals(true, sync())
        advanceTimeBy(2000)
        assertEquals(true, sync(manual = true))
        assertEquals(listOf("restore", "network", "network"), events)
        assertEquals(CatalogSyncState.UPDATED, sync.state.value)
    }

    @Test
    fun concurrentStartupForegroundAndManualRequestsShareOneFetch() = runTest {
        val response = CompletableDeferred<Boolean>()
        var calls = 0
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { false }, { calls++; response.await() }) { testScheduler.currentTime }
        val callers = List(20) { index -> async { sync(manual = index % 2 == 0) } }
        runCurrent()
        assertEquals(1, calls)
        assertEquals(CatalogSyncState.REFRESHING, sync.state.value)
        response.complete(true)
        callers.forEach { assertEquals(true, it.await()) }
        assertEquals(CatalogSyncState.UPDATED, sync.state.value)
    }

    @Test
    fun automaticForegroundRefreshIsLimitedToOneAttemptPerFiveMinutes() = runTest {
        var calls = 0
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { false }, { calls++; true }) { testScheduler.currentTime }
        sync()
        advanceTimeBy(299_999)
        assertNull(sync())
        assertEquals(1, calls)
        advanceTimeBy(1)
        assertEquals(true, sync())
        assertEquals(2, calls)
    }

    @Test
    fun manualRefreshBypassesAutomaticIntervalButRejectsTapBursts() = runTest {
        var calls = 0
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { false }, { calls++; true }) { testScheduler.currentTime }
        sync()
        assertNull(sync(manual = true))
        assertEquals(CatalogSyncState.COOLDOWN, sync.state.value)
        advanceTimeBy(2000)
        assertEquals(true, sync(manual = true))
        assertEquals(2, calls)
    }

    @Test
    fun failedRequestExitsProgressAndCanRetryWithoutWaitingFiveMinutes() = runTest {
        var success = false
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { false }, { success }) { testScheduler.currentTime }
        assertEquals(false, sync())
        assertEquals(CatalogSyncState.FAILED, sync.state.value)
        success = true
        advanceTimeBy(2000)
        assertEquals(true, sync(manual = true))
        assertEquals(CatalogSyncState.UPDATED, sync.state.value)
    }

    @Test
    fun cancellingOneWaiterDoesNotCancelApplicationSyncOrOtherWaiters() = runTest {
        val response = CompletableDeferred<Boolean>()
        var calls = 0
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { false }, { calls++; response.await() }) { testScheduler.currentTime }
        val first = async { sync() }
        val second = async { sync(manual = true) }
        runCurrent()
        first.cancelAndJoin()
        assertFalse(response.isCancelled)
        assertEquals(CatalogSyncState.REFRESHING, sync.state.value)
        response.complete(true)
        assertEquals(true, second.await())
        assertEquals(1, calls)
    }

    @Test
    fun applicationCancellationClearsProgress() = runTest {
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val sync = RequestWebToolCatalogSyncUseCase(appScope, { false }, { CompletableDeferred<Boolean>().await() }) { testScheduler.currentTime }
        val caller = async { sync() }
        runCurrent()
        appScope.cancel()
        caller.join()
        assertTrue(caller.isCancelled)
        assertEquals(CatalogSyncState.IDLE, sync.state.value)
    }

    @Test
    fun unexpectedFailureIsReducedToFixedStateWithoutRawException() = runTest {
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { false }, { throw IllegalStateException("sensitive-secret") }) { testScheduler.currentTime }
        assertEquals(false, sync())
        assertEquals(CatalogSyncState.FAILED, sync.state.value)
    }

    @Test
    fun missingCacheStillAllowsNetworkUpdate() = runTest {
        var requests = 0
        val sync = RequestWebToolCatalogSyncUseCase(backgroundScope, { false }, { requests++; true }) { testScheduler.currentTime }
        assertEquals(true, sync())
        assertEquals(1, requests)
    }
}
