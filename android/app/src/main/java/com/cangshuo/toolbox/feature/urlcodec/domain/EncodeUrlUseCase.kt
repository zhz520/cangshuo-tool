package com.cangshuo.toolbox.feature.urlcodec.domain

class EncodeUrlUseCase(private val repository: UrlCodecRepository) {
    suspend operator fun invoke(text: String, type: UrlEncodeType): UrlCodecResult =
        repository.encode(text, type)
}
