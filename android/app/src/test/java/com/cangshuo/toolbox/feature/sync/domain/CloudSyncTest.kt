package com.cangshuo.toolbox.feature.sync.domain

import java.io.IOException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudSyncTest {
    @Test fun defaultOffAndOfflineChangesSurviveUntilRetry() = runTest {
        val users=MutableStateFlow<Long?>(1); val store=MemoryStore(); val remote=Remote()
        val repo=CloudSyncRepository(users,store,remote,backgroundScope,{1000})
        runCurrent(); repo.sync(); assertEquals(0,remote.pushes)
        repo.configure(true); runCurrent(); remote.offline=true
        repo.setFavorite("calculator",true); repo.sync()
        assertEquals(SyncStatus.ERROR,repo.progress.value.status)
        assertEquals(1,store.dirty(1).size)
        remote.offline=false; repo.sync()
        assertTrue(store.dirty(1).isEmpty()); assertEquals(SyncStatus.SUCCESS,repo.progress.value.status)
        assertFalse(remote.data[1]!!.getValue("FAVORITE:calculator").deleted)
    }
    @Test fun logoutAndAccountSwitchDoNotUploadAnotherAccountsQueue() = runTest {
        val users=MutableStateFlow<Long?>(1); val store=MemoryStore(); val remote=Remote()
        val repo=CloudSyncRepository(users,store,remote,backgroundScope,{1000})
        runCurrent(); repo.configure(true); runCurrent()
        remote.offline=true; repo.setFavorite("calculator",true)
        users.value=null; runCurrent(); assertNull(repo.context.value.localScope)
        users.value=2; runCurrent(); repo.configure(true); runCurrent()
        remote.offline=false; repo.setFavorite("uuid",true); repo.sync()
        assertFalse(remote.data[2].orEmpty().containsKey("FAVORITE:calculator"))
        assertEquals(1,store.dirty(1).size)
        users.value=1; runCurrent(); repo.sync(); assertTrue(store.dirty(1).isEmpty())
    }
    @Test fun acknowledgedSnapshotDoesNotEraseConcurrentLocalRemoval() = runTest {
        val store=MemoryStore()
        val add=SyncRecord("FAVORITE","calculator",1000,"a",false,dirty=true)
        store.put(1,add); store.put(1,add.copy(updatedAt=1001,deleted=true))
        store.acknowledge(1,listOf(add))
        assertTrue(store.dirty(1).single().deleted)
        store.mergePage(1,SyncPage(listOf(add.copy(dirty=false)),"1",false))
        assertTrue(store.find(1,"FAVORITE","calculator")!!.deleted)
    }
    @Test fun disabledSyncKeepsAccountScopeAndQueuesRemoval() = runTest {
        val users=MutableStateFlow<Long?>(1); val store=MemoryStore(); val remote=Remote()
        val repo=CloudSyncRepository(users,store,remote,backgroundScope,{1000})
        runCurrent(); repo.configure(true); runCurrent(); repo.configure(false); runCurrent()
        assertEquals(1L,repo.context.value.localScope)
        repo.setFavorite("calculator",false); val count=remote.pushes
        repo.sync(); assertEquals(count,remote.pushes); assertTrue(store.dirty(1).single().deleted)
    }
    @Test fun recentOptInIsSeparateAndClearLeavesDeletionVersions() = runTest {
        val users=MutableStateFlow<Long?>(1); val store=MemoryStore(); val remote=Remote()
        val repo=CloudSyncRepository(users,store,remote,backgroundScope,{1000})
        runCurrent(); repo.configure(true); runCurrent()
        assertNull(repo.context.value.recentScope)
        assertFalse(repo.recordUsage("calculator",1000))
        repo.configure(true,"RECENT"); runCurrent()
        repo.recordUsage("calculator",1000); repo.recordUsage("calculator",1001); repo.recordUsage("uuid",1002)
        assertEquals(2L,store.find(1,"RECENT","calculator")!!.useCount)
        repo.clearRecent(); repo.sync()
        assertTrue(store.records(1,"RECENT").first().all { it.deleted })
        assertTrue(remote.data[1]!!.getValue("RECENT:calculator").deleted)
    }
    class MemoryStore : SyncStore {
        override suspend fun deleteAccount(user: Long) { data.value=data.value-user; preferences.value=preferences.value-user }
        val preferences=MutableStateFlow<Map<Long,SyncPreference>>(emptyMap())
        val data=MutableStateFlow<Map<Long,Map<String,SyncRecord>>>(emptyMap())
        override fun preference(user: Long)=preferences.map { it[user] ?: SyncPreference() }
        override fun records(user: Long,type: String)=data.map { it[user].orEmpty().values.filter { r->r.entityType==type } }
        override suspend fun configure(user: Long,enabled: Boolean,device: String,type: String) {
            val old=preferences.value[user] ?: SyncPreference()
            val next=when(type) {
                "FAVORITE" -> old.copy(enabled=enabled,enrolled=old.enrolled || enabled)
                "RECENT" -> old.copy(recentEnabled=enabled,recentEnrolled=old.recentEnrolled || enabled)
                else -> old.copy(settingsEnabled=enabled,settingsEnrolled=old.settingsEnrolled || enabled)
            }
            preferences.value=preferences.value+(user to if(enabled) next.copy(cursor="") else next)
        }
        override suspend fun deviceId()="a".repeat(32)
        override suspend fun find(user: Long,type: String,key: String)=data.value[user]?.get(type+":"+key)
        override suspend fun put(user: Long,record: SyncRecord) { data.value=data.value+(user to (data.value[user].orEmpty()+(record.entityType+":"+record.entityKey to record))) }
        override suspend fun dirty(user: Long,types: Set<String>)=data.value[user].orEmpty().values.filter { it.dirty && it.entityType in types }
        override suspend fun acknowledge(user: Long,records: List<SyncRecord>) {
            records.forEach { val current=find(user,it.entityType,it.entityKey); if (current?.updatedAt==it.updatedAt && current.deviceId==it.deviceId) put(user,current.copy(dirty=false)) }
        }
        override suspend fun mergePage(user: Long,page: SyncPage) {
            page.items.forEach { val old=find(user,it.entityType,it.entityKey); if (old==null || it.newerThan(old)) put(user,it.copy(dirty=false)) }
            val pref=preferences.value[user] ?: SyncPreference()
            preferences.value=preferences.value+(user to pref.copy(cursor=page.nextCursor))
        }
    }
    @Test fun deletionClearsOnlyConfirmedAccountAndLeavesAnonymousAndOtherAccounts() = runTest {
        val users=MutableStateFlow<Long?>(1); val store=MemoryStore(); val remote=Remote()
        val repo=CloudSyncRepository(users,store,remote,backgroundScope,{1000}); runCurrent()
        val record=SyncRecord("FAVORITE","calculator",1000,"a",false,dirty=true)
        for(user in listOf(0L,1L,2L))store.put(user,record)
        try { repo.deleteAccount(1){throw IOException()}; fail() } catch(_: IOException) {}
        assertNotNull(store.find(1,"FAVORITE","calculator"))
        repo.deleteAccount(1){ users.value=null }; runCurrent()
        assertNull(store.find(1,"FAVORITE","calculator")); assertNotNull(store.find(0,"FAVORITE","calculator"))
        assertNotNull(store.find(2,"FAVORITE","calculator")); repo.sync(); assertEquals(0,remote.pushes)
    }
    private class Remote : SyncTransport {
        var pushes=0; var offline=false
        val data=mutableMapOf<Long,Map<String,SyncRecord>>()
        override suspend fun push(user: Long,device: String,records: List<SyncRecord>) {
            if (offline) throw IOException()
            pushes++; data[user]=data[user].orEmpty()+records.associateBy { it.entityType+":"+it.entityKey }
        }
        override suspend fun pull(user: Long,cursor: String): SyncPage {
            if (offline) throw IOException()
            return SyncPage(data[user].orEmpty().values.toList(),"1",false)
        }
    }
}
