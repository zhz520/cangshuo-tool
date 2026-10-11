package com.cangshuo.toolbox.sitelink.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.sitelink.model.SiteLinkRequest;
import com.cangshuo.toolbox.sitelink.model.SiteLinkRow;
import com.cangshuo.toolbox.sitelink.repository.SiteLinkRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiteLinkServiceTest {

    @Test void submitTrimsNormalizesAndKeepsPending() {
        var repository = new FakeRepository();
        var service = new SiteLinkService(repository);
        var response = service.submit(7L, new SiteLinkRequest("  GitHub  ", "https://github.com/explore", " 代码托管 "));
        assertEquals("GitHub", response.title());
        assertEquals("https://github.com/explore", response.url());
        assertEquals("代码托管", response.description());
        assertEquals(List.of("GitHub|https://github.com/explore|代码托管|7"), repository.inserted);
    }

    @Test void rejectsUnsafeInputs() {
        var service = new SiteLinkService(new FakeRepository());
        List<SiteLinkRequest> bad = List.of(
                new SiteLinkRequest("title", "ftp://example.com", ""),
                new SiteLinkRequest("title", "javascript:alert(1)", ""),
                new SiteLinkRequest("title", "https://user:pass@example.com", ""),
                new SiteLinkRequest("title", "https://", ""),
                new SiteLinkRequest(" ", "https://example.com", ""),
                new SiteLinkRequest("title", "https://example.com", "bad" + (char) 0 + "control"));
        for (SiteLinkRequest request : bad) {
            var failure = assertThrows(ApiException.class, () -> service.submit(1L, request));
            assertEquals(ApiError.INVALID_ARGUMENT, failure.error());
        }
    }

    private static class FakeRepository extends SiteLinkRepository {
        final List<String> inserted = new ArrayList<>();
        SiteLinkRow row;

        FakeRepository() { super(null); }

        @Override public long insert(String title, String url, String description, Long submittedBy) {
            inserted.add(title + "|" + url + "|" + description + "|" + submittedBy);
            row = new SiteLinkRow(1L, title, url, description, "PENDING", submittedBy,
                    Instant.parse("2026-10-11T13:30:00Z"), Instant.parse("2026-10-11T13:30:00Z"));
            return 1L;
        }

        @Override public SiteLinkRow find(long id) { return row; }
    }
}
