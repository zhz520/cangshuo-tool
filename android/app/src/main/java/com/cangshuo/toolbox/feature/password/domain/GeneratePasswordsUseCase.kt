package com.cangshuo.toolbox.feature.password.domain

class GeneratePasswordsUseCase(private val repository: PasswordRepository) {
    operator fun invoke(options: PasswordOptions, count: Int): PasswordOutcome =
        repository.generate(options, count)
}
