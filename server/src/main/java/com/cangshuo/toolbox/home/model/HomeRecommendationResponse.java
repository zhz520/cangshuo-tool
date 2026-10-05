package com.cangshuo.toolbox.home.model;

/** Public recommendation view; no identifiers, schedule or audit data are exposed. */
public record HomeRecommendationResponse(String slotCode, String title, String subtitle, String toolCode,
    String linkUrl, String imageUrl) { }
