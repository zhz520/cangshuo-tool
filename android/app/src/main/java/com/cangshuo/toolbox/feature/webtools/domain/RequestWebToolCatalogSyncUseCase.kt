package com.cangshuo.toolbox.feature.webtools.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CatalogSyncState { IDLE, REFRESHING, UPDATED, FAILED, COOLDOWN }

/** Application-scoped single flight. Cancelling a screen's wait does not cancel shared sync. */
class RequestWebToolCatalogSyncUseCase(
    private val applicationScope: CoroutineScope,
    private val restore: suspend () -> Boolean,
    private val refresh: suspend () -> Boolean,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutableState = MutableStateFlow(CatalogSyncState.IDLE)
    val state = mutableState.asStateFlow()
    private val lock = Mutex()
    private var inFlight: Deferred<Boolean>? = null
    private var lastAttempt: Long? = null
    private var restoreOnNextRequest = true

    suspend operator fun invoke(manual: Boolean = false): Boolean? {
        val task = lock.withLock {
            inFlight?.takeUnless { it.isCompleted }?.let { return@withLock it }
            val now = monotonicMillis()
            val elapsed = lastAttempt?.let { now - it }
            val interval = if (manual) 2_000 else 300_000
            if (elapsed != null && elapsed >= 0 && elapsed < interval) {
                if (manual) mutableState.value = CatalogSyncState.COOLDOWN
                return@withLock null
            }
            lastAttempt = now
            val restoreFirst = restoreOnNextRequest
            restoreOnNextRequest = false
            applicationScope.async {
                mutableState.value = CatalogSyncState.REFRESHING
                try {
                    if (restoreFirst) restore()
                    val success = refresh()
                    mutableState.value = if (success) CatalogSyncState.UPDATED else CatalogSyncState.FAILED
                    success
                } catch (cancelled: CancellationException) {
                    mutableState.value = CatalogSyncState.IDLE
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = CatalogSyncState.FAILED
                    false
                }
            }.also { inFlight = it }
        }
        return task?.await()
    }
}
