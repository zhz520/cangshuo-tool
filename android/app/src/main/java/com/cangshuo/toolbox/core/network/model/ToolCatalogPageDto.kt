package com.cangshuo.toolbox.core.network.model

/** The data object inside the existing API response envelope. */
data class ToolCatalogPageDto(
    val records: List<ToolCatalogDto>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
) {
    init {
        require(page >= 1) { "Invalid catalog page" }
        require(pageSize in 1..100) { "Invalid catalog page size" }
        require(total >= 0 && records.size <= pageSize) { "Invalid catalog page data" }
    }
}
