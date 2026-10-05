package com.cangshuo.toolbox.feature.base64.domain

class EncodeBase64UseCase(private val repository: Base64Repository) {
    operator fun invoke(text: String, isUrlSafe: Boolean): Base64Result {
        return repository.encode(text, isUrlSafe)
    }
}
