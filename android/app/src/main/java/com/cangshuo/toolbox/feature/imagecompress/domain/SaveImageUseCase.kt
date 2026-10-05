package com.cangshuo.toolbox.feature.imagecompress.domain

import android.net.Uri

class SaveImageUseCase(
    private val repository: ImageCompressRepository,
) {
    suspend operator fun invoke(result: CompressResult, destination: Uri? = null): Result<Uri> =
        repository.save(result, destination)
}
