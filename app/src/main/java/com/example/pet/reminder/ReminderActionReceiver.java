package com.example.pet.reminder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.example.pet.data.Reminder;
import com.example.pet.data.ReminderRepository;

import java.util.List;

/**
 * Handles quick notification actions: "Mark as Done" and "Snooze 1h" directly from the notification tray.
 */
public class ReminderActionReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        String reminderId = intent.getStringExtra(ReminderNotificationHelper.EXTRA_REMINDER_ID);
        String petId = intent.getStringExtra(ReminderNotificationHelper.EXTRA_PET_ID);

        if (reminderId == null || petId == null) {
            Log.w(TAG, "Missing reminderId or petId in notification action");
            return;
        }

        // Dismiss the active notification
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(ReminderNotificationHelper.getRequestCode(reminderId));
        }

        ReminderRepository repository = new ReminderRepository();

        if (ReminderNotificationHelper.ACTION_COMPLETE.equals(action)) {
            Log.i(TAG, "Marking reminder completed from notification: " + reminderId);
            repository.loadRemindersForPet(petId, new ReminderRepository.RemindersCallback() {
                @Override
                public void onSuccess(List<Reminder> reminders) {
                    for (Reminder r : reminders) {
                        if (reminderId.equals(r.getReminderId())) {
                            repository.markCompleted(r, new ReminderRepository.SaveReminderCallback() {
                                @Override
                                public void onSuccess(Reminder saved) {
                                    ReminderNotificationHelper.cancelAlarm(context, reminderId);
                                    if (!Reminder.FREQ_NONE.equalsIgnoreCase(saved.getRepeatFrequency())) {
                                        ReminderNotificationHelper.scheduleAlarm(context, saved);
                                    }
                                    Toast.makeText(context, saved.getTitle() + " marked as completed!", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(Exception e) {
                                    Log.e(TAG, "Error marking completed", e);
                                }
                            });
                            break;
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error fetching reminder for completion", e);
                }
            });

        } else if (ReminderNotificationHelper.ACTION_SNOOZE.equals(action)) {
            Log.i(TAG, "Snoozing reminder 1 hour from notification: " + reminderId);
            repository.loadRemindersForPet(petId, new ReminderRepository.RemindersCallback() {
                @Override
                public void onSuccess(List<Reminder> reminders) {
                    for (Reminder r : reminders) {
                        if (reminderId.equals(r.getReminderId())) {
                            long oneHourMillis = 60 * 60 * 1000L;
                            repository.snoozeReminder(r, oneHourMillis, new ReminderRepository.SaveReminderCallback() {
                                @Override
                                public void onSuccess(Reminder snoozed) {
                                    ReminderNotificationHelper.scheduleAlarm(context, snoozed);
                                    Toast.makeText(context, "Snoozed for 1 hour: " + snoozed.getTitle(), Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(Exception e) {
                                    Log.e(TAG, "Error snoozing reminder", e);
                                }
                            });
                            break;
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error fetching reminder for snooze", e);
                }
            });
        }
    }
}
