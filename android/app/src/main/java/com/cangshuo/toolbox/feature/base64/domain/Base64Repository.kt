package com.cangshuo.toolbox.feature.base64.domain

interface Base64Repository {
    fun encode(text: String, isUrlSafe: Boolean): Base64Result
    fun decode(text: String, isUrlSafe: Boolean): Base64Result
}
