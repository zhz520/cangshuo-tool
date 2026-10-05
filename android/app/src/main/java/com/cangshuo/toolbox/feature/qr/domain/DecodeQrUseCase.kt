package com.cangshuo.toolbox.feature.qr.domain

import android.net.Uri

class DecodeQrUseCase(private val repository: QrRepository) {
    suspend operator fun invoke(uri: Uri, multi: Boolean = false, region: QrCropRegion? = null): QrDecodeResult =
        repository.decode(uri, multi, region)
}
