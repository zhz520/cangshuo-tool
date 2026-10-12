package com.cangshuo.toolbox.feature.password.domain

/** Injectable randomness so the generator stays testable. */
fun interface RandomSource {
    /** Returns a value in [0, bound); bound is always positive. */
    fun nextInt(bound: Int): Int
}

interface PasswordRepository {
    fun generate(options: PasswordOptions, count: Int): PasswordOutcome
}
