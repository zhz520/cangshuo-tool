package com.cangshuo.toolbox.feature.feedback.domain

import kotlinx.coroutines.flow.StateFlow

enum class FeedbackFailure { INPUT, LOGIN, NETWORK, LIMITED, SERVICE, UNCERTAIN }
class FeedbackException(val failure: FeedbackFailure) : Exception("Feedback unavailable")
data class FeedbackEntry(val id: Long, val type: String, val content: String, val status: String, val reply: String?, val createdAt: String) {
    override fun toString() = "FeedbackEntry[redacted]"
}
data class FeedbackDraft(val type: String, val content: String, val contact: String?) {
    override fun toString() = "FeedbackDraft[redacted]"
}
object FeedbackPolicy {
    fun draft(type: String, content: String, contact: String): FeedbackDraft {
        val body = content.trim()
        val address = contact.trim()
        if (type !in setOf("BUG", "SUGGESTION", "OTHER") || body.isEmpty() || body.length > 2000 ||
            body.any { it.isISOControl() && it != '\n' } || address.length > 128 || address.any(Char::isISOControl))
            throw FeedbackException(FeedbackFailure.INPUT)
        return FeedbackDraft(type, body, address.takeIf(String::isNotEmpty))
    }
}
interface FeedbackRepository {
    val users: StateFlow<Long?>
    suspend fun mine(user: Long): List<FeedbackEntry>
    suspend fun submit(user: Long, draft: FeedbackDraft): FeedbackEntry
}
class FeedbackUseCases(private val repository: FeedbackRepository) {
    val users get() = repository.users
    suspend fun mine(user: Long) = repository.mine(user)
    suspend fun submit(user: Long, type: String, content: String, contact: String) =
        repository.submit(user, FeedbackPolicy.draft(type, content, contact))
}
