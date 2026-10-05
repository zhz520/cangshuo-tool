package com.cangshuo.toolbox.feature.hash.domain

interface HashRepository {
    fun computeHashes(input: String, uppercase: Boolean): List<HashResult>
    fun computeSingleHash(input: String, algorithm: HashAlgorithm, uppercase: Boolean): HashResult?
}
