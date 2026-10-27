package com.civicbridge.analytics;

import java.util.Set;

/** Closed list of event types. Client-submittable types are a strict subset. */
public enum EventType {
    LOGIN_SUCCESS, LOGOUT, SESSION_EXPIRED, REGISTRATION,
    CASE_SUBMITTED, DOCUMENT_UPLOADED, AI_GUIDANCE_USED,
    PAGE_VIEW;

    /** Only page views may be reported by the browser; everything else is server-generated. */
    public static final Set<EventType> CLIENT_ALLOWED = Set.of(PAGE_VIEW);
}
