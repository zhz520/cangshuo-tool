package com.cangshuo.toolbox.membership.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.membership.model.MembershipRequest;
import com.cangshuo.toolbox.membership.model.MembershipResponse;
import com.cangshuo.toolbox.membership.model.MembershipRow;
import com.cangshuo.toolbox.membership.repository.MembershipRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private static final Set<String> PLANS = Set.of("FREE", "PRO");
    private static final Duration MAX_GRANT = Duration.ofDays(3650);

    private final MembershipRepository repository;

    public MembershipService(MembershipRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public MembershipResponse status(long userId) {
        return toResponse(repository.find(userId).orElseThrow(() -> new ApiException(ApiError.NOT_FOUND)));
    }

    /** Administrator grant; FREE clears any expiry, PRO expires within ten years or stays permanent. */
    @Transactional
    public MembershipResponse grant(long userId, MembershipRequest request) {
        String plan = request.plan() == null ? "" : request.plan().trim().toUpperCase(Locale.ROOT);
        if (!PLANS.contains(plan)) throw new ApiException(ApiError.INVALID_ARGUMENT);
        Instant expiresAt = parseExpiry(request.expiresAt());
        if (plan.equals("FREE")) expiresAt = null;
        Instant now = Instant.now();
        if (expiresAt != null && (!expiresAt.isAfter(now) || expiresAt.isAfter(now.plus(MAX_GRANT)))) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        if (!repository.update(userId, plan, expiresAt)) throw new ApiException(ApiError.NOT_FOUND);
        return status(userId);
    }

    private static Instant parseExpiry(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
    }

    private static MembershipResponse toResponse(MembershipRow row) {
        boolean active = "PRO".equals(row.plan())
                && (row.expiresAt() == null || row.expiresAt().isAfter(Instant.now()));
        return new MembershipResponse(row.plan(), row.expiresAt() == null ? null : row.expiresAt().toString(), active);
    }
}
