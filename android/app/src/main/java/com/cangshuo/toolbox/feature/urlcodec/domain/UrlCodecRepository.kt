package com.cangshuo.toolbox.feature.urlcodec.domain

interface UrlCodecRepository {
    suspend fun encode(text: String, type: UrlEncodeType): UrlCodecResult
    suspend fun decode(text: String, type: UrlEncodeType): UrlCodecResult
}
