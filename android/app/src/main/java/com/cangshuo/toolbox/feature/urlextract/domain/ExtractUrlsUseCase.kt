package com.cangshuo.toolbox.feature.urlextract.domain

class ExtractUrlsUseCase(private val repository: UrlExtractRepository) {
    fun extract(text: String, options: UrlExtractOptions): UrlExtractResult =
        repository.extract(text, options)

    fun format(links: List<String>, format: UrlOutputFormat): String =
        repository.format(links, format)
}
