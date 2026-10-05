package com.cangshuo.toolbox.feature.sync.domain

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsTest {
    @Test fun anonymousAppearanceIsStoredWithoutUploading() = runTest {
        val users=MutableStateFlow<Long?>(null); val store=CloudSyncTest.MemoryStore()
        var uploads=0
        val remote=object : SyncTransport {
            override suspend fun push(user: Long,device: String,records: List<SyncRecord>) { uploads++ }
            override suspend fun pull(user: Long,cursor: String)=SyncPage(emptyList(),"1",false)
        }
        val repo=CloudSyncRepository(users,store,remote,backgroundScope,{1000})
        runCurrent(); repo.setSetting("theme","DARK"); repo.setSetting("language","en"); runCurrent(); repo.sync()
        assertEquals("DARK",repo.settings.value.theme); assertEquals("en",repo.settings.value.language)
        assertTrue(store.dirty(0).isEmpty()); assertEquals(0,uploads)
        try { repo.setSetting("sync_enabled","true"); fail() } catch(_: IllegalArgumentException) { }
    }
    @Test fun settingsAreAccountScopedAndOnlyOptedInTypesUpload() = runTest {
        val users=MutableStateFlow<Long?>(1); val store=CloudSyncTest.MemoryStore()
        val uploaded=mutableListOf<SyncRecord>()
        val remote=object : SyncTransport {
            override suspend fun push(user: Long,device: String,records: List<SyncRecord>) { uploaded.addAll(records) }
            override suspend fun pull(user: Long,cursor: String)=SyncPage(emptyList(),"1",false)
        }
        val repo=CloudSyncRepository(users,store,remote,backgroundScope,{1000})
        runCurrent(); repo.configure(true,"SETTING"); runCurrent()
        store.put(1,SyncRecord("RECENT","calculator",1000,"a",false,lastUsedAt=1000,useCount=1,dirty=true))
        repo.setSetting("theme","DARK"); runCurrent(); repo.sync()
        assertTrue(uploaded.isNotEmpty()); assertTrue(uploaded.all { it.entityType=="SETTING" })
        assertEquals(1,store.dirty(1,setOf("RECENT")).size)
        users.value=null;runCurrent(); assertEquals("SYSTEM",repo.settings.value.theme)
        users.value=2;runCurrent(); assertEquals("SYSTEM",repo.settings.value.theme)
        users.value=1;runCurrent(); assertEquals("DARK",repo.settings.value.theme)
    }
    @Test fun settingsPolicyRejectsUnlistedKeysAndUsesDefaultsForBadLocalRecords() {
        assertFalse(SettingsPolicy.valid("secret","anything")); assertFalse(SettingsPolicy.valid("theme","custom"))
        assertFalse(SettingsPolicy.valid("language","fr")); assertFalse(SettingsPolicy.valid("grid_columns","99"))
        val settings=SettingsPolicy.read(listOf(SyncRecord("SETTING","theme",1,"a",false,value="custom"),
            SyncRecord("SETTING","language",1,"a",true,value="en"),SyncRecord("SETTING","grid_columns",1,"a",false,value="3")))
        assertEquals("SYSTEM",settings.theme); assertEquals("SYSTEM",settings.language); assertEquals(3,settings.gridColumns)
    }
}
