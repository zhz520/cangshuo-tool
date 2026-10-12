package com.cangshuo.toolbox.feature.mortgage.domain

interface MortgageRepository {
    /** Builds a full amortization schedule; displayed money values stay estimates. */
    fun calculate(input: MortgageInput): MortgageOutcome
}
