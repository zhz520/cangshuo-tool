package com.cangshuo.toolbox.feature.hash.domain

enum class HashAlgorithm(
    val displayName: String,
    val standardName: String,
    val bitLength: Int,
) {
    MD5("MD5", "MD5", 128),
    SHA1("SHA-1", "SHA-1", 160),
    SHA224("SHA-224", "SHA-224", 224),
    SHA256("SHA-256", "SHA-256", 256),
    SHA384("SHA-384", "SHA-384", 384),
    SHA512("SHA-512", "SHA-512", 512),
}
