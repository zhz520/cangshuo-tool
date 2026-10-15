package com.cangshuo.toolbox.feature.chineseconvert.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ConvertChineseUseCase(
    private val repository: ChineseConversionRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val execution = Mutex()

    suspend operator fun invoke(text: String, mode: ChineseConversionMode): ChineseConversionOutcome {
        if (text.length > ChineseConversionLimits.MAX_INPUT) {
            return ChineseConversionOutcome.Failure(ChineseConversionError.INPUT_TOO_LONG)
        }
        if (text.isEmpty()) return ChineseConversionOutcome.Success("", ChineseConversionStatistics(0, 0, 0))
        return withContext(dispatcher) {
            execution.withLock {
                val context = currentCoroutineContext()
                val started = nanoTime()
                val checkpoint = ChineseConversionCheckpoint {
                    context.ensureActive()
                    if (nanoTime() - started > ChineseConversionLimits.BUDGET_NANOS) {
                        throw ChineseConversionException(ChineseConversionError.TIME_LIMIT)
                    }
                }
                try {
                    checkpoint.check()
                    if (!Charsets.UTF_8.newEncoder().canEncode(text)) {
                        return@withLock ChineseConversionOutcome.Failure(ChineseConversionError.INVALID_UNICODE)
                    }
                    repository.convert(text, mode, checkpoint).also { checkpoint.check() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: ChineseConversionException) {
                    ChineseConversionOutcome.Failure(error.error)
                } catch (_: Exception) {
                    ChineseConversionOutcome.Failure(ChineseConversionError.FAILED)
                }
            }
        }
    }
}
