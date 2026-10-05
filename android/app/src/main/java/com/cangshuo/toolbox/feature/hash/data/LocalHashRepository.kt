package com.cangshuo.toolbox.feature.hash.data

import com.cangshuo.toolbox.feature.hash.domain.HashAlgorithm
import com.cangshuo.toolbox.feature.hash.domain.HashRepository
import com.cangshuo.toolbox.feature.hash.domain.HashResult
import java.security.MessageDigest

class LocalHashRepository : HashRepository {

    override fun computeHashes(input: String, uppercase: Boolean): List<HashResult> {
        if (input.isEmpty()) return emptyList()
        val bytes = input.toByteArray(Charsets.UTF_8)
        return HashAlgorithm.entries.mapNotNull { algorithm ->
            computeDigest(bytes, algorithm, uppercase)
        }
    }

    override fun computeSingleHash(input: String, algorithm: HashAlgorithm, uppercase: Boolean): HashResult? {
        if (input.isEmpty()) return null
        val bytes = input.toByteArray(Charsets.UTF_8)
        return computeDigest(bytes, algorithm, uppercase)
    }

    private fun computeDigest(bytes: ByteArray, algorithm: HashAlgorithm, uppercase: Boolean): HashResult? {
        return runCatching {
            val md = MessageDigest.getInstance(algorithm.standardName)
            val digest = md.digest(bytes)
            HashResult(
                algorithm = algorithm,
                hash = digest.toHexString(uppercase),
            )
        }.getOrNull()
    }

    private fun ByteArray.toHexString(uppercase: Boolean): String {
        val hexChars = if (uppercase) HEX_CHARS_UPPER else HEX_CHARS_LOWER
        val result = StringBuilder(size * 2)
        for (b in this) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i ushr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

    companion object {
        private const val HEX_CHARS_LOWER = "0123456789abcdef"
        private const val HEX_CHARS_UPPER = "0123456789ABCDEF"
    }
}
