package com.cangshuo.toolbox.feature.home.domain

data class HomeRecommendation(val slotCode: String, val title: String, val subtitle: String?, val toolCode: String?, val linkUrl: String?)
data class HomeAnnouncement(val id: Long, val title: String, val body: String, val level: String)
interface HomeExtrasRepository {
    suspend fun recommendations(): List<HomeRecommendation>
    suspend fun announcements(): List<HomeAnnouncement>
}
class HomeExtrasUseCases(private val repository: HomeExtrasRepository) {
    suspend fun recommendations() = repository.recommendations()
    suspend fun announcements() = repository.announcements()
}
