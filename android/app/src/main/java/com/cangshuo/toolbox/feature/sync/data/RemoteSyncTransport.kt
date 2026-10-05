package com.cangshuo.toolbox.feature.sync.data

import com.cangshuo.toolbox.feature.auth.data.*
import com.cangshuo.toolbox.feature.sync.domain.*
import java.util.Base64
import retrofit2.Response
import retrofit2.http.*

internal data class SyncPayloadDto(val lastUsedAt: Long?=null,val useCount: Long?=null,val value: String?=null)
internal data class SyncItemDto(val entityType: String,val entityKey: String,val updatedAt: Long,
    val deleted: Boolean,val payload: SyncPayloadDto) {
    override fun toString() = "SyncItemDto[redacted]"
}
internal data class SyncPushDto(val deviceId: String,val items: List<SyncItemDto>) {
    override fun toString() = "SyncPushDto[redacted]"
}
internal data class SyncEntryDto(val entityType: String,val entityKey: String,val updatedAt: Long,val deviceId: String,
    val deleted: Boolean,val payload: SyncPayloadDto,val revision: Long) {
    override fun toString() = "SyncEntryDto[redacted]"
}
internal data class SyncPullDto(val items: List<SyncEntryDto>,val nextCursor: String,val hasMore: Boolean)
internal data class SyncPushResultDto(val applied: Int,val nextCursor: String)
internal interface SyncApi {
    @POST("sync/push") suspend fun push(@Header("Authorization") bearer: String,@Body body: SyncPushDto): Response<AuthEnvelope<SyncPushResultDto>>
    @GET("sync/pull") suspend fun pull(@Header("Authorization") bearer: String,@Query("cursor") cursor: String?): Response<AuthEnvelope<SyncPullDto>>
}
class RemoteSyncTransport internal constructor(private val api: SyncApi,
    private val bearer: suspend (Long,Boolean)->String) : SyncTransport {
    constructor(baseUrl: String,bearer: suspend (Long,Boolean)->String) :
        this(createRemoteApi(baseUrl,SyncApi::class.java,262_144),bearer)
    override suspend fun push(user: Long,device: String,records: List<SyncRecord>) {
        val body=SyncPushDto(device,records.map { SyncItemDto(it.entityType,it.entityKey,it.updatedAt,it.deleted,
            SyncPayloadDto(it.lastUsedAt,it.useCount,it.value)) })
        val result=request(user) { api.push(it,body) }
        require(result.applied in 0..records.size)
        cursor(user,result.nextCursor)
    }
    override suspend fun pull(user: Long,cursor: String): SyncPage {
        val result=request(user) { api.pull(it,cursor.takeIf(String::isNotEmpty)) }
        val after=if (cursor.isEmpty()) 0 else cursor(user,cursor)
        val next=cursor(user,result.nextCursor)
        require(result.items.size<=100 && next>=after && (!result.hasMore || (result.items.isNotEmpty() && next>after)))
        var revision=after
        val keys=HashSet<String>()
        val records=result.items.map {
            require(it.revision>revision && it.revision<=next && it.updatedAt>=946684800000L &&
                it.deviceId.matches(Regex("[0-9a-f]{32}")) &&
                it.entityKey.matches(Regex("[a-z][a-z0-9_]{0,63}")) && keys.add(it.entityType+":"+it.entityKey))
            require(when(it.entityType) {
                "FAVORITE" -> it.payload.lastUsedAt==null && it.payload.useCount==null && it.payload.value==null
                "RECENT" -> it.payload.value==null && if(it.deleted) it.payload.lastUsedAt==null && it.payload.useCount==null
                    else (it.payload.lastUsedAt ?: 0)>=946684800000L && (it.payload.useCount ?: 0) in 1..2147483647L
                "SETTING" -> it.payload.lastUsedAt==null && it.payload.useCount==null &&
                    it.entityKey in SettingsPolicy.options && if(it.deleted) it.payload.value==null
                    else it.payload.value?.let { value -> SettingsPolicy.valid(it.entityKey,value) }==true
                else -> false
            })
            revision=it.revision
            SyncRecord(it.entityType,it.entityKey,it.updatedAt,it.deviceId,it.deleted,it.payload.lastUsedAt,it.payload.useCount,it.payload.value)
        }
        return SyncPage(records,result.nextCursor,result.hasMore)
    }
    private fun cursor(user: Long,value: String): Long {
        require(value.length in 1..128 && value.matches(Regex("[A-Za-z0-9_-]+")))
        val decoded=String(Base64.getUrlDecoder().decode(value),Charsets.US_ASCII)
        require(decoded.matches(Regex("$user:(0|[1-9][0-9]*)")))
        return decoded.substringAfter(':').toLong()
    }
    private suspend fun <T> request(user: Long,call: suspend (String)->Response<AuthEnvelope<T>>): T {
        var response=call(bearer(user,false))
        if (response.code()==401) { response.errorBody()?.close(); response=call(bearer(user,true)) }
        response.errorBody()?.close()
        check(response.isSuccessful)
        val envelope=response.body() ?: error("Missing sync response")
        check(envelope.code==0)
        return envelope.data ?: error("Missing sync data")
    }
}
