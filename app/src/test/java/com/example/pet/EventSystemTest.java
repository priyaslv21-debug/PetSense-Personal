package com.example.pet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.pet.data.Event;
import com.example.pet.data.EventRegistration;
import com.example.pet.data.EventReport;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests validating:
 * 1. Event POJO integrity, setters/getters, default status.
 * 2. Capacity management, available seats calculation, and isFull() enforcement.
 * 3. Date, past event, and deadline validation logic.
 * 4. Free vs. paid event fee determination.
 * 5. Supported community event category constants.
 * 6. Organizer verification and safety badge logic.
 * 7. EventRegistration POJO and status handling.
 * 8. EventReport POJO and safety reporting integrity.
 * 9. Multi-criteria event list filtering by category and search query.
 */
public class EventSystemTest {

    @Test
    public void testEventDefaultsAndSetters() {
        Event event = new Event();
        assertNotNull(event);
        assertEquals(Event.STATUS_ACTIVE, event.getEventStatus());
        assertEquals(0, event.getRegistrationCount());
        assertNotNull(event.getCreatedAt());

        event.setEventId("evt_101");
        event.setTitle("Paws in the Park - Annual Meetup");
        event.setCategory(Event.CATEGORY_MEETUP);
        event.setDescription("Friendly social dog gathering at the public park.");
        event.setOrganizerId("user_xyz");
        event.setOrganizerName("Central Bark Club");
        event.setOrganizerContact("meetup@barkclub.org");
        event.setVenue("Riverside Meadow");
        event.setAddress("450 Parkside Ave");
        event.setCity("Seattle");
        event.setMaximumParticipants(40);
        event.setEventFee("Free");

        assertEquals("evt_101", event.getEventId());
        assertEquals("Paws in the Park - Annual Meetup", event.getTitle());
        assertEquals(Event.CATEGORY_MEETUP, event.getCategory());
        assertEquals("user_xyz", event.getOrganizerId());
        assertEquals("Central Bark Club", event.getOrganizerName());
        assertEquals("Seattle", event.getCity());
        assertEquals(40, event.getMaximumParticipants());
        assertTrue(event.isFree());
    }

    @Test
    public void testCapacityAndRemainingSeatsCalculation() {
        Event event = new Event();
        event.setMaximumParticipants(50);
        event.setRegistrationCount(15);

        assertFalse(event.isFull());
        assertEquals(35, event.getAvailableSeats());

        // Fill to capacity
        event.setRegistrationCount(50);
        assertTrue(event.isFull());
        assertEquals(0, event.getAvailableSeats());

        // Overflow edge-case defense
        event.setRegistrationCount(52);
        assertTrue(event.isFull());
        assertEquals(0, event.getAvailableSeats());

        // Unlimited capacity (0 max participants)
        Event openEvent = new Event();
        openEvent.setMaximumParticipants(0);
        openEvent.setRegistrationCount(120);
        assertFalse(openEvent.isFull());
        assertEquals(Integer.MAX_VALUE, openEvent.getAvailableSeats());
    }

    @Test
    public void testDateAndDeadlineValidation() {
        long now = System.currentTimeMillis();
        long twoDaysFuture = now + (2 * 24 * 3600 * 1000L);
        long oneDayPast = now - (24 * 3600 * 1000L);

        // Future event and deadline
        Event upcomingEvent = new Event();
        upcomingEvent.setEventDate(twoDaysFuture);
        upcomingEvent.setRegistrationDeadline(twoDaysFuture - (6 * 3600 * 1000L));

        assertFalse(upcomingEvent.isPastEvent());
        assertFalse(upcomingEvent.isPastDeadline());

        // Past event
        Event pastEvent = new Event();
        pastEvent.setEventDate(oneDayPast);
        pastEvent.setRegistrationDeadline(oneDayPast);

        assertTrue(pastEvent.isPastEvent());
        assertTrue(pastEvent.isPastDeadline());

        // Event in future, but registration deadline has passed
        Event closedEvent = new Event();
        closedEvent.setEventDate(twoDaysFuture);
        closedEvent.setRegistrationDeadline(oneDayPast);

        assertFalse(closedEvent.isPastEvent());
        assertTrue(closedEvent.isPastDeadline());
    }

    @Test
    public void testEventFeeDetermination() {
        Event event = new Event();

        event.setEventFee("Free");
        assertTrue(event.isFree());

        event.setEventFee("FREE");
        assertTrue(event.isFree());

        event.setEventFee("0");
        assertTrue(event.isFree());

        event.setEventFee("0.00");
        assertTrue(event.isFree());

        event.setEventFee("");
        assertTrue(event.isFree());

        event.setEventFee(null);
        assertTrue(event.isFree());

        event.setEventFee("$10.00");
        assertFalse(event.isFree());

        event.setEventFee("25 USD");
        assertFalse(event.isFree());
    }

    @Test
    public void testEventCategories() {
        assertEquals("Vaccination Camps", Event.CATEGORY_VACCINATION);
        assertEquals("Pet Meetups", Event.CATEGORY_MEETUP);
        assertEquals("Training Workshops", Event.CATEGORY_TRAINING);
        assertEquals("Health Check-up Camps", Event.CATEGORY_HEALTH_CHECKUP);
        assertEquals("Pet Adoption Events", Event.CATEGORY_ADOPTION);
        assertEquals("Pet Awareness Programs", Event.CATEGORY_AWARENESS);
        assertEquals("Other Pet-related Events", Event.CATEGORY_OTHER);

        assertEquals(7, Event.CATEGORIES.length);
        assertEquals(Event.CATEGORY_VACCINATION, Event.CATEGORIES[0]);
        assertEquals(Event.CATEGORY_MEETUP, Event.CATEGORIES[1]);
        assertEquals(Event.CATEGORY_TRAINING, Event.CATEGORIES[2]);
        assertEquals(Event.CATEGORY_HEALTH_CHECKUP, Event.CATEGORIES[3]);
        assertEquals(Event.CATEGORY_ADOPTION, Event.CATEGORIES[4]);
        assertEquals(Event.CATEGORY_AWARENESS, Event.CATEGORIES[5]);
        assertEquals(Event.CATEGORY_OTHER, Event.CATEGORIES[6]);
    }

    @Test
    public void testOrganizerVerificationAndSafetyBadge() {
        Event unverified = new Event();
        assertFalse(unverified.isOrganizerVerified());

        unverified.setOrganizerVerified(true);
        assertTrue(unverified.isOrganizerVerified());

        unverified.setOrganizerVerified(false);
        assertFalse(unverified.isOrganizerVerified());
    }

    @Test
    public void testEventRegistrationModel() {
        EventRegistration reg = new EventRegistration();
        assertNotNull(reg);
        assertEquals(EventRegistration.STATUS_CONFIRMED, reg.getRegistrationStatus());
        assertNotNull(reg.getRegisteredAt());

        reg.setRegistrationId("user_42");
        reg.setEventId("evt_99");
        reg.setUserId("user_42");
        reg.setParticipantName("Samantha Ray");
        reg.setEmail("samantha@petsense.test");
        reg.setPhone("555-0199");
        reg.setPetId("pet_bella");
        reg.setPetName("Bella");
        reg.setPetSpecies("Dog");
        reg.setPetAge("3 years");
        reg.setAdditionalNotes("Please ensure allergen-free treats");

        assertEquals("evt_99", reg.getEventId());
        assertEquals("user_42", reg.getUserId());
        assertEquals("Samantha Ray", reg.getParticipantName());
        assertEquals("samantha@petsense.test", reg.getEmail());
        assertEquals("555-0199", reg.getPhone());
        assertEquals("Bella", reg.getPetName());
        assertEquals("Dog", reg.getPetSpecies());
        assertEquals("3 years", reg.getPetAge());
        assertEquals("Please ensure allergen-free treats", reg.getAdditionalNotes());
        assertEquals(EventRegistration.STATUS_CONFIRMED, reg.getRegistrationStatus());

        reg.setRegistrationStatus(EventRegistration.STATUS_CANCELLED);
        assertEquals(EventRegistration.STATUS_CANCELLED, reg.getRegistrationStatus());
    }

    @Test
    public void testEventReportModel() {
        EventReport report = new EventReport(
                "rep_001",
                "evt_99",
                "Spurious Vaccination Camp",
                "user_whistleblower",
                "whistle@petsense.test",
                "Unverified medical claims",
                "Organizer does not have licensed vet supervision."
        );

        assertEquals("rep_001", report.getReportId());
        assertEquals("evt_99", report.getEventId());
        assertEquals("Spurious Vaccination Camp", report.getEventTitle());
        assertEquals("user_whistleblower", report.getReporterId());
        assertEquals("whistle@petsense.test", report.getReporterEmail());
        assertEquals("Unverified medical claims", report.getReason());
        assertEquals(EventReport.STATUS_PENDING, report.getStatus());
        assertNotNull(report.getCreatedAt());
    }

    @Test
    public void testEventFilteringLogic() {
        List<Event> allEvents = new ArrayList<>();

        Event e1 = new Event();
        e1.setTitle("Downtown Puppy Social");
        e1.setCategory(Event.CATEGORY_MEETUP);
        e1.setCity("Austin");
        e1.setVenue("Zilker Park");
        e1.setOrganizerName("Austin Paws");
        allEvents.add(e1);

        Event e2 = new Event();
        e2.setTitle("Rabies & Core Vaccination Clinic");
        e2.setCategory(Event.CATEGORY_VACCINATION);
        e2.setCity("Austin");
        e2.setVenue("Community Center");
        e2.setOrganizerName("Dr. Miller Vet");
        allEvents.add(e2);

        Event e3 = new Event();
        e3.setTitle("Agility & Obedience Workshop");
        e3.setCategory(Event.CATEGORY_TRAINING);
        e3.setCity("Dallas");
        e3.setVenue("Fair Park Canine Hall");
        e3.setOrganizerName("Canine Academy");
        allEvents.add(e3);

        // Filter by Category
        List<Event> meetups = new ArrayList<>();
        for (Event e : allEvents) {
            if (Event.CATEGORY_MEETUP.equalsIgnoreCase(e.getCategory())) {
                meetups.add(e);
            }
        }
        assertEquals(1, meetups.size());
        assertEquals("Downtown Puppy Social", meetups.get(0).getTitle());

        // Filter by City Search Query "Austin"
        String query = "austin";
        List<Event> austinEvents = new ArrayList<>();
        for (Event e : allEvents) {
            boolean matches = (e.getTitle() != null && e.getTitle().toLowerCase().contains(query)) ||
                    (e.getCity() != null && e.getCity().toLowerCase().contains(query)) ||
                    (e.getVenue() != null && e.getVenue().toLowerCase().contains(query));
            if (matches) {
                austinEvents.add(e);
            }
        }
        assertEquals(2, austinEvents.size());
    }
}
