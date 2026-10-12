package com.cangshuo.toolbox.feature.urlextract.domain

interface UrlExtractRepository {
    fun extract(text: String, options: UrlExtractOptions): UrlExtractResult

    fun format(links: List<String>, format: UrlOutputFormat): String
}
