package com.cangshuo.toolbox.feature.imagecompress.domain

import android.graphics.Bitmap
import android.net.Uri

class ReadImageMetadataUseCase(
    private val repository: ImageCompressRepository,
) {
    suspend operator fun invoke(uri: Uri): Result<Pair<ImageMetadata, Bitmap>> =
        repository.readMetadata(uri)
}
