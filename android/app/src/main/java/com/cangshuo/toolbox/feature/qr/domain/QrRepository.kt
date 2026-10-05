package com.cangshuo.toolbox.feature.qr.domain

import android.net.Uri

/** Result of loading a bounded preview for the manual crop overlay. */
sealed interface QrPreviewResult {
    data class Success(val bitmap: android.graphics.Bitmap) : QrPreviewResult
    data class Error(val reason: QrFailure) : QrPreviewResult
}

class LoadQrPreviewUseCase(private val repository: QrRepository) {
    suspend operator fun invoke(uri: Uri) = repository.loadPreview(uri)
}

interface QrRepository {
    suspend fun generate(content: String, errorCorrection: QrErrorCorrection,
        format: QrCodeFormat = QrCodeFormat.QR_CODE): QrGenerateResult
    /** [multi] enables the QR multi-code reader; a single code is still reported as [QrDecodeResult.Success]. */
    suspend fun decode(uri: Uri, multi: Boolean = false, region: QrCropRegion? = null): QrDecodeResult
    /** Bounded ARGB preview for the manual crop overlay; callers must not recycle the returned bitmap. */
    suspend fun loadPreview(uri: Uri): QrPreviewResult
}
