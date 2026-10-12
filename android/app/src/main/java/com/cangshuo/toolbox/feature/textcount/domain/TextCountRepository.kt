package com.cangshuo.toolbox.feature.textcount.domain

interface TextCountRepository {
    fun analyze(text: String): TextStats
}
