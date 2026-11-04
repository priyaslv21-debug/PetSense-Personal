package com.example.pet.data;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Model representing a Pet Care Reminder (Vaccination, Check-up, Deworming, Medication).
 * Compatible with Firestore serialization and local Android notifications.
 */
public class Reminder implements Serializable {

    // Category Types
    public static final String TYPE_VACCINATION = "Vaccination";
    public static final String TYPE_CHECKUP = "Routine Check-up";
    public static final String TYPE_DEWORMING = "Deworming";
    public static final String TYPE_MEDICATION = "Medication";

    // Status Constants
    public static final String STATUS_UPCOMING = "Upcoming";
    public static final String STATUS_DUE_TODAY = "Due Today";
    public static final String STATUS_OVERDUE = "Overdue";
    public static final String STATUS_COMPLETED = "Completed";

    // Repeat Frequencies
    public static final String FREQ_NONE = "None";
    public static final String FREQ_DAILY = "Daily";
    public static final String FREQ_WEEKLY = "Weekly";
    public static final String FREQ_MONTHLY = "Monthly";
    public static final String FREQ_YEARLY = "Yearly";

    // Advance Notification Options
    public static final String ADVANCE_EXACT = "At time of event";
    public static final String ADVANCE_1_HOUR = "1 hour before";
    public static final String ADVANCE_1_DAY = "1 day before";
    public static final String ADVANCE_2_DAYS = "2 days before";
    public static final String ADVANCE_1_WEEK = "1 week before";

    private String reminderId;
    private String petId;
    private String petName;
    private String reminderType;        // Vaccination, Routine Check-up, Deworming, Medication
    private String title;
    private String description;
    private String scheduledDate;       // Format: yyyy-MM-dd
    private String scheduledTime;       // Format: hh:mm a (e.g. 09:00 AM)
    private long scheduledTimestamp;    // Epoch millis for exact sorting and alarm triggering
    private String repeatFrequency;     // None, Daily, Weekly, Monthly, Yearly
    private String advanceNotification; // Exact, 1 hour, 1 day, 2 days, 1 week
    private String status;              // Upcoming, Due Today, Overdue, Completed
    private boolean isEnabled;          // Alarms active or disabled
    private Date createdAt;
    private Date updatedAt;

    // Medication specific fields
    private String medicationName;
    private String medicationDosage;
    private String startDate;
    private String endDate;
    private String dosageFrequency;
    private List<String> administrationTimes = new ArrayList<>();

    /** Required no-arg constructor for Firestore deserialization. */
    public Reminder() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.isEnabled = true;
        this.status = STATUS_UPCOMING;
        this.repeatFrequency = FREQ_NONE;
        this.advanceNotification = ADVANCE_EXACT;
        this.reminderType = TYPE_VACCINATION;
    }

    public Reminder(String petId, String petName, String reminderType, String title,
                    String description, String scheduledDate, String scheduledTime,
                    long scheduledTimestamp, String repeatFrequency, String advanceNotification) {
        this();
        this.petId = petId;
        this.petName = petName;
        this.reminderType = reminderType;
        this.title = title;
        this.description = description;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.scheduledTimestamp = scheduledTimestamp;
        this.repeatFrequency = (repeatFrequency != null) ? repeatFrequency : FREQ_NONE;
        this.advanceNotification = (advanceNotification != null) ? advanceNotification : ADVANCE_EXACT;
        this.status = calculateCurrentStatus();
    }

    // Getters and Setters
    public String getReminderId() { return reminderId; }
    public void setReminderId(String reminderId) { this.reminderId = reminderId; }

    public String getPetId() { return petId; }
    public void setPetId(String petId) { this.petId = petId; }

    public String getPetName() { return petName; }
    public void setPetName(String petName) { this.petName = petName; }

    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }

    @Exclude
    public String getType() { return reminderType; }
    public void setType(String type) { this.reminderType = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }

    public String getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }

    public long getScheduledTimestamp() { return scheduledTimestamp; }
    public void setScheduledTimestamp(long scheduledTimestamp) { this.scheduledTimestamp = scheduledTimestamp; }

    public String getRepeatFrequency() { return repeatFrequency; }
    public void setRepeatFrequency(String repeatFrequency) { this.repeatFrequency = repeatFrequency; }

    public String getAdvanceNotification() { return advanceNotification; }
    public void setAdvanceNotification(String advanceNotification) { this.advanceNotification = advanceNotification; }

    @Exclude
    public String getAdvanceNotice() { return advanceNotification; }
    public void setAdvanceNotice(String advanceNotice) { this.advanceNotification = advanceNotice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public String getMedicationName() { return medicationName; }
    public void setMedicationName(String medicationName) { this.medicationName = medicationName; }

    public String getMedicationDosage() { return medicationDosage; }
    public void setMedicationDosage(String medicationDosage) { this.medicationDosage = medicationDosage; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    @Exclude
    public String getMedicationStartDate() { return startDate; }
    public void setMedicationStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    @Exclude
    public String getMedicationEndDate() { return endDate; }
    public void setMedicationEndDate(String endDate) { this.endDate = endDate; }

    public String getDosageFrequency() { return dosageFrequency; }
    public void setDosageFrequency(String dosageFrequency) { this.dosageFrequency = dosageFrequency; }

    @Exclude
    public String getMedicationFrequency() { return dosageFrequency; }
    public void setMedicationFrequency(String freq) { this.dosageFrequency = freq; }

    public List<String> getAdministrationTimes() { return administrationTimes; }
    public void setAdministrationTimes(List<String> administrationTimes) {
        this.administrationTimes = administrationTimes != null ? administrationTimes : new ArrayList<>();
    }

    @Exclude
    public List<String> getMedicationAdminTimes() { return administrationTimes; }
    public void setMedicationAdminTimes(List<String> times) { setAdministrationTimes(times); }

    // Helper Methods

    /** Computes dynamic status (Overdue, Due Today, Upcoming, or Completed). */
    public String calculateCurrentStatus() {
        if (STATUS_COMPLETED.equalsIgnoreCase(status)) {
            return STATUS_COMPLETED;
        }

        Calendar scheduledCal = Calendar.getInstance();
        scheduledCal.setTimeInMillis(scheduledTimestamp);

        Calendar todayCal = Calendar.getInstance();

        boolean isSameYear = scheduledCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR);
        boolean isSameDay = isSameYear && (scheduledCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR));

        if (isSameDay) {
            return STATUS_DUE_TODAY;
        } else if (scheduledTimestamp < System.currentTimeMillis()) {
            return STATUS_OVERDUE;
        } else {
            return STATUS_UPCOMING;
        }
    }

    public boolean isOverdue() {
        return STATUS_OVERDUE.equalsIgnoreCase(calculateCurrentStatus());
    }

    public boolean isDueToday() {
        return STATUS_DUE_TODAY.equalsIgnoreCase(calculateCurrentStatus());
    }

    public boolean isUpcoming() {
        return STATUS_UPCOMING.equalsIgnoreCase(calculateCurrentStatus());
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equalsIgnoreCase(status);
    }

    /** Formatted date display, e.g. "Nov 15, 2026 • 09:00 AM". */
    public String getFormattedDateTime() {
        if (scheduledTimestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy \u00b7 h:mm a", Locale.getDefault());
            return sdf.format(new Date(scheduledTimestamp));
        }
        String d = scheduledDate != null ? scheduledDate : "";
        String t = scheduledTime != null ? scheduledTime : "";
        return d + (!t.isEmpty() ? " \u00b7 " + t : "");
    }

    /** Calculates the exact alarm trigger millis taking into account advance notice. */
    public long calculateAlarmTriggerTimestamp() {
        long triggerTime = scheduledTimestamp;
        if (ADVANCE_1_HOUR.equalsIgnoreCase(advanceNotification)) {
            triggerTime -= (60 * 60 * 1000L);
        } else if (ADVANCE_1_DAY.equalsIgnoreCase(advanceNotification)) {
            triggerTime -= (24 * 60 * 60 * 1000L);
        } else if (ADVANCE_2_DAYS.equalsIgnoreCase(advanceNotification)) {
            triggerTime -= (2 * 24 * 60 * 60 * 1000L);
        } else if (ADVANCE_1_WEEK.equalsIgnoreCase(advanceNotification)) {
            triggerTime -= (7 * 24 * 60 * 60 * 1000L);
        }
        return triggerTime;
    }

    /** Computes the next scheduled timestamp for recurring reminders. */
    public long computeNextScheduledTimestamp() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(scheduledTimestamp);

        if (FREQ_DAILY.equalsIgnoreCase(repeatFrequency)) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        } else if (FREQ_WEEKLY.equalsIgnoreCase(repeatFrequency)) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        } else if (FREQ_MONTHLY.equalsIgnoreCase(repeatFrequency)) {
            cal.add(Calendar.MONTH, 1);
        } else if (FREQ_YEARLY.equalsIgnoreCase(repeatFrequency)) {
            cal.add(Calendar.YEAR, 1);
        }
        return cal.getTimeInMillis();
    }
}
