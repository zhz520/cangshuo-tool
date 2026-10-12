package com.cangshuo.toolbox.feature.urlextract

import com.cangshuo.toolbox.feature.urlextract.data.LocalUrlExtractRepository
import com.cangshuo.toolbox.feature.urlextract.domain.ExtractUrlsUseCase
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractLimits
import com.cangshuo.toolbox.feature.urlextract.domain.UrlExtractOptions
import com.cangshuo.toolbox.feature.urlextract.domain.UrlOutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlExtractTest {
    private val extract = ExtractUrlsUseCase(LocalUrlExtractRepository())
    private val strict = UrlExtractOptions(includeBareDomains = false, dedupe = true)
    private val fuzzy = UrlExtractOptions(includeBareDomains = true, dedupe = true)

    @Test
    fun strictModeKeepsCompleteLinksAndTrimsPunctuation() {
        val result = extract.extract("看 https://example.com/a?b=1。还有 http://foo.cn/x 和 ftp://files.example.com/z", strict)
        assertEquals(listOf("https://example.com/a?b=1", "http://foo.cn/x", "ftp://files.example.com/z"), result.links)
    }

    @Test
    fun trailingPunctuationAndBracketsAreBalanced() {
        assertEquals(listOf("https://a.com/b"), extract.extract("https://a.com/b).", strict).links)
        assertEquals(listOf("https://a.com/(x)"), extract.extract("https://a.com/(x)", strict).links)
        assertEquals(listOf("https://b.com/y"), extract.extract("（https://b.com/y）", strict).links)
    }

    @Test
    fun fuzzyModeAddsDomainsButSkipsEmailsVersionsAndHostlessSchemes() {
        val text = "访问 www.example.com 或 example.org；邮箱 user@example.com 不算；版本 1.2.3 不匹配；http://x 也不算 http://"
        val result = extract.extract(text, fuzzy)
        assertEquals(listOf("www.example.com", "example.org"), result.links)
        assertEquals(emptyList<String>(), extract.extract("user@example.com 1.2.3", fuzzy).links)
    }

    @Test
    fun deduplicationIgnoresCaseAndPreservesOrder() {
        val text = "https://A.com/x https://a.com/x https://b.com"
        assertEquals(listOf("https://A.com/x", "https://b.com"), extract.extract(text, strict).links)
        val keepAll = extract.extract(text, UrlExtractOptions(includeBareDomains = false, dedupe = false))
        assertEquals(3, keepAll.links.size)
    }

    @Test
    fun markdownAndLinesFormatsAreExact() {
        val links = listOf("https://a.com", "https://b.com")
        assertEquals("https://a.com\nhttps://b.com", extract.format(links, UrlOutputFormat.LINES))
        assertEquals("[https://a.com](https://a.com)\n[https://b.com](https://b.com)", extract.format(links, UrlOutputFormat.MARKDOWN))
    }

    @Test
    fun resultsAreCappedAndFlagged() {
        val text = (1..1200).joinToString(" ") { "https://h$it.example.com/p" }
        val result = extract.extract(text, strict)
        assertTrue(result.truncated)
        assertEquals(UrlExtractLimits.MAX_RESULTS, result.links.size)
        assertFalse(extract.extract("https://h1.example.com", strict).truncated)
    }
}
