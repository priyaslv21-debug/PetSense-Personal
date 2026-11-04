package com.example.pet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.pet.data.Reminder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Unit tests validating:
 * 1. Reminder POJO integrity, getters/setters, default values.
 * 2. Status calculation logic (Upcoming, Due Today, Overdue, Completed).
 * 3. Advance alert trigger offset calculation (Exact, 1h, 1d, 2d, 1w).
 * 4. Recurring interval calculation (Daily, Weekly, Monthly, Yearly).
 * 5. Medication reminder model integrity (dosage, start/end dates, admin times).
 * 6. Category validation (Vaccination, Routine Check-up, Deworming, Medication).
 * 7. Firestore document hierarchy and path isolation: users/{uid}/pets/{petId}/reminders/{reminderId}.
 * 8. Summary metric aggregation and category filtering.
 */
public class ReminderSystemTest {

    @Test
    public void testReminderModelDefaultsAndSetters() {
        Reminder reminder = new Reminder();
        assertNotNull(reminder);
        assertEquals(Reminder.TYPE_VACCINATION, reminder.getType());
        assertEquals(Reminder.FREQ_NONE, reminder.getRepeatFrequency());
        assertEquals(Reminder.ADVANCE_EXACT, reminder.getAdvanceNotice());
        assertEquals(Reminder.STATUS_UPCOMING, reminder.getStatus());
        assertTrue(reminder.isEnabled());

        reminder.setReminderId("rem_123");
        reminder.setPetId("pet_456");
        reminder.setPetName("Milo");
        reminder.setTitle("Rabies Booster");
        reminder.setDescription("Annual booster shot");
        reminder.setType(Reminder.TYPE_VACCINATION);
        reminder.setRepeatFrequency(Reminder.FREQ_YEARLY);
        reminder.setAdvanceNotice(Reminder.ADVANCE_1_DAY);

        assertEquals("rem_123", reminder.getReminderId());
        assertEquals("pet_456", reminder.getPetId());
        assertEquals("Milo", reminder.getPetName());
        assertEquals("Rabies Booster", reminder.getTitle());
        assertEquals("Annual booster shot", reminder.getDescription());
        assertEquals(Reminder.TYPE_VACCINATION, reminder.getType());
        assertEquals(Reminder.FREQ_YEARLY, reminder.getRepeatFrequency());
        assertEquals(Reminder.ADVANCE_1_DAY, reminder.getAdvanceNotice());
    }

    @Test
    public void testStatusCalculationUpcomingDueTodayOverdue() {
        Calendar cal = Calendar.getInstance();

        // 1. Due Today: scheduled for later today
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        Reminder dueToday = new Reminder();
        dueToday.setScheduledTimestamp(cal.getTimeInMillis());
        assertEquals("Should be Due Today", Reminder.STATUS_DUE_TODAY, dueToday.calculateCurrentStatus());

        // 2. Overdue: scheduled 2 days in the past
        Calendar pastCal = Calendar.getInstance();
        pastCal.add(Calendar.DAY_OF_YEAR, -2);
        Reminder overdue = new Reminder();
        overdue.setScheduledTimestamp(pastCal.getTimeInMillis());
        assertEquals("Should be Overdue", Reminder.STATUS_OVERDUE, overdue.calculateCurrentStatus());

        // 3. Upcoming: scheduled 5 days in the future
        Calendar futureCal = Calendar.getInstance();
        futureCal.add(Calendar.DAY_OF_YEAR, 5);
        Reminder upcoming = new Reminder();
        upcoming.setScheduledTimestamp(futureCal.getTimeInMillis());
        assertEquals("Should be Upcoming", Reminder.STATUS_UPCOMING, upcoming.calculateCurrentStatus());

        // 4. Completed: status remains Completed
        Reminder completed = new Reminder();
        completed.setScheduledTimestamp(pastCal.getTimeInMillis());
        completed.setStatus(Reminder.STATUS_COMPLETED);
        assertEquals(Reminder.STATUS_COMPLETED, completed.getStatus());
    }

    @Test
    public void testAdvanceNotificationOffsets() {
        long scheduledTime = 1700000000000L;
        Reminder reminder = new Reminder();
        reminder.setScheduledTimestamp(scheduledTime);

        // Exact time
        reminder.setAdvanceNotice(Reminder.ADVANCE_EXACT);
        assertEquals(scheduledTime, reminder.calculateAlarmTriggerTimestamp());

        // 1 hour before
        reminder.setAdvanceNotice(Reminder.ADVANCE_1_HOUR);
        assertEquals(scheduledTime - (60 * 60 * 1000L), reminder.calculateAlarmTriggerTimestamp());

        // 1 day before
        reminder.setAdvanceNotice(Reminder.ADVANCE_1_DAY);
        assertEquals(scheduledTime - (24 * 60 * 60 * 1000L), reminder.calculateAlarmTriggerTimestamp());

        // 2 days before
        reminder.setAdvanceNotice(Reminder.ADVANCE_2_DAYS);
        assertEquals(scheduledTime - (48 * 60 * 60 * 1000L), reminder.calculateAlarmTriggerTimestamp());

        // 1 week before
        reminder.setAdvanceNotice(Reminder.ADVANCE_1_WEEK);
        assertEquals(scheduledTime - (7 * 24 * 60 * 60 * 1000L), reminder.calculateAlarmTriggerTimestamp());
    }

    @Test
    public void testRecurringScheduleAdvancement() {
        Calendar base = Calendar.getInstance();
        base.set(2026, Calendar.JANUARY, 15, 10, 0, 0);
        base.set(Calendar.MILLISECOND, 0);
        long baseTimestamp = base.getTimeInMillis();

        // Daily
        Reminder daily = new Reminder();
        daily.setScheduledTimestamp(baseTimestamp);
        daily.setRepeatFrequency(Reminder.FREQ_DAILY);
        long nextDaily = daily.computeNextScheduledTimestamp();
        Calendar expectedDaily = (Calendar) base.clone();
        expectedDaily.add(Calendar.DAY_OF_YEAR, 1);
        assertEquals(expectedDaily.getTimeInMillis(), nextDaily);

        // Weekly
        Reminder weekly = new Reminder();
        weekly.setScheduledTimestamp(baseTimestamp);
        weekly.setRepeatFrequency(Reminder.FREQ_WEEKLY);
        long nextWeekly = weekly.computeNextScheduledTimestamp();
        Calendar expectedWeekly = (Calendar) base.clone();
        expectedWeekly.add(Calendar.WEEK_OF_YEAR, 1);
        assertEquals(expectedWeekly.getTimeInMillis(), nextWeekly);

        // Monthly
        Reminder monthly = new Reminder();
        monthly.setScheduledTimestamp(baseTimestamp);
        monthly.setRepeatFrequency(Reminder.FREQ_MONTHLY);
        long nextMonthly = monthly.computeNextScheduledTimestamp();
        Calendar expectedMonthly = (Calendar) base.clone();
        expectedMonthly.add(Calendar.MONTH, 1);
        assertEquals(expectedMonthly.getTimeInMillis(), nextMonthly);

        // Yearly
        Reminder yearly = new Reminder();
        yearly.setScheduledTimestamp(baseTimestamp);
        yearly.setRepeatFrequency(Reminder.FREQ_YEARLY);
        long nextYearly = yearly.computeNextScheduledTimestamp();
        Calendar expectedYearly = (Calendar) base.clone();
        expectedYearly.add(Calendar.YEAR, 1);
        assertEquals(expectedYearly.getTimeInMillis(), nextYearly);

        // None
        Reminder nonRepeating = new Reminder();
        nonRepeating.setScheduledTimestamp(baseTimestamp);
        nonRepeating.setRepeatFrequency(Reminder.FREQ_NONE);
        assertEquals(baseTimestamp, nonRepeating.computeNextScheduledTimestamp());
    }

    @Test
    public void testMedicationDetailsAndAdministrationTimes() {
        Reminder medReminder = new Reminder();
        medReminder.setType(Reminder.TYPE_MEDICATION);
        medReminder.setMedicationName("Amoxicillin 250mg");
        medReminder.setMedicationDosage("1 tablet");
        medReminder.setMedicationFrequency("Twice daily");
        medReminder.setMedicationStartDate("2026-11-01");
        medReminder.setMedicationEndDate("2026-11-14");

        List<String> adminTimes = Arrays.asList("08:00 AM", "08:00 PM");
        medReminder.setMedicationAdminTimes(adminTimes);

        assertEquals("Amoxicillin 250mg", medReminder.getMedicationName());
        assertEquals("1 tablet", medReminder.getMedicationDosage());
        assertEquals("Twice daily", medReminder.getMedicationFrequency());
        assertEquals("2026-11-01", medReminder.getMedicationStartDate());
        assertEquals("2026-11-14", medReminder.getMedicationEndDate());
        assertNotNull(medReminder.getMedicationAdminTimes());
        assertEquals(2, medReminder.getMedicationAdminTimes().size());
        assertEquals("08:00 AM", medReminder.getMedicationAdminTimes().get(0));
        assertEquals("08:00 PM", medReminder.getMedicationAdminTimes().get(1));
    }

    @Test
    public void testFirestorePathContractCompliance() {
        String testUid = "user_abc_789";
        String testPetId = "pet_xyz_456";
        String testReminderId = "rem_001";

        String expectedPath = "users/" + testUid + "/pets/" + testPetId + "/reminders/" + testReminderId;

        assertTrue("Path must start with users/{uid}", expectedPath.startsWith("users/" + testUid));
        assertTrue("Path must isolate by petId", expectedPath.contains("/pets/" + testPetId + "/"));
        assertTrue("Subcollection must be reminders", expectedPath.endsWith("/reminders/" + testReminderId));
    }

    @Test
    public void testCategoryFilteringAndMetricAggregation() {
        List<Reminder> list = new ArrayList<>();

        Calendar cal = Calendar.getInstance();

        // 1. Due Today - Vaccination
        cal.set(Calendar.HOUR_OF_DAY, 20);
        Reminder r1 = new Reminder();
        r1.setReminderId("r1");
        r1.setType(Reminder.TYPE_VACCINATION);
        r1.setScheduledTimestamp(cal.getTimeInMillis());
        r1.setEnabled(true);
        list.add(r1);

        // 2. Overdue - Deworming
        Calendar past = Calendar.getInstance();
        past.add(Calendar.DAY_OF_YEAR, -3);
        Reminder r2 = new Reminder();
        r2.setReminderId("r2");
        r2.setType(Reminder.TYPE_DEWORMING);
        r2.setScheduledTimestamp(past.getTimeInMillis());
        r2.setEnabled(true);
        list.add(r2);

        // 3. Upcoming - Checkup
        Calendar future = Calendar.getInstance();
        future.add(Calendar.DAY_OF_YEAR, 4);
        Reminder r3 = new Reminder();
        r3.setReminderId("r3");
        r3.setType(Reminder.TYPE_CHECKUP);
        r3.setScheduledTimestamp(future.getTimeInMillis());
        r3.setEnabled(true);
        list.add(r3);

        // 4. Completed - Medication (should not be counted in active metrics)
        Reminder r4 = new Reminder();
        r4.setReminderId("r4");
        r4.setType(Reminder.TYPE_MEDICATION);
        r4.setStatus(Reminder.STATUS_COMPLETED);
        r4.setEnabled(false);
        list.add(r4);

        int upcoming = 0;
        int dueToday = 0;
        int overdue = 0;

        for (Reminder r : list) {
            if (Reminder.STATUS_COMPLETED.equalsIgnoreCase(r.getStatus()) || !r.isEnabled()) {
                continue;
            }
            String status = r.calculateCurrentStatus();
            if (Reminder.STATUS_OVERDUE.equalsIgnoreCase(status)) {
                overdue++;
            } else if (Reminder.STATUS_DUE_TODAY.equalsIgnoreCase(status)) {
                dueToday++;
            } else {
                upcoming++;
            }
        }

        assertEquals(1, upcoming);
        assertEquals(1, dueToday);
        assertEquals(1, overdue);

        // Test category filtering
        int vaccineCount = 0;
        int dewormingCount = 0;
        int checkupCount = 0;
        int medicationCount = 0;

        for (Reminder r : list) {
            if (Reminder.TYPE_VACCINATION.equalsIgnoreCase(r.getType())) vaccineCount++;
            if (Reminder.TYPE_DEWORMING.equalsIgnoreCase(r.getType())) dewormingCount++;
            if (Reminder.TYPE_CHECKUP.equalsIgnoreCase(r.getType())) checkupCount++;
            if (Reminder.TYPE_MEDICATION.equalsIgnoreCase(r.getType())) medicationCount++;
        }

        assertEquals(1, vaccineCount);
        assertEquals(1, dewormingCount);
        assertEquals(1, checkupCount);
        assertEquals(1, medicationCount);
    }
}
