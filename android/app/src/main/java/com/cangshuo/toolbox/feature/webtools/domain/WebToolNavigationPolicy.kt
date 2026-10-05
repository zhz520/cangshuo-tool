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

    private fun port(uri: URI): Int = if (uri.port != -1) uri.port else if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
}
