package com.cangshuo.toolbox.feature.json.data

import com.cangshuo.toolbox.feature.json.domain.JsonFailure
import com.cangshuo.toolbox.feature.json.domain.JsonIndent
import com.cangshuo.toolbox.feature.json.domain.JsonProcessResult
import com.cangshuo.toolbox.feature.json.domain.JsonProcessingException
import com.cangshuo.toolbox.feature.json.domain.JsonProcessingPolicy
import com.cangshuo.toolbox.feature.json.domain.JsonRepository
import com.cangshuo.toolbox.feature.json.domain.JsonStringInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalJsonRepository : JsonRepository {
    private val workMutex = Mutex()

    override suspend fun format(input: String, indent: JsonIndent): JsonProcessResult = document(input, indent.spaces)
    override suspend fun compress(input: String): JsonProcessResult = document(input, 0)

    private suspend fun document(input: String, indent: Int): JsonProcessResult = work(input) { guard, bytes ->
        val reader = StrictJsonReader(input, guard, indent)
        val output = reader.document()
        if (output == null) JsonProcessResult.Empty(bytes) else success(output, bytes, guard, reader.duplicateKeyCount)
    }

    override suspend fun escape(input: String): JsonProcessResult = work(input) { guard, bytes ->
        val output = JsonOutput(input.length, guard)
        output.append('"')
        for (char in input) {
            guard.step()
            when (char) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\u000C' -> output.append("\\f")
                '\n' -> output.append("\\n")
                '\r' -> output.append("\\r")
                '\t' -> output.append("\\t")
                else -> if (char.code < 0x20) {
                    output.append("\\u00")
                    output.append(HEX[char.code ushr 4])
                    output.append(HEX[char.code and 15])
                } else output.append(char)
            }
        }
        output.append('"')
        success(output.toString(), bytes, guard)
    }

    override suspend fun unescape(input: String, stringInput: JsonStringInput): JsonProcessResult = work(input) { guard, bytes ->
        val output = StrictJsonReader(input, guard).decodedString(stringInput == JsonStringInput.QUOTED_LITERAL)
        success(output, bytes, guard)
    }

    private fun success(output: String, inputBytes: Int, guard: JsonWorkGuard, duplicates: Int = 0): JsonProcessResult.Success {
        val metrics = measureJsonText(output, guard)
        return JsonProcessResult.Success(output, metrics.lines, metrics.bytes, inputBytes, duplicates)
    }

    private suspend fun work(input: String, block: (JsonWorkGuard, Int) -> JsonProcessResult): JsonProcessResult = withContext(Dispatchers.Default) {
        workMutex.withLock {
            val guard = JsonWorkGuard(currentCoroutineContext())
            var bytes: Int? = null
            try {
                guard.checkpoint()
                if (input.length > JsonProcessingPolicy.MAX_INPUT_LENGTH) throw JsonProcessingException(JsonFailure.INPUT_LIMIT)
                bytes = measureJsonText(input, guard).bytes
                val result = block(guard, bytes)
                guard.checkpoint()
                result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: JsonProcessingException) {
                val position = error.offset?.let { jsonErrorPosition(input, it, guard) }
                JsonProcessResult.Error(error.reason, position?.first, position?.second, bytes)
            } catch (_: StackOverflowError) { JsonProcessResult.Error(JsonFailure.DEPTH_LIMIT, inputByteCount = bytes) }
            catch (_: OutOfMemoryError) { JsonProcessResult.Error(JsonFailure.MEMORY_LIMIT, inputByteCount = bytes) }
            catch (_: Exception) { JsonProcessResult.Error(JsonFailure.PROCESSING_FAILED, inputByteCount = bytes) }
        }
    }

    companion object { private const val HEX = "0123456789abcdef" }
}
