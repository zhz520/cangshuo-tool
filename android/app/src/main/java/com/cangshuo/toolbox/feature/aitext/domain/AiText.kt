package com.cangshuo.toolbox.feature.aitext.domain

import kotlinx.coroutines.flow.StateFlow

enum class AiFailure { INPUT, LOGIN, DISABLED, QUOTA, LIMITED, NETWORK, SERVICE }
class AiException(val failure: AiFailure) : Exception("AI unavailable")
data class AiStatus(val enabled: Boolean, val providerName: String, val model: String, val dailyLimit: Int, val usedToday: Int, val maxInputChars: Int)
data class AiResult(val text: String, val truncated: Boolean, val inputTokens: Long?, val outputTokens: Long?) {
    override fun toString() = "AiResult[redacted]"
}
data class AiInput(val task: String, val text: String, val targetLanguage: String?) {
    override fun toString() = "AiInput[redacted]"
}
interface AiTextRepository {
    val users: StateFlow<Long?>
    suspend fun status(user: Long): AiStatus
    suspend fun execute(user: Long, input: AiInput): AiResult
}
class AiTextUseCases(private val repository: AiTextRepository) {
    val users get() = repository.users
    suspend fun status(user: Long) = repository.status(user)
    suspend fun execute(user: Long, task: String, text: String, target: String): AiResult {
        if (text.isBlank() || text.length > 8000 || task !in setOf("SUMMARIZE", "REWRITE", "TRANSLATE") ||
            text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } || target !in setOf("zh", "en")) throw AiException(AiFailure.INPUT)
        return repository.execute(user, AiInput(task, text, target.takeIf { task == "TRANSLATE" }))
    }
}
