package com.cangshuo.toolbox.feature.urlcodec.ui

import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecMode
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlCodecResult
import com.cangshuo.toolbox.feature.urlcodec.domain.UrlEncodeType

data class UrlCodecUiState(
    val mode: UrlCodecMode = UrlCodecMode.ENCODE,
    val encodeType: UrlEncodeType = UrlEncodeType.COMPONENT,
    val inputText: String = "",
    val outputText: String = "",
    val error: UrlCodecResult.Error? = null,
    val isProcessing: Boolean = false,
    val hasResult: Boolean = false,
    val cancelled: Boolean = false,
    val inputRejected: Boolean = false,
)
