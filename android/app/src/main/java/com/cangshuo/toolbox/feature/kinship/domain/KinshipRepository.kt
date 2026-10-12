package com.cangshuo.toolbox.feature.kinship.domain

interface KinshipRepository {
    /** Runs one query against the local data tables. */
    fun query(query: KinshipQuery): List<String>
}
