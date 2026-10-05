package com.cangshuo.toolbox.feature.sync.domain

data class AppSettings(val theme: String="SYSTEM",val language: String="SYSTEM",val gridColumns: Int=0,
    val startupPage: String="HOME",val ready: Boolean=false,val ownerScope: Long=0)
object SettingsPolicy {
    val options=mapOf("theme" to setOf("SYSTEM","LIGHT","DARK"),"language" to setOf("SYSTEM","zh-CN","en"),
        "grid_columns" to setOf("AUTO","1","2","3"),"startup_page" to setOf("HOME","TOOLS","FAVORITES","PROFILE"))
    val defaults=mapOf("theme" to "SYSTEM","language" to "SYSTEM","grid_columns" to "AUTO","startup_page" to "HOME")
    fun valid(key: String,value: String)=value in options[key].orEmpty()
    fun read(records: List<SyncRecord>): AppSettings {
        val values=defaults+records.filter { !it.deleted && it.value != null && valid(it.entityKey,it.value) }.associate { it.entityKey to requireNotNull(it.value) }
        return AppSettings(values.getValue("theme"),values.getValue("language"),values.getValue("grid_columns").toIntOrNull() ?: 0,
            values.getValue("startup_page"),true)
    }
}
