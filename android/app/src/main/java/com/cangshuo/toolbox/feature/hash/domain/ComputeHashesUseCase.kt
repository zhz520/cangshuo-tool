package com.cangshuo.toolbox.feature.hash.domain

class ComputeHashesUseCase(
    private val repository: HashRepository,
) {
    operator fun invoke(input: String, uppercase: Boolean): List<HashResult> {
        return repository.computeHashes(input, uppercase)
    }
}
