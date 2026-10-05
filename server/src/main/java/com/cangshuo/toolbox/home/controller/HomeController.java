package com.cangshuo.toolbox.home.controller;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.home.model.HomeAnnouncementResponse;
import com.cangshuo.toolbox.home.model.HomeRecommendationResponse;
import com.cangshuo.toolbox.home.repository.HomeRecommendationRepository;
import com.cangshuo.toolbox.home.repository.HomeAnnouncementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/home", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Home")
public class HomeController {
    private final HomeRecommendationRepository recommendations;
    private final HomeAnnouncementRepository announcements;
    public HomeController(HomeRecommendationRepository recommendations, HomeAnnouncementRepository announcements) {
        this.recommendations = recommendations; this.announcements = announcements;
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Read the currently active home recommendations (anonymous)")
    public ApiResponse<List<HomeRecommendationResponse>> recommendations(HttpServletRequest request) {
        return ApiResponse.success(recommendations.active(), TraceIdFilter.traceId(request));
    }

    @GetMapping("/announcements")
    @Operation(summary = "Read currently visible announcements (anonymous, newest first)")
    public ApiResponse<List<HomeAnnouncementResponse>> announcements(HttpServletRequest request) {
        return ApiResponse.success(announcements.active(), TraceIdFilter.traceId(request));
    }
}
