package com.cangshuo.toolbox.feature.json.ui

import com.cangshuo.toolbox.feature.json.domain.JsonIndent
import com.cangshuo.toolbox.feature.json.domain.JsonProcessResult
import com.cangshuo.toolbox.feature.json.domain.JsonStringInput

enum class JsonMode { FORMAT, COMPRESS, ESCAPE, UNESCAPE }

data class JsonUiState(
    val mode: JsonMode = JsonMode.FORMAT,
    val indent: JsonIndent = JsonIndent.TWO_SPACES,
    val stringInput: JsonStringInput = JsonStringInput.QUOTED_LITERAL,
    val inputText: String = "",
    val outputText: String = "",
    val inputByteCount: Int? = 0,
    val error: JsonProcessResult.Error? = null,
    val outputLineCount: Int = 0,
    val outputByteCount: Int = 0,
    val duplicateKeyCount: Int = 0,
    val inputRejected: Boolean = false,
    val isProcessing: Boolean = false,
    val hasResult: Boolean = false,
    val cancelled: Boolean = false,
)
