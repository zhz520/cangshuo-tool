package com.cangshuo.toolbox.feature.urlextract.data

import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractLimits
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractOptions
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractRepository
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractResult
import com.cangshuo.toolbox.feature.urlextract.domain.UrlOutputFormat
import java.util.Locale

/**
 * Local link extraction. Scheme links are matched anywhere; fuzzy mode additionally accepts
 * `www.` hosts and bare domains limited to a curated suffix list, always skipping candidates
 * that sit inside an email address. Trailing punctuation and unbalanced closing brackets are
 * trimmed with an independent scanner (compare markdown-it/linkify-it's separator and
 * paired-bracket handling, MIT).
 */
class LocalUrlExtractRepository : UrlExtractRepository {

    override fun extract(text: String, options: UrlExtractOptions): UrlExtractResult {
        val pattern = if (options.includeBareDomains) FUZZY_PATTERN else SCHEME_PATTERN
        val seen = HashSet<String>()
        val links = ArrayList<String>()
        var truncated = false
        for (match in pattern.findAll(text)) {
            val candidate = trimTrailing(match.value)
            if (!isAcceptable(candidate)) continue
            val key = candidate.lowercase(Locale.ROOT)
            if (options.dedupe && !seen.add(key)) continue
            if (links.size >= UrlExtractLimits.MAX_RESULTS) {
                truncated = true
                break
            }
            seen.add(key)
            links += candidate
        }
        return UrlExtractResult(links, truncated)
    }

    override fun format(links: List<String>, format: UrlOutputFormat): String = when (format) {
        UrlOutputFormat.LINES -> links.joinToString("\n")
        UrlOutputFormat.MARKDOWN -> links.joinToString("\n") { "[$it]($it)" }
    }

    private fun trimTrailing(raw: String): String {
        var value = raw
        while (value.isNotEmpty()) {
            val last = value.last()
            if (last in TRAILING_PUNCTUATION) {
                value = value.dropLast(1)
                continue
            }
            val opener = CLOSING_TO_OPENING[last] ?: break
            if (value.count { it == opener } < value.count { it == last }) {
                value = value.dropLast(1)
                continue
            }
            break
        }
        return value
    }

    private fun isAcceptable(value: String): Boolean {
        if (value.length < 4) return false
        val hostPart = value.substringAfter("://", value)
        val host = hostPart.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty()) return false
        val hostWithoutPort = host.substringBefore(':')
        return hostWithoutPort.contains('.') || hostWithoutPort == "localhost" || hostWithoutPort.startsWith("[")
    }

    private companion object {
        const val SCHEME = """(?i)\b(?:https?://|ftp://|www\.)[^\s<>"'“”‘’（）【】《》，。；：！？、]+"""
        const val DOMAIN = """(?i)(?<![@\w.:/-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:com|cn|net|org|io|dev|app|ai|cc|tv|me|info|biz|top|xyz|site|online|shop|club|vip|wang|edu|gov|co|uk|us|jp|kr|de|fr|ru|hk|tw|sg|au|ca|in|eu|tech|store|fun|live|pro|work|space|link|art|blog|wiki|news|media|design|cloud|host|one|run|sh|ly|to)"""
        const val TRAILING_PUNCTUATION = ".,;:!?、。，；：！？…·\"'”’"
        val CLOSING_TO_OPENING = mapOf(')' to '(', ']' to '[', '}' to '{', '）' to '（', '】' to '【', '》' to '《', '」' to '「')
        val SCHEME_PATTERN = Regex(SCHEME)
        val FUZZY_PATTERN = Regex("(?:$SCHEME)|(?:$DOMAIN)")
    }
}
