package com.cangshuo.toolbox.feature.qr.domain

class GenerateQrUseCase(private val repository: QrRepository) {
    suspend operator fun invoke(input: QrInput, errorCorrection: QrErrorCorrection): QrGenerateResult {
        QrInputPolicy.validate(input)?.let { return QrGenerateResult.Error(it) }
        val content = QrInputPolicy.content(input)
        if (content.isEmpty()) return QrGenerateResult.Empty
        return repository.generate(content, errorCorrection, input.format)
    }
}
