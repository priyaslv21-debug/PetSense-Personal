package com.example.pet.data;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data model representing a user's registration for an event.
 * Stored under:
 * 1. events/{eventId}/registrations/{userId}
 * 2. users/{userId}/registered_events/{eventId} (for user's private event listing)
 */
public class EventRegistration implements Serializable {

    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String registrationId; // Usually userId
    private String eventId;
    private String userId;
    private String participantName;
    private String email;
    private String phone;
    private String petId;
    private String petName;
    private String petSpecies;
    private String petAge;
    private String additionalNotes;
    private String registrationStatus; // CONFIRMED, CANCELLED
    private Long registeredAt;

    // Denormalized event info for convenient display in "Events I Registered For"
    private String eventTitle;
    private Long eventDate;
    private String eventVenue;
    private String eventCity;
    private String eventFee;

    /**
     * Required default constructor for Cloud Firestore deserialization.
     */
    public EventRegistration() {
        this.registrationStatus = STATUS_CONFIRMED;
        this.registeredAt = System.currentTimeMillis();
    }

    public EventRegistration(String eventId, String userId, String participantName,
                             String email, String phone, String petId, String petName,
                             String petSpecies, String petAge, String additionalNotes,
                             String eventTitle, Long eventDate, String eventVenue,
                             String eventCity, String eventFee) {
        this();
        this.registrationId = userId;
        this.eventId = eventId;
        this.userId = userId;
        this.participantName = participantName;
        this.email = email;
        this.phone = phone;
        this.petId = petId;
        this.petName = petName;
        this.petSpecies = petSpecies;
        this.petAge = petAge;
        this.additionalNotes = additionalNotes;
        this.eventTitle = eventTitle;
        this.eventDate = eventDate;
        this.eventVenue = eventVenue;
        this.eventCity = eventCity;
        this.eventFee = eventFee;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getParticipantName() {
        return participantName;
    }

    public void setParticipantName(String participantName) {
        this.participantName = participantName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPetId() {
        return petId;
    }

    public void setPetId(String petId) {
        this.petId = petId;
    }

    public String getPetName() {
        return petName;
    }

    public void setPetName(String petName) {
        this.petName = petName;
    }

    public String getPetSpecies() {
        return petSpecies;
    }

    public void setPetSpecies(String petSpecies) {
        this.petSpecies = petSpecies;
    }

    public String getPetAge() {
        return petAge;
    }

    public void setPetAge(String petAge) {
        this.petAge = petAge;
    }

    public String getAdditionalNotes() {
        return additionalNotes;
    }

    public void setAdditionalNotes(String additionalNotes) {
        this.additionalNotes = additionalNotes;
    }

    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    public Long getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Long registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public Long getEventDate() {
        return eventDate;
    }

    public void setEventDate(Long eventDate) {
        this.eventDate = eventDate;
    }

    public String getEventVenue() {
        return eventVenue;
    }

    public void setEventVenue(String eventVenue) {
        this.eventVenue = eventVenue;
    }

    public String getEventCity() {
        return eventCity;
    }

    public void setEventCity(String eventCity) {
        this.eventCity = eventCity;
    }

    public String getEventFee() {
        return eventFee;
    }

    public void setEventFee(String eventFee) {
        this.eventFee = eventFee;
    }

    @Exclude
    @Nullable
    public String getFormattedEventDate() {
        if (eventDate == null || eventDate <= 0) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
        return sdf.format(new Date(eventDate));
    }

    @Exclude
    @Nullable
    public String getFormattedRegistrationTime() {
        if (registeredAt == null || registeredAt <= 0) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
        return sdf.format(new Date(registeredAt));
    }
}
