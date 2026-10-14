package com.cangshuo.toolbox.feature.pinyin.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ConvertToPinyinUseCase(
    private val repository: PinyinRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val execution = Mutex()

    suspend operator fun invoke(text: String, options: PinyinOptions): PinyinOutcome {
        if (text.length > PinyinLimits.MAX_INPUT_LENGTH) {
            return PinyinOutcome.Failure(PinyinError.INPUT_TOO_LONG)
        }
        if (text.isEmpty()) {
            return PinyinOutcome.Success("", PinyinStatistics())
        }
        return withContext(dispatcher) {
            // Old synchronous work must finish or observe cancellation before the next task
            // starts. The cancellable mutex also prevents overlapping initial dictionary loads.
            execution.withLock {
                val context = currentCoroutineContext()
                val started = System.nanoTime()
                val checkpoint = PinyinCheckpoint {
                    context.ensureActive()
                    if (System.nanoTime() - started > PinyinLimits.WORK_BUDGET_NANOS) {
                        throw PinyinProcessingException(PinyinError.TIME_LIMIT)
                    }
                }
                try {
                    checkpoint.check()
                    if (!Charsets.UTF_8.newEncoder().canEncode(text)) {
                        return@withLock PinyinOutcome.Failure(PinyinError.INVALID_UNICODE)
                    }
                    val conversion = repository.convert(text, options, checkpoint)
                    checkpoint.check()
                    PinyinOutcome.Success(conversion.text, conversion.statistics)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (processing: PinyinProcessingException) {
                    PinyinOutcome.Failure(processing.error)
                } catch (_: Exception) {
                    PinyinOutcome.Failure(PinyinError.PROCESSING_FAILED)
                }
            }
        }
    }
}
