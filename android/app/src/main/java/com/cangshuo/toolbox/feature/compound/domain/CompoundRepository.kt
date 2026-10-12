package com.cangshuo.toolbox.feature.compound.domain

interface CompoundRepository {
    /**
     * Iterates period by period: the running balance earns the periodic rate,
     * then the contribution is added (ordinary annuity, end of period).
     */
    fun calculate(input: CompoundInput): CompoundOutcome
}
