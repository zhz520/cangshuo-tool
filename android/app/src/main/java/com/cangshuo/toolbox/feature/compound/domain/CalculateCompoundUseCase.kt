package com.cangshuo.toolbox.feature.compound.domain

class CalculateCompoundUseCase(private val repository: CompoundRepository) {
    operator fun invoke(input: CompoundInput): CompoundOutcome = repository.calculate(input)
}
