package com.example.pet.data;

import java.io.Serializable;

/**
 * Data model for reporting events that contain misleading veterinary claims,
 * fraudulent fees, or inappropriate content.
 * Stored under Firestore collection: event_reports/{reportId}
 */
public class EventReport implements Serializable {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REVIEWED = "REVIEWED";
    public static final String STATUS_DISMISSED = "DISMISSED";

    private String reportId;
    private String eventId;
    private String eventTitle;
    private String reportedByUserId;
    private String reportedByEmail;
    private String reason;
    private String details;
    private String status;
    private Long reportedAt;

    public EventReport() {
        this.status = STATUS_PENDING;
        this.reportedAt = System.currentTimeMillis();
    }

    public EventReport(String reportId, String eventId, String eventTitle,
                       String reportedByUserId, String reportedByEmail,
                       String reason, String details) {
        this();
        this.reportId = reportId;
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.reportedByUserId = reportedByUserId;
        this.reportedByEmail = reportedByEmail;
        this.reason = reason;
        this.details = details;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public String getReportedByUserId() {
        return reportedByUserId;
    }

    public void setReportedByUserId(String reportedByUserId) {
        this.reportedByUserId = reportedByUserId;
    }

    public String getReportedByEmail() {
        return reportedByEmail;
    }

    public void setReportedByEmail(String reportedByEmail) {
        this.reportedByEmail = reportedByEmail;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Long getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(Long reportedAt) {
        this.reportedAt = reportedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // Convenience aliases
    public String getReporterId() {
        return reportedByUserId;
    }

    public String getReporterEmail() {
        return reportedByEmail;
    }

    public Long getCreatedAt() {
        return reportedAt;
    }
}
