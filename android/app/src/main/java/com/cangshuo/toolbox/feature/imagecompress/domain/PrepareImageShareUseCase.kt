package com.cangshuo.toolbox.feature.imagecompress.domain

import android.net.Uri

class PrepareImageShareUseCase(private val repository: ImageCompressRepository) {
    suspend operator fun invoke(result: CompressResult): Result<Uri> = repository.prepareShare(result)
}
