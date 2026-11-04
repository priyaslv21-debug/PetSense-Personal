package com.example.pet.reminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.pet.R;
import com.example.pet.data.Reminder;
import com.example.pet.ui.insights.InsightsActivity;

/**
 * Handles notification channels, exact AlarmManager scheduling,
 * and high-priority notification generation for PetSense reminders.
 */
public class ReminderNotificationHelper {

    private static final String TAG = "ReminderHelper";

    public static final String CHANNEL_ID = "petsense_care_reminders";
    public static final String CHANNEL_NAME = "Pet Care Reminders";
    public static final String CHANNEL_DESC = "Notifications for pet vaccinations, medications, vet check-ups, and deworming";

    public static final String EXTRA_REMINDER_ID = "extra_reminder_id";
    public static final String EXTRA_PET_ID = "extra_pet_id";
    public static final String EXTRA_PET_NAME = "extra_pet_name";
    public static final String EXTRA_REMINDER_TITLE = "extra_reminder_title";
    public static final String EXTRA_REMINDER_TYPE = "extra_reminder_type";
    public static final String EXTRA_REMINDER_DESC = "extra_reminder_desc";
    public static final String EXTRA_MED_NAME = "extra_med_name";

    public static final String ACTION_COMPLETE = "com.example.pet.ACTION_COMPLETE_REMINDER";
    public static final String ACTION_SNOOZE = "com.example.pet.ACTION_SNOOZE_REMINDER";

    private static final String PREFS_NAME = "petsense_reminders_prefs";

    /** Creates notification channel on Android 8.0+ (API 26+) */
    public static void createNotificationChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(CHANNEL_DESC);
                channel.enableLights(true);
                channel.setLightColor(Color.MAGENTA);
                channel.enableVibration(true);
                channel.setShowBadge(true);
                manager.createNotificationChannel(channel);
            }
        }
    }

    /** Checks whether app has permission to schedule exact alarms (API 31+) */
    public static boolean canScheduleExactAlarms(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true;
    }

    /** Checks whether POST_NOTIFICATIONS is granted on Android 13+ (API 33+) */
    public static boolean hasNotificationPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /** Schedules a reliable alarm using AlarmManager */
    public static void scheduleAlarm(@NonNull Context context, @NonNull Reminder reminder) {
        if (!reminder.isEnabled() || Reminder.STATUS_COMPLETED.equalsIgnoreCase(reminder.getStatus())) {
            cancelAlarm(context, reminder.getReminderId());
            return;
        }

        long triggerTimestamp = reminder.calculateAlarmTriggerTimestamp();
        long now = System.currentTimeMillis();

        if (triggerTimestamp <= now) {
            // If already overdue or past, and recurring, calculate next cycle
            if (!Reminder.FREQ_NONE.equalsIgnoreCase(reminder.getRepeatFrequency())) {
                while (triggerTimestamp <= now) {
                    triggerTimestamp = reminder.computeNextScheduledTimestamp();
                    reminder.setScheduledTimestamp(triggerTimestamp);
                }
            } else {
                Log.w(TAG, "Not scheduling past non-recurring alarm for: " + reminder.getTitle());
                return;
            }
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager unavailable");
            return;
        }

        int requestCode = getRequestCode(reminder.getReminderId());
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(EXTRA_REMINDER_ID, reminder.getReminderId());
        intent.putExtra(EXTRA_PET_ID, reminder.getPetId());
        intent.putExtra(EXTRA_PET_NAME, reminder.getPetName());
        intent.putExtra(EXTRA_REMINDER_TITLE, reminder.getTitle());
        intent.putExtra(EXTRA_REMINDER_TYPE, reminder.getReminderType());
        intent.putExtra(EXTRA_REMINDER_DESC, reminder.getDescription());
        intent.putExtra(EXTRA_MED_NAME, reminder.getMedicationName());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimestamp, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimestamp, pendingIntent);
            }

            // Save scheduled alarm ID to preferences for boot restoration
            saveScheduledReminderId(context, reminder.getReminderId(), reminder.getPetId());
            Log.i(TAG, "Scheduled alarm for '" + reminder.getTitle() + "' at millis: " + triggerTimestamp);
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException scheduling exact alarm. Falling back to set()", se);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimestamp, pendingIntent);
        }
    }

    /** Cancels a scheduled alarm */
    public static void cancelAlarm(@NonNull Context context, @NonNull String reminderId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            int requestCode = getRequestCode(reminderId);
            Intent intent = new Intent(context, ReminderReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }
        removeScheduledReminderId(context, reminderId);
        Log.i(TAG, "Cancelled alarm for reminder ID: " + reminderId);
    }

    /** Displays the high-priority system notification with quick actions */
    public static void showNotification(@NonNull Context context, @NonNull Intent reminderData) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Cannot post notification: POST_NOTIFICATIONS permission not granted");
            return;
        }

        createNotificationChannel(context);

        String reminderId = reminderData.getStringExtra(EXTRA_REMINDER_ID);
        String petId = reminderData.getStringExtra(EXTRA_PET_ID);
        String petName = reminderData.getStringExtra(EXTRA_PET_NAME);
        String title = reminderData.getStringExtra(EXTRA_REMINDER_TITLE);
        String type = reminderData.getStringExtra(EXTRA_REMINDER_TYPE);
        String desc = reminderData.getStringExtra(EXTRA_REMINDER_DESC);
        String medName = reminderData.getStringExtra(EXTRA_MED_NAME);

        if (petName == null || petName.isEmpty()) petName = "Your pet";
        if (title == null || title.isEmpty()) title = "Care Reminder";

        String notifTitle;
        String notifBody;

        if (Reminder.TYPE_VACCINATION.equalsIgnoreCase(type)) {
            notifTitle = "\uD83D\uDC3E Vaccination Reminder: " + petName;
            notifBody = petName + "'s vaccination (" + title + ") is due.";
        } else if (Reminder.TYPE_MEDICATION.equalsIgnoreCase(type)) {
            notifTitle = "\uD83D\uDC8A Medication Reminder: " + petName;
            String med = (medName != null && !medName.isEmpty()) ? medName : title;
            notifBody = "It is time for " + petName + "'s scheduled medication: " + med + ".";
        } else if (Reminder.TYPE_CHECKUP.equalsIgnoreCase(type)) {
            notifTitle = "\uD83E\uDE7A Health Check-up: " + petName;
            notifBody = petName + " has a veterinary appointment scheduled today.";
        } else if (Reminder.TYPE_DEWORMING.equalsIgnoreCase(type)) {
            notifTitle = "\uD83D\uDEE1\uFE0F Deworming Reminder: " + petName;
            notifBody = petName + "'s deworming treatment (" + title + ") is due.";
        } else {
            notifTitle = "\uD83D\uDC3E Pet Care Reminder: " + petName;
            notifBody = title + (desc != null && !desc.isEmpty() ? " \u00b7 " + desc : "");
        }

        // Tap notification -> Opens InsightsActivity for the specific pet
        Intent tapIntent = new Intent(context, InsightsActivity.class);
        tapIntent.putExtra("petId", petId);
        tapIntent.putExtra("petName", petName);
        tapIntent.putExtra("target_section", "reminders");
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int notifId = (reminderId != null) ? getRequestCode(reminderId) : (int) System.currentTimeMillis();

        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action 1: Mark as Done
        Intent completeIntent = new Intent(context, ReminderActionReceiver.class);
        completeIntent.setAction(ACTION_COMPLETE);
        completeIntent.putExtra(EXTRA_REMINDER_ID, reminderId);
        completeIntent.putExtra(EXTRA_PET_ID, petId);

        PendingIntent completePendingIntent = PendingIntent.getBroadcast(
                context,
                notifId + 1,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action 2: Snooze 1 Hour
        Intent snoozeIntent = new Intent(context, ReminderActionReceiver.class);
        snoozeIntent.setAction(ACTION_SNOOZE);
        snoozeIntent.putExtra(EXTRA_REMINDER_ID, reminderId);
        snoozeIntent.putExtra(EXTRA_PET_ID, petId);

        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                notifId + 2,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ps_notifications)
                .setContentTitle(notifTitle)
                .setContentText(notifBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notifBody + (desc != null && !desc.isEmpty() ? "\n" + desc : "")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(context, R.color.ps_primary))
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.ic_ps_check, "Mark as Done", completePendingIntent)
                .addAction(R.drawable.ic_ps_refresh, "Snooze 1h", snoozePendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notifId, builder.build());
            Log.i(TAG, "Notification displayed for reminder: " + title);
        }
    }

    public static int getRequestCode(@NonNull String id) {
        return Math.abs(id.hashCode());
    }

    private static void saveScheduledReminderId(Context context, String reminderId, String petId) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(reminderId, petId).apply();
    }

    private static void removeScheduledReminderId(Context context, String reminderId) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(reminderId).apply();
    }
}
