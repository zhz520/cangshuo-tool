package com.cangshuo.toolbox.feature.imagecompress.domain

class DiscardImageUseCase(private val repository: ImageCompressRepository) {
    operator fun invoke(result: CompressResult) = repository.discard(result)
}
