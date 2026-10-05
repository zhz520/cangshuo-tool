package com.cangshuo.toolbox.sync.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.sync.model.SyncModels.*;
import com.cangshuo.toolbox.sync.repository.SyncRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncService {
    private final SyncRepository repository;
    private final Clock clock;
    public SyncService(SyncRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }
    @Transactional
    public PushResult push(long user, Push request) {
        var keys=new HashSet<String>();
        for (var i:request.items()) {
            validate(i);
            if (!keys.add(i.entityType()+":"+i.entityKey())) invalid();
        }
        if (!repository.lockUser(user)) throw new ApiException(ApiError.UNAUTHENTICATED);
        long revision=repository.revision(user), count=repository.count(user);
        int applied=0;
        for (var i:request.items()) {
            var old=repository.find(user,i.entityType(),i.entityKey());
            if (old.isPresent() && !newer(i.updatedAt(),request.deviceId(),old.get().updatedAt(),old.get().deviceId())) continue;
            if (old.isEmpty() && ++count>10000) invalid();
            var entry=new Entry(i.entityType(),i.entityKey(),i.updatedAt(),request.deviceId(),i.deleted(),i.payload(),++revision);
            repository.save(user,entry); applied++;
        }
        if (applied>0) repository.revision(user,revision);
        return new PushResult(applied,cursor(user,revision));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PullResult pull(long user, String cursor) {
        long after=parseCursor(user,cursor), head=repository.revision(user);
        if (after>head) invalid();
        var entries=repository.pull(user,after);
        boolean more=entries.size()>100;
        var page=entries.stream().limit(100).toList();
        long next=more ? page.getLast().revision() : head;
        return new PullResult(page,cursor(user,next),more);
    }
    void validate(Item i) {
        if (!i.entityKey().matches("[a-z][a-z0-9_]{0,63}") || i.updatedAt()<946684800000L || i.updatedAt()>clock.millis()+300000) invalid();
        var payload=i.payload();
        if ("FAVORITE".equals(i.entityType())) {
            if (payload.lastUsedAt()!=null || payload.useCount()!=null || payload.value()!=null) invalid();
        } else if ("RECENT".equals(i.entityType())) {
            if (payload.value()!=null) invalid();
            if (!i.deleted() && (payload.lastUsedAt()==null || payload.useCount()==null ||
                    payload.lastUsedAt()<946684800000L || payload.lastUsedAt()>clock.millis()+300000 ||
                    payload.useCount()<1 || payload.useCount()>2147483647L)) invalid();
            if (i.deleted() && (payload.lastUsedAt()!=null || payload.useCount()!=null)) invalid();
        } else if ("SETTING".equals(i.entityType())) {
            if (payload.lastUsedAt()!=null || payload.useCount()!=null) invalid();
            var options=switch(i.entityKey()) {
                case "theme" -> java.util.Set.of("SYSTEM","LIGHT","DARK");
                case "language" -> java.util.Set.of("SYSTEM","zh-CN","en");
                case "grid_columns" -> java.util.Set.of("AUTO","1","2","3");
                case "startup_page" -> java.util.Set.of("HOME","TOOLS","FAVORITES","PROFILE");
                default -> java.util.Set.<String>of();
            };
            if (options.isEmpty() || (i.deleted() ? payload.value()!=null : payload.value()==null || !options.contains(payload.value()))) invalid();
        } else invalid();
    }
    public static boolean newer(long at,String device,long oldAt,String oldDevice) {
        return at>oldAt || (at==oldAt && device.compareTo(oldDevice)>0);
    }
    public static String cursor(long user,long revision) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((user+":"+revision).getBytes(StandardCharsets.US_ASCII));
    }
    static long parseCursor(long user,String cursor) {
        if (cursor==null || cursor.isEmpty()) return 0;
        try {
            if (cursor.length()>128 || !cursor.matches("[A-Za-z0-9_-]+")) { invalid(); }
            var text=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.US_ASCII);
            if (!text.matches("[1-9][0-9]*:(0|[1-9][0-9]*)")) invalid();
            var parts=text.split(":");
            if (Long.parseLong(parts[0])!=user) invalid();
            return Long.parseLong(parts[1]);
        } catch (IllegalArgumentException e) { throw new ApiException(ApiError.INVALID_ARGUMENT); }
    }
    private static void invalid() { throw new ApiException(ApiError.INVALID_ARGUMENT); }
}
