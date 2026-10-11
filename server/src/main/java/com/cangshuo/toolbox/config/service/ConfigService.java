package com.cangshuo.toolbox.config.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.config.model.ConfigEntry;
import com.cangshuo.toolbox.config.repository.ConfigRepository;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator-managed business configuration with a fixed key whitelist. */
@Service
public class ConfigService {

    public static final String APP_LATEST_VERSION_CODE = "APP_LATEST_VERSION_CODE";
    public static final String APP_LATEST_VERSION_NAME = "APP_LATEST_VERSION_NAME";
    public static final String APP_UPDATE_URL = "APP_UPDATE_URL";
    public static final String APP_UPDATE_NOTES = "APP_UPDATE_NOTES";

    private static final List<String> KEYS = List.of(APP_LATEST_VERSION_CODE, APP_LATEST_VERSION_NAME,
            APP_UPDATE_URL, APP_UPDATE_NOTES);
    private static final Pattern VERSION_NAME = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.]+)?");

    private final ConfigRepository repository;

    public ConfigService(ConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ConfigEntry> list() {
        Map<String, String> values = repository.find(KEYS);
        return KEYS.stream().map(key -> new ConfigEntry(key, values.getOrDefault(key, ""))).toList();
    }

    @Transactional
    public ConfigEntry update(String key, String value) {
        if (!KEYS.contains(key)) throw new ApiException(ApiError.INVALID_ARGUMENT);
        String normalized = value == null ? "" : value.trim();
        validate(key, normalized);
        if (!repository.update(key, normalized)) throw new ApiException(ApiError.INTERNAL_ERROR);
        return new ConfigEntry(key, normalized);
    }

    /** Stored value for one whitelisted key; absent or blank rows fall back to caller defaults. */
    public Optional<String> value(String key) {
        return Optional.ofNullable(repository.find(List.of(key)).get(key));
    }

    public OptionalLong longValue(String key) {
        return value(key).flatMap(raw -> {
            try {
                return Optional.of(Long.parseLong(raw.trim()));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }).map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    private static void validate(String key, String value) {
        if (value.chars().anyMatch(Character::isISOControl)) throw invalid();
        switch (key) {
            case APP_LATEST_VERSION_CODE -> {
                long parsed;
                try {
                    parsed = Long.parseLong(value);
                } catch (NumberFormatException exception) {
                    throw invalid();
                }
                if (parsed < 1 || parsed > 2_100_000_000L) throw invalid();
            }
            case APP_LATEST_VERSION_NAME -> {
                if (value.length() > 40 || !VERSION_NAME.matcher(value).matches()) throw invalid();
            }
            case APP_UPDATE_URL -> {
                if (value.isEmpty()) return;
                if (value.length() > 500) throw invalid();
                try {
                    URI uri = new URI(value);
                    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                    if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null
                            || uri.getHost().isBlank() || uri.getUserInfo() != null) throw invalid();
                } catch (java.net.URISyntaxException exception) {
                    throw invalid();
                }
            }
            case APP_UPDATE_NOTES -> {
                if (value.length() > 300) throw invalid();
            }
            default -> throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ApiError.INVALID_ARGUMENT);
    }
}
