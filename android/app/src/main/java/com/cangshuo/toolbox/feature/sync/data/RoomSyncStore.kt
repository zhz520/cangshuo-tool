package com.cangshuo.toolbox.feature.sync.data

import androidx.room.withTransaction
import com.cangshuo.toolbox.core.database.*
import com.cangshuo.toolbox.feature.sync.domain.*
import java.util.UUID
import kotlinx.coroutines.flow.map

class RoomSyncStore(private val database: ToolboxDatabase) : SyncStore {
    private val dao=database.syncDao()
    override fun preference(user: Long) = dao.observePreference(user).map {
        it?.let { p -> SyncPreference(p.enabled,p.enrolled,p.cursor,p.recentEnabled,p.recentEnrolled,p.settingsEnabled,p.settingsEnrolled) } ?: SyncPreference()
    }
    override fun records(user: Long,type: String) = dao.observeRecords(user,type).map { list -> list.map { it.record() } }
    override suspend fun deviceId(): String {
        dao.device(SyncDeviceEntity(deviceId=UUID.randomUUID().toString().replace("-","")))
        return requireNotNull(dao.device())
    }
    override suspend fun configure(user: Long,enabled: Boolean,device: String,type: String) = database.withTransaction {
        val old=dao.preference(user) ?: SyncPreferenceEntity(user,false,false,"")
        require(type in setOf("FAVORITE","RECENT","SETTING"))
        if (type=="FAVORITE" && enabled && !old.enrolled) {
            // Explicit opt-in imports anonymous favorites once; account data remains separate.
            database.favoriteToolDao().snapshot().forEach {
                val record=SyncRecord("FAVORITE",it.toolCode,it.addedAt,device,false,dirty=true)
                val prior=dao.find(user,"FAVORITE",it.toolCode)?.record()
                if (prior==null || record.newerThan(prior)) dao.put(record.entity(user))
            }
        }
        if (type=="RECENT" && enabled && !old.recentEnrolled) {
            database.recentToolDao().snapshot().forEach {
                val record=SyncRecord("RECENT",it.toolCode,it.lastUsedAt,device,false,lastUsedAt=it.lastUsedAt,
                    useCount=it.useCount.coerceIn(1,2147483647).toLong(),dirty=true)
                val prior=dao.find(user,"RECENT",it.toolCode)?.record()
                if (prior==null || record.newerThan(prior)) dao.put(record.entity(user))
            }
        }
        if(type=="SETTING" && enabled && !old.settingsEnrolled) {
            val anonymous=dao.snapshot(0,"SETTING").map { it.record() }.associateBy { it.entityKey }
            SettingsPolicy.defaults.forEach { (key,value) ->
                val record=(anonymous[key] ?: SyncRecord("SETTING",key,946684800000L,device,false,value=value)).copy(deviceId=device,dirty=true)
                val prior=dao.find(user,"SETTING",key)?.record()
                if (prior==null || record.newerThan(prior)) dao.put(record.entity(user))
            }
        }
        val next=when(type) {
            "FAVORITE" -> old.copy(enabled=enabled,enrolled=old.enrolled || enabled)
            "RECENT" -> old.copy(recentEnabled=enabled,recentEnrolled=old.recentEnrolled || enabled)
            else -> old.copy(settingsEnabled=enabled,settingsEnrolled=old.settingsEnrolled || enabled)
        }
        // Re-enabling includes changes skipped while this type was disabled.
        dao.preference(if(enabled) next.copy(cursor="") else next)
    }
    override suspend fun find(user: Long,type: String,key: String) = dao.find(user,type,key)?.record()
    override suspend fun put(user: Long,record: SyncRecord) = dao.put(record.entity(user))
    override suspend fun putAll(user: Long,records: List<SyncRecord>) = database.withTransaction { records.forEach { dao.put(it.entity(user)) } }
    override suspend fun dirty(user: Long,types: Set<String>) = dao.dirty(user,types).map { it.record() }
    override suspend fun acknowledge(user: Long,records: List<SyncRecord>) = database.withTransaction {
        records.forEach { dao.acknowledge(user,it.entityType,it.entityKey,it.updatedAt,it.deviceId) }
    }
    override suspend fun mergePage(user: Long,page: SyncPage) = database.withTransaction {
        page.items.forEach { incoming ->
            val old=dao.find(user,incoming.entityType,incoming.entityKey)?.record()
            if (old == null || incoming.newerThan(old)) dao.put(incoming.copy(dirty=false).entity(user))
        }
        val old=dao.preference(user) ?: error("Missing sync preference")
        dao.preference(old.copy(cursor=page.nextCursor))
    }
    private fun SyncEntity.record()=SyncRecord(entityType,entityKey,updatedAt,deviceId,deleted,lastUsedAt,useCount,value,dirty)
    private fun SyncRecord.entity(user: Long)=SyncEntity(user,entityType,entityKey,updatedAt,deviceId,deleted,lastUsedAt,useCount,value,dirty)
}
