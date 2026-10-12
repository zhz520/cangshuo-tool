package com.cangshuo.toolbox.feature.password.data

import com.cangshuo.toolbox.feature.password.domain.GeneratedPassword
import com.cangshuo.toolbox.feature.password.domain.PasswordError
import com.cangshuo.toolbox.feature.password.domain.PasswordLimits
import com.cangshuo.toolbox.feature.password.domain.PasswordOptions
import com.cangshuo.toolbox.feature.password.domain.PasswordOutcome
import com.cangshuo.toolbox.feature.password.domain.PasswordRepository
import com.cangshuo.toolbox.feature.password.domain.RandomSource
import java.security.SecureRandom
import kotlin.math.ln

/**
 * Local password generation. Character classes and the ambiguous-character set follow the
 * MIT-licensed brendanashworth/generate-password reference, while the strict "one per class"
 * rule is built constructively (pick one per class, fill, then Fisher-Yates shuffle) instead of
 * regenerating until the rules happen to match, so a valid result is guaranteed in one pass.
 * Entropy is reported as length * log2(pool size).
 */
class LocalPasswordRepository(private val random: RandomSource = SecureRandomSource()) : PasswordRepository {

    override fun generate(options: PasswordOptions, count: Int): PasswordOutcome {
        if (options.length !in PasswordLimits.MIN_LENGTH..PasswordLimits.MAX_LENGTH) {
            return PasswordOutcome.Failure(PasswordError.LENGTH_RANGE)
        }
        if (count !in PasswordLimits.MIN_COUNT..PasswordLimits.MAX_COUNT) {
            return PasswordOutcome.Failure(PasswordError.COUNT_RANGE)
        }
        val excluded = HashSet<Char>()
        if (options.excludeSimilar) PasswordLimits.SIMILAR_CHARACTERS.forEach { excluded.add(it) }
        options.excludeText.filterNot(Char::isWhitespace).forEach { excluded.add(it) }
        val classPools = enabledClasses(options).map { pool -> pool.filterNot { it in excluded } }
        val available = classPools.flatten().distinct()
        if (available.isEmpty()) return PasswordOutcome.Failure(PasswordError.NO_CHARSET)
        val activeClasses = classPools.filter { it.isNotEmpty() }
        if (options.requireEachClass && activeClasses.size > options.length) {
            return PasswordOutcome.Failure(PasswordError.STRICT_LENGTH)
        }
        if (options.uniqueCharacters && available.size < options.length) {
            return PasswordOutcome.Failure(PasswordError.UNIQUE_IMPOSSIBLE)
        }
        val entropyBits = options.length * (ln(available.size.toDouble()) / ln(2.0))
        val passwords = List(count) { GeneratedPassword(generateOne(options, activeClasses, available), entropyBits) }
        return PasswordOutcome.Success(passwords, available.size)
    }

    private fun enabledClasses(options: PasswordOptions): List<List<Char>> = buildList<List<Char>> {
        if (options.lowercase) add(('a'..'z').toList())
        if (options.uppercase) add(('A'..'Z').toList())
        if (options.digits) add(('0'..'9').toList())
        if (options.symbols) add(PasswordLimits.SYMBOLS.toList())
    }

    private fun generateOne(
        options: PasswordOptions,
        activeClasses: List<List<Char>>,
        available: List<Char>,
    ): String {
        val remaining = available.toMutableList()
        val picked = StringBuilder()
        if (options.requireEachClass) {
            for (pool in activeClasses) {
                val candidates = pool.filter { it in remaining }
                val chosen = candidates[random.nextInt(candidates.size)]
                picked.append(chosen)
                if (options.uniqueCharacters) remaining.remove(chosen)
            }
        }
        while (picked.length < options.length) {
            val index = random.nextInt(remaining.size)
            picked.append(remaining[index])
            if (options.uniqueCharacters) remaining.removeAt(index)
        }
        for (i in picked.length - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val swap = picked[i]
            picked.setCharAt(i, picked[j])
            picked.setCharAt(j, swap)
        }
        return picked.toString()
    }
}

/** Unbiased [java.security.SecureRandom] source (nextInt bounds without modulo bias). */
class SecureRandomSource : RandomSource {
    private val random = SecureRandom()

    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}
