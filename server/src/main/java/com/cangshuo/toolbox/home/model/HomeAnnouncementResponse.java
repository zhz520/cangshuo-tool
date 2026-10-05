package com.cangshuo.toolbox.home.model;

/** Public announcement view: plain text only, no schedule or audit fields. */
public record HomeAnnouncementResponse(long id, String title, String body, String level) { }
