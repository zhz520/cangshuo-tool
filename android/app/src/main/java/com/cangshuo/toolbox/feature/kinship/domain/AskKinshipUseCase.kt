package com.cangshuo.toolbox.feature.kinship.domain

class AskKinshipUseCase(private val repository: KinshipRepository) {
    operator fun invoke(query: KinshipQuery): List<String> = repository.query(query)
}
