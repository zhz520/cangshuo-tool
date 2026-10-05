package com.cangshuo.toolbox.feature.json.domain

interface JsonRepository {
    suspend fun format(input: String, indent: JsonIndent): JsonProcessResult
    suspend fun compress(input: String): JsonProcessResult
    suspend fun escape(input: String): JsonProcessResult
    suspend fun unescape(input: String, stringInput: JsonStringInput): JsonProcessResult
}
