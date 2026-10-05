package com.cangshuo.toolbox.feature.sync.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncRecord(val entityType: String, val entityKey: String, val updatedAt: Long, val deviceId: String,
    val deleted: Boolean, val lastUsedAt: Long? = null, val useCount: Long? = null, val value: String? = null,
    val dirty: Boolean = false) {
    override fun toString() = "SyncRecord[redacted]"
}
data class SyncPreference(val enabled: Boolean = false, val enrolled: Boolean = false, val cursor: String = "",
    val recentEnabled: Boolean = false, val recentEnrolled: Boolean = false,
    val settingsEnabled: Boolean = false, val settingsEnrolled: Boolean = false) {
    val types get() = buildSet { if(enabled) add("FAVORITE"); if(recentEnabled) add("RECENT"); if(settingsEnabled) add("SETTING") }
}
data class SyncContext(val userId: Long? = null, val preference: SyncPreference = SyncPreference()) {
    val localScope get() = userId?.takeIf { preference.enrolled }
    val recentScope get() = userId?.takeIf { preference.recentEnrolled }
    val settingsScope get() = userId?.takeIf { preference.settingsEnrolled }
}
enum class SyncStatus { IDLE, SYNCING, SUCCESS, ERROR }
data class SyncProgress(val userId: Long? = null, val status: SyncStatus = SyncStatus.IDLE)
data class SyncPage(val items: List<SyncRecord>, val nextCursor: String, val hasMore: Boolean)

interface SyncStore {
    suspend fun deleteAccount(user: Long)
    fun preference(user: Long): Flow<SyncPreference>
    fun records(user: Long, type: String): Flow<List<SyncRecord>>
    suspend fun configure(user: Long, enabled: Boolean, device: String, type: String = "FAVORITE")
    suspend fun deviceId(): String
    suspend fun find(user: Long, type: String, key: String): SyncRecord?
    suspend fun put(user: Long, record: SyncRecord)
    suspend fun putAll(user: Long, records: List<SyncRecord>) { records.forEach { put(user,it) } }
    suspend fun dirty(user: Long, types: Set<String> = setOf("FAVORITE","RECENT","SETTING")): List<SyncRecord>
    suspend fun acknowledge(user: Long, records: List<SyncRecord>)
    suspend fun mergePage(user: Long, page: SyncPage)
}
interface SyncTransport {
    suspend fun push(user: Long, device: String, records: List<SyncRecord>)
    suspend fun pull(user: Long, cursor: String): SyncPage
}

class CloudSyncRepository(private val users: StateFlow<Long?>, private val store: SyncStore,
    private val transport: SyncTransport, private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val context = users.flatMapLatest { user ->
        if (user == null) flowOf(SyncContext()) else store.preference(user).map { SyncContext(user,it) }
    }.stateIn(scope, SharingStarted.Eagerly, SyncContext())
    private val mutableProgress = MutableStateFlow(SyncProgress())
    @OptIn(ExperimentalCoroutinesApi::class)
    val settings = context.flatMapLatest { c -> store.records(c.settingsScope ?: 0,"SETTING")
        .map { SettingsPolicy.read(it).copy(ownerScope=c.settingsScope ?: 0) } }
        .stateIn(scope,SharingStarted.Eagerly,AppSettings())
    val progress = mutableProgress.asStateFlow()
    private val network = Mutex()
    private val writes = Mutex()
    private var debounce: Job? = null

    init {
        scope.launch {
            context.map { it.userId to it.preference.types }.distinctUntilChanged().collectLatest { (user,types) ->
                if (user != null && types.isNotEmpty()) while (isActive) { sync(); delay(60_000) }
            }
        }
    }
    fun records(user: Long, type: String) = store.records(user,type)
    suspend fun deleteAccount(user: Long, remoteDeletion: suspend () -> Unit) = network.withLock {
        check(users.value == user)
        writes.withLock {
            check(users.value == user)
            debounce?.cancel(); debounce=null
            remoteDeletion()
            withContext(NonCancellable) { store.deleteAccount(user) }
        }
    }
    suspend fun configure(enabled: Boolean,type: String = "FAVORITE") {
        val user = users.value ?: error("Authentication required")
        network.withLock {
            check(users.value==user)
            writes.withLock { store.configure(user,enabled,store.deviceId(),type) }
        }
        if (enabled) request()
    }
    suspend fun setFavorite(code: String, selected: Boolean): Boolean = writes.withLock {
        val user=context.value.localScope ?: return@withLock false
        if (users.value != user) return@withLock false
        val old=store.find(user,"FAVORITE",code)
        val at=maxOf(clock(),(old?.updatedAt ?: 0)+1)
        store.put(user,SyncRecord("FAVORITE",code,at,store.deviceId(),!selected,dirty=true))
        request(); true
    }
    suspend fun recordUsage(code: String,at: Long): Boolean = writes.withLock {
        val user=context.value.recentScope ?: return@withLock false
        if (users.value != user) return@withLock false
        val old=store.find(user,"RECENT",code)
        store.put(user,SyncRecord("RECENT",code,maxOf(clock(),(old?.updatedAt ?: 0)+1),store.deviceId(),false,
            lastUsedAt=maxOf(at,old?.lastUsedAt ?: 0),useCount=((old?.useCount ?: 0)+1).coerceAtMost(2147483647),dirty=true))
        request(); true
    }
    suspend fun removeRecent(code: String): Boolean = writes.withLock {
        val user=context.value.recentScope ?: return@withLock false
        if (users.value != user) return@withLock false
        val old=store.find(user,"RECENT",code)
        store.put(user,SyncRecord("RECENT",code,maxOf(clock(),(old?.updatedAt ?: 0)+1),store.deviceId(),true,dirty=true))
        request(); true
    }
    suspend fun clearRecent(): Boolean = writes.withLock {
        val user=context.value.recentScope ?: return@withLock false
        if (users.value != user) return@withLock false
        val device=store.deviceId()
        val entries=store.records(user,"RECENT").first().filterNot { it.deleted }
        store.putAll(user,entries.map { SyncRecord("RECENT",it.entityKey,maxOf(clock(),it.updatedAt+1),device,true,dirty=true) })
        request(); true
    }
    suspend fun setSetting(key: String,value: String) = writes.withLock {
        require(SettingsPolicy.valid(key,value))
        val user=context.value.settingsScope ?: 0
        if (user != 0L) check(users.value==user)
        val old=store.find(user,"SETTING",key)
        store.put(user,SyncRecord("SETTING",key,maxOf(clock(),(old?.updatedAt ?: 0)+1),store.deviceId(),false,value=value,dirty=user!=0L))
        if(user!=0L) request()
    }
    private fun request() {
        debounce?.cancel()
        debounce=scope.launch { delay(500); sync() }
    }
    suspend fun sync() = network.withLock {
        val user=context.value.userId ?: return@withLock
        fun allowed() = users.value == user && context.value.userId == user && context.value.preference.types.isNotEmpty()
        if (!allowed()) return@withLock
        mutableProgress.value=SyncProgress(user,SyncStatus.SYNCING)
        try {
            // A bounded run. Pending rows remain durable for the next foreground retry.
            repeat(100) {
                if (!allowed()) return@withLock
                val types=context.value.preference.types
                val batch=store.dirty(user,types).take(100)
                if (batch.isNotEmpty()) {
                    transport.push(user,store.deviceId(),batch)
                    store.acknowledge(user,batch)
                }
                var cursor=store.preference(user).first().cursor
                var pages=0
                do {
                    if (!allowed()) return@withLock
                    val page=transport.pull(user,cursor)
                    if (page.hasMore && page.nextCursor == cursor) error("Invalid sync cursor")
                    writes.withLock {
                        store.mergePage(user,page.copy(items=page.items.filter { it.entityType in context.value.preference.types }))
                    }
                    cursor=page.nextCursor
                    if (++pages > 100) error("Sync page limit")
                } while(page.hasMore)
                if (store.dirty(user,context.value.preference.types).isEmpty()) {
                    mutableProgress.value=SyncProgress(user,SyncStatus.SUCCESS)
                    return@withLock
                }
            }
            error("Sync batch limit")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableProgress.value=SyncProgress(user,SyncStatus.ERROR) }
    }
}

fun SyncRecord.newerThan(other: SyncRecord) = updatedAt > other.updatedAt ||
    (updatedAt == other.updatedAt && deviceId > other.deviceId)

class CloudSyncUseCases(private val repository: CloudSyncRepository) {
    val context get() = repository.context
    val progress get() = repository.progress
    val settings get() = repository.settings
    suspend fun configure(enabled: Boolean,type: String = "FAVORITE") = repository.configure(enabled,type)
    suspend fun sync() = repository.sync()
    suspend fun setSetting(key: String,value: String) = repository.setSetting(key,value)
}
