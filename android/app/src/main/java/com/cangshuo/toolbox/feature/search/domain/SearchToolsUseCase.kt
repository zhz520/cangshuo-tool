package com.cangshuo.toolbox.feature.search.domain

import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolStatus
import java.text.Normalizer
import java.util.Locale

const val MAX_SEARCH_QUERY_LENGTH = 80
private val whitespace = Regex("[\\s\\p{Z}]+")

/** Literal local matching; query text is never interpreted as a regex or executable code. */
class SearchToolsUseCase(private val repository: ToolSearchRepository) {
    operator fun invoke(
        query: String,
        category: ToolCategory? = null,
        favoriteCodes: Set<String> = emptySet(),
    ): List<ToolMetadata> {
        require(query.length <= MAX_SEARCH_QUERY_LENGTH) { "Search query is too long" }
        val normalizedQuery = normalize(query)
        val terms = normalizedQuery.split(' ').filter { it.isNotEmpty() }.distinct()
        return repository.getDocuments().mapNotNull { document ->
            val tool = document.tool
            if (tool.status != ToolStatus.ENABLED || (category != null && tool.category != category)) {
                return@mapNotNull null
            }
            val rank = if (terms.isEmpty()) 0 else rank(document, normalizedQuery, terms) ?: return@mapNotNull null
            RankedTool(tool, rank)
        }.sortedWith(
            compareBy<RankedTool> { it.rank }.thenByDescending { it.tool.code in favoriteCodes }
                .thenBy { it.tool.sortOrder }.thenBy { it.tool.code },
        )
            .map { it.tool }
    }

    private fun rank(document: ToolSearchDocument, query: String, terms: List<String>): Int? {
        val tool = document.tool
        val name = normalize(tool.name)
        if (name == query) return 0
        if (name.startsWith(query)) return 1
        if (name.contains(query)) return 2
        val fields = listOf(name to 2, normalize(tool.code) to 3, normalize(tool.description) to 4) +
            tool.keywords.map { normalize(it) to 3 } +
            (document.categoryNames + tool.categoryCode).map { normalize(it) to 5 }
        // Every term must match; the weakest term determines the relevance tier.
        return terms.map { term ->
            fields.filter { (text, _) -> text.contains(term) }.minOfOrNull { it.second } ?: return null
        }.maxOrNull()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace(whitespace, " ").trim()

    private data class RankedTool(val tool: ToolMetadata, val rank: Int)
}
