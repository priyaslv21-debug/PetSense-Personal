package com.example.pet.data;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data model representing a PetSense Community Event.
 * Stored under Cloud Firestore collection: `events/{eventId}`
 */
public class Event implements Serializable {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    public static final String VERIFY_UNVERIFIED = "UNVERIFIED";
    public static final String VERIFY_CLINIC = "VERIFIED_CLINIC";
    public static final String VERIFY_ORGANIZER = "VERIFIED_ORGANIZER";

    public static final String CATEGORY_VACCINATION = "Vaccination Camps";
    public static final String CATEGORY_MEETUP = "Pet Meetups";
    public static final String CATEGORY_TRAINING = "Training Workshops";
    public static final String CATEGORY_HEALTH_CHECKUP = "Health Check-up Camps";
    public static final String CATEGORY_ADOPTION = "Pet Adoption Events";
    public static final String CATEGORY_AWARENESS = "Pet Awareness Programs";
    public static final String CATEGORY_OTHER = "Other Pet-related Events";

    public static final String[] CATEGORIES = {
            CATEGORY_VACCINATION,
            CATEGORY_MEETUP,
            CATEGORY_TRAINING,
            CATEGORY_HEALTH_CHECKUP,
            CATEGORY_ADOPTION,
            CATEGORY_AWARENESS,
            CATEGORY_OTHER
    };

    private String eventId;
    private String title;
    private String description;
    private String category;
    private String imageUrl;
    private String organizerId;
    private String organizerName;
    private String organizerContact;
    private String organizerVerificationStatus;
    private Long eventDate;                 // Epoch milliseconds
    private String startTime;               // e.g. "10:00 AM"
    private String endTime;                 // e.g. "01:00 PM"
    private String venue;                   // e.g. "Riverside Dog Park"
    private String address;                 // Full street address
    private String city;                    // City name
    private String eventFee;                // "Free" or fee amount e.g. "$10"
    private int maximumParticipants;       // Capacity limit
    private Long registrationDeadline;      // Epoch milliseconds
    private int registrationCount;          // Currently registered participant count
    private String eventStatus;             // ACTIVE, CANCELLED, COMPLETED
    private Long createdAt;
    private Long updatedAt;

    /**
     * Default constructor required for Cloud Firestore deserialization.
     */
    public Event() {
        this.eventStatus = STATUS_ACTIVE;
        this.organizerVerificationStatus = VERIFY_UNVERIFIED;
        this.registrationCount = 0;
        this.maximumParticipants = 50;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Event(String title, String category, String description, String organizerId,
                 String organizerName, String organizerContact, Long eventDate,
                 String startTime, String endTime, String venue, String address,
                 String city, String eventFee, int maximumParticipants, Long registrationDeadline) {
        this();
        this.title = title;
        this.category = category;
        this.description = description;
        this.organizerId = organizerId;
        this.organizerName = organizerName;
        this.organizerContact = organizerContact;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.venue = venue;
        this.address = address;
        this.city = city;
        this.eventFee = eventFee;
        this.maximumParticipants = maximumParticipants;
        this.registrationDeadline = registrationDeadline;
    }

    public Event(String eventId, String title, String category, String description,
                 String organizerId, String organizerName, String organizerContact,
                 Long eventDate, String startTime, String endTime, String venue,
                 String address, String city, String eventFee, int maximumParticipants,
                 Long registrationDeadline) {
        this(title, category, description, organizerId, organizerName, organizerContact,
                eventDate, startTime, endTime, venue, address, city, eventFee,
                maximumParticipants, registrationDeadline);
        this.eventId = eventId;
    }

    // Getters and Setters

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
    }

    public String getOrganizerContact() {
        return organizerContact;
    }

    public void setOrganizerContact(String organizerContact) {
        this.organizerContact = organizerContact;
    }

    public String getOrganizerVerificationStatus() {
        return organizerVerificationStatus;
    }

    public void setOrganizerVerificationStatus(String organizerVerificationStatus) {
        this.organizerVerificationStatus = organizerVerificationStatus;
    }

    public Long getEventDate() {
        return eventDate;
    }

    public void setEventDate(Long eventDate) {
        this.eventDate = eventDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getEventFee() {
        return eventFee;
    }

    public void setEventFee(String eventFee) {
        this.eventFee = eventFee;
    }

    public int getMaximumParticipants() {
        return maximumParticipants;
    }

    public void setMaximumParticipants(int maximumParticipants) {
        this.maximumParticipants = maximumParticipants;
    }

    public Long getRegistrationDeadline() {
        return registrationDeadline;
    }

    public void setRegistrationDeadline(Long registrationDeadline) {
        this.registrationDeadline = registrationDeadline;
    }

    public int getRegistrationCount() {
        return registrationCount;
    }

    public void setRegistrationCount(int registrationCount) {
        this.registrationCount = registrationCount;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper business logic methods (Excluded from Firestore document serialization)

    @Exclude
    public boolean isFree() {
        if (eventFee == null) return true;
        String f = eventFee.trim().toLowerCase(Locale.ROOT);
        return f.isEmpty() || f.equals("free") || f.equals("0") || f.equals("0.00") || f.equals("$0") || f.equals("$0.00");
    }

    @Exclude
    public boolean isOrganizerVerified() {
        return isVerifiedOrganizer();
    }

    public void setOrganizerVerified(boolean verified) {
        this.organizerVerificationStatus = verified ? VERIFY_ORGANIZER : VERIFY_UNVERIFIED;
    }

    @Exclude
    public boolean isFull() {
        return maximumParticipants > 0 && registrationCount >= maximumParticipants;
    }

    @Exclude
    public boolean isPastDeadline() {
        return registrationDeadline != null && registrationDeadline > 0 && System.currentTimeMillis() > registrationDeadline;
    }

    @Exclude
    public boolean isPastEvent() {
        return eventDate != null && eventDate > 0 && System.currentTimeMillis() > eventDate;
    }

    @Exclude
    public boolean isRegistrationOpen() {
        return STATUS_ACTIVE.equalsIgnoreCase(eventStatus) && !isFull() && !isPastDeadline() && !isPastEvent();
    }

    @Exclude
    public int getAvailableSeats() {
        if (maximumParticipants <= 0) return Integer.MAX_VALUE; // Unlimited
        return Math.max(0, maximumParticipants - registrationCount);
    }

    @Exclude
    public boolean isVerifiedOrganizer() {
        return organizerVerificationStatus != null && !VERIFY_UNVERIFIED.equalsIgnoreCase(organizerVerificationStatus);
    }

    @Exclude
    public boolean isMedicalOrVaccineEvent() {
        if (category == null) return false;
        String c = category.toLowerCase(Locale.ROOT);
        return c.contains("vaccin") || c.contains("health") || c.contains("check-up") || c.contains("medical");
    }

    @Exclude
    @Nullable
    public String getFormattedDate() {
        if (eventDate == null || eventDate <= 0) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
        return sdf.format(new Date(eventDate));
    }

    @Exclude
    @Nullable
    public String getFormattedDeadline() {
        if (registrationDeadline == null || registrationDeadline <= 0) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        return sdf.format(new Date(registrationDeadline));
    }
}
