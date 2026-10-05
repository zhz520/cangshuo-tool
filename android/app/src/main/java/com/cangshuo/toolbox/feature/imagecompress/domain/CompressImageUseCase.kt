package com.cangshuo.toolbox.feature.imagecompress.domain

import android.net.Uri

class CompressImageUseCase(
    private val repository: ImageCompressRepository,
) {
    suspend operator fun invoke(uri: Uri, params: CompressParams): Result<CompressResult> =
        repository.compress(uri, params)
}
