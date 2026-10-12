package com.cangshuo.toolbox.feature.kinship.domain

enum class KinshipSex(val value: Int) { UNSPECIFIED(-1), MALE(1), FEMALE(0) }

enum class KinshipType { TITLE, CHAIN, PAIR }

data class KinshipQuery(
    val text: String,
    val target: String = "",
    val sex: KinshipSex = KinshipSex.UNSPECIFIED,
    val type: KinshipType = KinshipType.TITLE,
    val reverse: Boolean = false,
    val optimal: Boolean = false,
)

enum class KinshipError { EMPTY_INPUT, INPUT_TOO_LONG, DATA_UNAVAILABLE }

object KinshipLimits {
    const val MAX_INPUT_LENGTH = 60
}

sealed interface KinshipOutcome {
    data class Success(val titles: List<String>) : KinshipOutcome

    data class Failure(val error: KinshipError) : KinshipOutcome
}
