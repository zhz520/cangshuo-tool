package com.cangshuo.toolbox.sitelink.service;

import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.sitelink.model.SiteLinkRequest;
import com.cangshuo.toolbox.sitelink.model.SiteLinkResponse;
import com.cangshuo.toolbox.sitelink.model.SiteLinkRow;
import com.cangshuo.toolbox.sitelink.repository.SiteLinkRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteLinkService {

    private static final int MAX_TITLE = 100;
    private static final int MAX_URL = 500;
    private static final int MAX_DESCRIPTION = 300;

    private final SiteLinkRepository repository;

    public SiteLinkService(SiteLinkRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SiteLinkResponse> list(int limit) {
        return repository.findApproved(limit).stream().map(SiteLinkService::toResponse).toList();
    }

    @Transactional
    public SiteLinkResponse submit(long userId, SiteLinkRequest request) {
        String title = normalize(request.title(), MAX_TITLE);
        String description = normalize(request.description() == null ? "" : request.description(), MAX_DESCRIPTION);
        String url = normalizeUrl(request.url());
        if (title.isEmpty()) throw invalid();
        long id = repository.insert(title, url, description, userId);
        SiteLinkRow row = repository.find(id);
        if (row == null) throw new ApiException(ApiError.INTERNAL_ERROR);
        return toResponse(row);
    }

    private static String normalize(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > max || trimmed.chars().anyMatch(Character::isISOControl)) throw invalid();
        return trimmed;
    }

    /** Only http(s) URLs with a host and without embedded credentials are accepted. */
    private static String normalizeUrl(String value) {
        String trimmed = normalize(value, MAX_URL);
        if (trimmed.isEmpty()) throw invalid();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) throw invalid();
            if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) throw invalid();
            if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)) throw invalid();
            return uri.toString();
        } catch (URISyntaxException exception) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ApiError.INVALID_ARGUMENT);
    }

    private static SiteLinkResponse toResponse(SiteLinkRow row) {
        return new SiteLinkResponse(row.id(), row.title(), row.url(), row.description(), row.createdAt().toString());
    }
}
