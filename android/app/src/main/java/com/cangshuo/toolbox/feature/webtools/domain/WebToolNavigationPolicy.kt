package com.cangshuo.toolbox.feature.webtools.domain

import java.net.URI

/** Match the configured origin, including protocol/port; never accept suffix lookalikes. */
class WebToolNavigationPolicy(baseUrl: String) {
    private val origin = URI(baseUrl)

    fun allows(url: String): Boolean = try {
        val target = URI(url)
        target.scheme?.lowercase(java.util.Locale.ROOT) in setOf("http", "https") && target.userInfo == null &&
            target.scheme.equals(origin.scheme, ignoreCase = true) &&
            target.host != null && target.host.equals(origin.host, ignoreCase = true) &&
            port(target) == port(origin)
    } catch (_: Exception) {
        false
    }

    /** Only this tool's expiring opaque media handles may be handed to a browser for saving. */
    fun allowsMediaDownload(url: String): Boolean = try {
        val target = URI(url)
        val base = "/api/v1/web-tools/short_video_parse/"
        allows(url) && target.rawFragment == null && (
            target.rawPath.matches(Regex(base + "assets/[a-f0-9]{32}/[0-9]{1,3}")) && target.rawQuery == "download=true" ||
            target.rawPath.matches(Regex(base + "albums/[a-f0-9]{32}")) &&
                target.rawQuery?.matches(Regex("selection=[0-9]{1,3}(?:,[0-9]{1,3}){0,49}")) == true
        )
    } catch (_: Exception) {
        false
    }

    private fun port(uri: URI): Int = if (uri.port != -1) uri.port else if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
}
