package com.cangshuo.toolbox.feature.urlcodec.domain

class DecodeUrlUseCase(private val repository: UrlCodecRepository) {
    suspend operator fun invoke(text: String, type: UrlEncodeType = UrlEncodeType.COMPONENT): UrlCodecResult =
        repository.decode(text, type)
}
