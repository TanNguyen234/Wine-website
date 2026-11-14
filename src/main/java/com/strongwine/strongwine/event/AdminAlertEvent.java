package com.strongwine.strongwine.event;

import org.springframework.context.ApplicationEvent;

public class AdminAlertEvent extends ApplicationEvent {

    private final String title;
    private final String message;
    private final String linkUrl;
    private final String severity;
    private final Long referenceOrderId;

    public AdminAlertEvent(
        Object source,
        String title,
        String message,
        String linkUrl,
        String severity,
        Long referenceOrderId
    ) {
        super(source != null ? source : AdminAlertEvent.class);
        this.title = title;
        this.message = message;
        this.linkUrl = linkUrl;
        this.severity = severity;
        this.referenceOrderId = referenceOrderId;
    }

    public AdminAlertEvent(
        Object source,
        String title,
        String message,
        String linkUrl,
        String severity
    ) {
        this(source, title, message, linkUrl, severity, null);
    }

    public AdminAlertEvent(
        String title,
        String message,
        String linkUrl,
        String severity,
        Long referenceOrderId
    ) {
        this(referenceOrderId != null ? referenceOrderId : AdminAlertEvent.class, title, message, linkUrl, severity, referenceOrderId);
    }

    public AdminAlertEvent(
        String title,
        String message,
        String linkUrl,
        String severity
    ) {
        this(AdminAlertEvent.class, title, message, linkUrl, severity, null);
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public String getSeverity() {
        return severity;
    }

    public Long getReferenceOrderId() {
        return referenceOrderId;
    }
}
