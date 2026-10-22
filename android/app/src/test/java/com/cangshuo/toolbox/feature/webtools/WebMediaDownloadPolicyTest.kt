package com.cangshuo.toolbox.feature.webtools

import com.cangshuo.toolbox.feature.webtools.domain.WebToolNavigationPolicy
import org.junit.Assert.*
import org.junit.Test

class WebMediaDownloadPolicyTest {
    private val origin = "https://tool.zhzgo.cn"
    private val id = "a".repeat(32)
    private val policy = WebToolNavigationPolicy(origin)

    @Test fun lanzouExternalLinksRequireAPublicHostAndSafeScheme() {
        assertTrue(policy.allowsLanzouExternal("https://www.lanzouf.com/iAbC123"))
        assertTrue(policy.allowsLanzouExternal("https://cdn.example.com/file/a.zip?fn=a.zip"))
        listOf("javascript:alert(1)", "file:///etc/passwd", "https://u:p@example.com/a", "https://127.0.0.1/a",
            "https://localhost/a", "https://example.com:8443/a", "https://example.com/a\r\nX-Test: 1",
            "https://example.com", "https://example.com/a#fragment").forEach { assertFalse(it, policy.allowsLanzouExternal(it)) }
    }

    @Test fun allowsOnlyOwnOpaqueAttachmentUrls() {
        assertTrue(policy.allowsMediaDownload("$origin/api/v1/web-tools/short_video_parse/assets/$id/0?download=true"))
        assertTrue(policy.allowsMediaDownload("$origin/api/v1/web-tools/short_video_parse/albums/$id?selection=1,3,5"))
        val root = "$origin/api/v1/web-tools/short_video_parse"
        listOf("$root/assets/$id/0", "$root/assets/$id/0?download=true&url=http://localhost",
            "$root/assets/$id/0?download=true#fragment", "$root/assets/$id/0?download=false",
            "$root/assets/$id/1000?download=true", "$root/assets/bad/0?download=true",
            "$root/albums/$id?selection=-1", "$root/albums/$id?selection=",
            "$root/albums/$id?selection=1%2C2", "$root/assets/$id/%30?download=true",
            "$origin/tools/short_video_parse/", "https://evil.example/api/v1/web-tools/short_video_parse/assets/$id/0?download=true",
            "http://tool.zhzgo.cn/api/v1/web-tools/short_video_parse/assets/$id/0?download=true",
            "https://tool.zhzgo.cn.evil.example/api/v1/web-tools/short_video_parse/assets/$id/0?download=true",
            "https://user@tool.zhzgo.cn/api/v1/web-tools/short_video_parse/assets/$id/0?download=true",
            "$root/albums/$id?selection=" + List(51) { "0" }.joinToString(",")
        ).forEach { assertFalse(it, policy.allowsMediaDownload(it)) }
    }
}
