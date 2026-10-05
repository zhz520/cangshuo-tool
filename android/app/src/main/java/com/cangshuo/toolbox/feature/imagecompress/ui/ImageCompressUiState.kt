package com.cangshuo.toolbox.feature.imagecompress.ui

import android.graphics.Bitmap
import android.net.Uri
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressParams
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressResult
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageMetadata
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageShareRequest

enum class PreviewTab {
    COMPRESSED,
    ORIGINAL,
}

data class ImageCompressUiState(
    val selectedUri: Uri? = null,
    val originalMetadata: ImageMetadata? = null,
    val originalPreviewBitmap: Bitmap? = null,
    val compressParams: CompressParams = CompressParams(),
    val isLoadingImage: Boolean = false,
    val isCompressing: Boolean = false,
    val isSaving: Boolean = false,
    val isSharing: Boolean = false,
    val result: CompressResult? = null,
    val previewTab: PreviewTab = PreviewTab.COMPRESSED,
    val userMessageRes: Int? = null,
    val isSuccessMessage: Boolean = false,
    val shareRequest: ImageShareRequest? = null,
)
