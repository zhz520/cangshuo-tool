package com.cangshuo.toolbox.feature.charconvert.domain

interface CharConvertRepository {
    fun transform(text: String, options: CharConvertOptions): String
}
