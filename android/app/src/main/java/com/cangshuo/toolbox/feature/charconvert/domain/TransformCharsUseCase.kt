package com.cangshuo.toolbox.feature.charconvert.domain

class TransformCharsUseCase(private val repository: CharConvertRepository) {
    operator fun invoke(text: String, options: CharConvertOptions): String =
        repository.transform(text, options)
}
