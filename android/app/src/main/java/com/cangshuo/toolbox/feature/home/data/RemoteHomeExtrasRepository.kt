package com.cangshuo.toolbox.feature.home.data

import com.cangshuo.toolbox.feature.auth.data.AuthEnvelope
import com.cangshuo.toolbox.feature.auth.data.createRemoteApi
import com.cangshuo.toolbox.feature.home.domain.*
import java.net.URI
import retrofit2.Response
import retrofit2.http.GET

internal data class RecommendationDto(val slotCode: String, val title: String, val subtitle: String?, val toolCode: String?, val linkUrl: String?, val imageUrl: String?)
internal data class AnnouncementDto(val id: Long, val title: String, val body: String, val level: String)
internal interface HomeExtrasApi {
    @GET("home/recommendations") suspend fun recommendations(): Response<AuthEnvelope<List<RecommendationDto>>>
    @GET("home/announcements") suspend fun announcements(): Response<AuthEnvelope<List<AnnouncementDto>>>
}
class RemoteHomeExtrasRepository internal constructor(private val api: HomeExtrasApi) : HomeExtrasRepository {
    constructor(baseUrl: String) : this(createRemoteApi(baseUrl, HomeExtrasApi::class.java, 65_536))
    override suspend fun recommendations(): List<HomeRecommendation> {
        val values = data(api.recommendations())
        require(values.size <= 20)
        val codes = HashSet<String>()
        return values.map {
            require(it.slotCode.matches(Regex("[a-z][a-z0-9_]{0,31}")) && codes.add(it.slotCode))
            text(it.title, 64); it.subtitle?.let { subtitle -> text(subtitle, 128, empty = true) }
            require((it.toolCode != null) != (it.linkUrl != null))
            it.toolCode?.let { code -> require(code.matches(Regex("[a-z][a-z0-9_]{0,63}"))) }
            it.linkUrl?.let(::https); it.imageUrl?.let(::https)
            HomeRecommendation(it.slotCode, it.title, it.subtitle, it.toolCode, it.linkUrl)
        }
    }
    override suspend fun announcements(): List<HomeAnnouncement> {
        val values = data(api.announcements())
        require(values.size <= 5)
        val ids = HashSet<Long>()
        return values.map {
            require(it.id > 0 && ids.add(it.id) && it.level in setOf("INFO", "WARNING", "CRITICAL"))
            text(it.title, 128); text(it.body, 2000, multiline = true)
            HomeAnnouncement(it.id, it.title, it.body, it.level)
        }
    }
    private fun <T> data(response: Response<AuthEnvelope<T>>): T {
        response.errorBody()?.close()
        check(response.isSuccessful)
        val envelope = response.body() ?: error("Missing home response")
        check(envelope.code == 0 && envelope.traceId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        return envelope.data ?: error("Missing home data")
    }
    private fun text(value: String, max: Int, empty: Boolean = false, multiline: Boolean = false) {
        require(value.length <= max && (empty || value.isNotBlank()) && value.none { it.isISOControl() && !(multiline && it == '\n') })
    }
    private fun https(value: String) {
        require(value.length <= 500)
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
    }
}
