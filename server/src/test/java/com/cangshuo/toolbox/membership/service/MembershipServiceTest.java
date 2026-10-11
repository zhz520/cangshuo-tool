package com.cangshuo.toolbox.membership.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.membership.model.MembershipRequest;
import com.cangshuo.toolbox.membership.model.MembershipRow;
import com.cangshuo.toolbox.membership.repository.MembershipRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MembershipServiceTest {

    @Test void freePlanIsInactiveAndProWithFutureExpiryIsActive() {
        var repository = new FakeRepository();
        var service = new MembershipService(repository);
        repository.row = new MembershipRow("FREE", null);
        assertFalse(service.status(1).active());
        repository.row = new MembershipRow("PRO", Instant.now().plusSeconds(86400));
        assertTrue(service.status(1).active());
        repository.row = new MembershipRow("PRO", Instant.now().minusSeconds(60));
        assertFalse(service.status(1).active());
    }

    @Test void grantValidatesPlanAndExpiryBounds() {
        var repository = new FakeRepository();
        var service = new MembershipService(repository);
        assertThrows(ApiException.class, () -> service.grant(1, new MembershipRequest("VIP", null)));
        assertThrows(ApiException.class, () -> service.grant(1, new MembershipRequest("PRO", "2020-01-01T00:00:00Z")));
        assertThrows(ApiException.class, () -> service.grant(1, new MembershipRequest("PRO", "not-a-time")));
    }

    @Test void grantStoresNormalizedPlanAndClearsExpiryForFree() {
        var repository = new FakeRepository();
        var service = new MembershipService(repository);
        repository.row = new MembershipRow("PRO", null);
        service.grant(1, new MembershipRequest("pro", null));
        assertEquals("PRO", repository.lastPlan);
        assertNull(repository.lastExpires);
        repository.row = new MembershipRow("FREE", null);
        service.grant(1, new MembershipRequest("FREE", "2027-10-11T00:00:00Z"));
        assertEquals("FREE", repository.lastPlan);
        assertNull(repository.lastExpires);
    }

    @Test void unknownAccountMapsToNotFound() {
        var repository = new FakeRepository();
        repository.row = null;
        var service = new MembershipService(repository);
        var failure = assertThrows(ApiException.class, () -> service.status(9));
        assertEquals(ApiError.NOT_FOUND, failure.error());
    }

    private static class FakeRepository extends MembershipRepository {
        MembershipRow row = new MembershipRow("FREE", null);
        String lastPlan = "";
        Instant lastExpires;

        FakeRepository() { super(null); }

        @Override public Optional<MembershipRow> find(long userId) { return Optional.ofNullable(row); }

        @Override public boolean update(long userId, String plan, Instant expiresAt) {
            if (row == null) return false;
            lastPlan = plan;
            lastExpires = expiresAt;
            row = new MembershipRow(plan, expiresAt);
            return true;
        }
    }
}
