package com.cangshuo.toolbox.feature.text.domain

interface TextRepository {
    suspend fun computeStatistics(input: String): Result<TextStatistics>
    suspend fun transform(input: String, operation: TextOperation): Result<String>
    suspend fun findAndReplace(
        input: String, find: String, replace: String, matchCase: Boolean, useRegex: Boolean,
        expandGroups: Boolean = true, multiline: Boolean = false, dotAll: Boolean = false,
    ): Result<TextReplaceResult>
}
