package com.cangshuo.toolbox.sync.service;

import com.cangshuo.toolbox.sync.model.SyncModels.*;
import com.cangshuo.toolbox.sync.repository.SyncRepository;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SyncServiceTest {
    private final FakeRepository rows=new FakeRepository();
    private final long now=1791187200000L;
    private final SyncService service=new SyncService(rows,Clock.fixed(Instant.ofEpochMilli(now),ZoneOffset.UTC));
    private Push push(long at,String device,boolean deleted) {
        return new Push(device,List.of(new Item("FAVORITE","calculator",at,deleted,new Payload(null,null,null))));
    }
    @Test void duplicateRetryIsIdempotentAndRemoveWinsOverOlderAdd() {
        assertEquals(1,service.push(1,push(now,"a".repeat(32),false)).applied());
        assertEquals(0,service.push(1,push(now,"a".repeat(32),false)).applied());
        service.push(1,push(now+1,"b".repeat(32),true));
        assertEquals(0,service.push(1,push(now,"f".repeat(32),false)).applied());
        assertTrue(service.pull(1,null).items().getFirst().deleted());
        assertEquals(2,rows.revision(1));
    }
    @Test void equalTimestampUsesDeviceAndAccountsStaySeparate() {
        service.push(1,push(now,"b".repeat(32),true));
        service.push(1,push(now,"a".repeat(32),false));
        assertTrue(service.pull(1,null).items().getFirst().deleted());
        service.push(2,push(now,"a".repeat(32),false));
        assertFalse(service.pull(2,null).items().getFirst().deleted());
    }
    @Test void cursorIsAccountBoundAndCannotSkipFutureChanges() {
        service.push(1,push(now,"a".repeat(32),false));
        var first=service.pull(1,null);
        assertTrue(service.pull(1,first.nextCursor()).items().isEmpty());
        assertThrows(ApiException.class,()->service.pull(2,first.nextCursor()));
        assertThrows(ApiException.class,()->service.pull(1,SyncService.cursor(1,999)));
        assertThrows(ApiException.class,()->service.pull(1,"bad!"));
    }
    @Test void invalidClockPayloadAndDuplicateKeysFailBeforeAnyWrite() {
        assertThrows(ApiException.class,()->service.push(1,push(now+300001,"a".repeat(32),false)));
        assertThrows(ApiException.class,()->service.push(1,push(0,"a".repeat(32),false)));
        assertThrows(ApiException.class,()->service.push(1,new Push("a".repeat(32),List.of(
                new Item("FAVORITE","calculator",now,false,new Payload(1L,null,null))))));
        var item=push(now,"a".repeat(32),false).items().getFirst();
        assertThrows(ApiException.class,()->service.push(1,new Push("a".repeat(32),List.of(item,item))));
        assertEquals(0,rows.count(1));
    }
    @Test void recentUsesLwwCountAndRetainedDeletion() {
        var recent=new Item("RECENT","calculator",now,false,new Payload(now,7L,null));
        assertEquals(1,service.push(1,new Push("a".repeat(32),List.of(recent))).applied());
        var removed=new Item("RECENT","calculator",now+1,true,new Payload(null,null,null));
        service.push(1,new Push("b".repeat(32),List.of(removed)));
        assertEquals(0,service.push(1,new Push("a".repeat(32),List.of(recent))).applied());
        assertTrue(service.pull(1,null).items().getFirst().deleted());
        assertThrows(ApiException.class,()->service.push(1,new Push("a".repeat(32),List.of(
                new Item("RECENT","qr",now,false,new Payload(now,0L,null))))));
    }
    @Test void settingsOnlyAllowUiKeysAndDeletionRestoresDefaults() {
        var setting=new Item("SETTING","theme",now,false,new Payload(null,null,"DARK"));
        assertEquals(1,service.push(1,new Push("a".repeat(32),List.of(setting))).applied());
        assertEquals("DARK",service.pull(1,null).items().getFirst().payload().value());
        service.push(1,new Push("a".repeat(32),List.of(new Item("SETTING","theme",now+1,true,new Payload(null,null,null)))));
        assertTrue(service.pull(1,null).items().getFirst().deleted());
        for(var key:List.of("sync_enabled","api_secret","unknown")) assertThrows(ApiException.class,()->service.push(1,
                new Push("a".repeat(32),List.of(new Item("SETTING",key,now,false,new Payload(null,null,"true"))))));
        assertThrows(ApiException.class,()->service.push(1,new Push("a".repeat(32),List.of(
                new Item("SETTING","language",now,false,new Payload(null,null,"fr"))))));
    }
    private static class FakeRepository extends SyncRepository {
        private final Map<Long,Map<String,Entry>> data=new HashMap<>();
        private final Map<Long,Long> revisions=new HashMap<>();
        FakeRepository() { super(null,null); }
        @Override public boolean lockUser(long user) { return true; }
        @Override public long revision(long user) { return revisions.getOrDefault(user,0L); }
        @Override public void revision(long user,long revision) { revisions.put(user,revision); }
        @Override public Optional<Entry> find(long user,String type,String key) { return Optional.ofNullable(data.getOrDefault(user,Map.of()).get(type+key)); }
        @Override public long count(long user) { return data.getOrDefault(user,Map.of()).size(); }
        @Override public void save(long user,Entry e) { data.computeIfAbsent(user,u->new HashMap<>()).put(e.entityType()+e.entityKey(),e); }
        @Override public List<Entry> pull(long user,long after) {
            return data.getOrDefault(user,Map.of()).values().stream().filter(e->e.revision()>after).sorted(Comparator.comparingLong(Entry::revision)).limit(101).toList();
        }
    }
}
