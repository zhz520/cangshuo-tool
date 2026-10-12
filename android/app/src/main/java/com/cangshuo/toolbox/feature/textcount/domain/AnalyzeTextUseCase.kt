package com.cangshuo.toolbox.feature.textcount.domain

class AnalyzeTextUseCase(private val repository: TextCountRepository) {
    operator fun invoke(text: String): TextStats = repository.analyze(text)
}
