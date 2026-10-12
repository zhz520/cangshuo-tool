package com.cangshuo.toolbox.zepp;

import com.cangshuo.toolbox.common.exception.*;
import com.cangshuo.toolbox.common.ratelimit.RateLimitService;
import com.cangshuo.toolbox.tool.repository.ToolUsageRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ZeppStepsService {
    public static final String TOOL_CODE = "zepp_steps";
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern PHONE = Pattern.compile("(?:\\+86)?1[3-9][0-9]{9}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}");
    private final ZeppProperties properties;
    private final ZeppProvider provider;
    private final ToolUsageRepository catalog;
    private final RateLimitService limiter;
    private final Semaphore slots = new Semaphore(4);
    private final byte[] salt = new SecureRandom().generateSeed(32);
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ZeppStepsService(ZeppProperties properties, ZeppProvider provider, ToolUsageRepository catalog,
                            RateLimitService limiter) {
        this(properties, provider, catalog, limiter, Clock.systemUTC());
    }
    ZeppStepsService(ZeppProperties properties, ZeppProvider provider, ToolUsageRepository catalog,
                     RateLimitService limiter, Clock clock) {
        this.properties = properties; this.provider = provider; this.catalog = catalog;
        this.limiter = limiter; this.clock = clock;
    }

    public Capabilities capabilities() {
        return new Capabilities(properties.ready() && catalog.visible(TOOL_CODE),
                LocalDate.now(clock.withZone(ZONE)), 1, 50000, 60, 30);
    }

    public ZeppStepsResponse submit(ZeppStepsRequest request) {
        if (!properties.ready()) throw new ApiException(ApiError.ZEPP_NOT_CONFIGURED);
        if (!catalog.visible(TOOL_CODE)) throw new ApiException(ApiError.SERVICE_UNAVAILABLE);
        String account = normalize(request.account());
        if (request.password() == null || request.password().isBlank() || request.password().length() > 128
                || request.steps() == null || request.steps() < 1 || request.steps() > 50000
                || !Boolean.TRUE.equals(request.consent())) throw new ApiException(ApiError.INVALID_ARGUMENT);
        if (!slots.tryAcquire()) throw new ApiException(ApiError.SERVICE_UNAVAILABLE);
        try {
            if (!limiter.check(accountKey(account), 1, 60).allowed())
                throw new ApiException(ApiError.ZEPP_ACCOUNT_COOLDOWN);
            var result = provider.submit(account, request.password(), request.steps());
            return new ZeppStepsResponse("ACCEPTED", request.steps(), result.date(), result.submittedAt(),
                    mask(account), result.boundDevice(), false);
        } finally { slots.release(); }
    }

    static String normalize(String input) {
        if (input == null) throw new ApiException(ApiError.INVALID_ARGUMENT);
        String account = input.strip();
        if (account.length() > 254) throw new ApiException(ApiError.INVALID_ARGUMENT);
        if (PHONE.matcher(account).matches()) return account.startsWith("+") ? account : "+86" + account;
        if (!EMAIL.matcher(account).matches() || account.contains("..")) throw new ApiException(ApiError.INVALID_ARGUMENT);
        int at = account.indexOf('@');
        return account.substring(0, at + 1) + account.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    static String mask(String account) {
        int at = account.indexOf('@');
        if (at >= 0) return account.substring(0, Math.min(at, 2)) + "***" + account.substring(at);
        return account.substring(0, 5) + "****" + account.substring(account.length() - 4);
    }

    private String accountKey(String account) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return "toolbox:rate:zepp:" + HexFormat.of().formatHex(
                    digest.digest(account.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    public record Capabilities(boolean available, LocalDate date, int minSteps, int maxSteps,
                               int cooldownSeconds, int timeoutSeconds) {}
}
