package com.cangshuo.toolbox.feature.text.domain

class FindReplaceUseCase(private val repository: TextRepository) {
    suspend operator fun invoke(
        input: String, find: String, replace: String, matchCase: Boolean, useRegex: Boolean,
        expandGroups: Boolean = true, multiline: Boolean = false, dotAll: Boolean = false,
    ): Result<TextReplaceResult> = repository.findAndReplace(input, find, replace, matchCase, useRegex,
        expandGroups, multiline, dotAll)
}
