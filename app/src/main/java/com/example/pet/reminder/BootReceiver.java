package com.example.pet.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.pet.data.Reminder;
import com.example.pet.data.ReminderRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

/**
 * Reschedules all active, pending pet care reminders upon:
 * - Device boot (BOOT_COMPLETED)
 * - Application update (MY_PACKAGE_REPLACED)
 * - Timezone change (TIMEZONE_CHANGED)
 * - Clock adjustment (TIME_SET)
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        Log.i(TAG, "BootReceiver received action: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)) {

            // Only reschedule if a user is currently authenticated
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Log.i(TAG, "No authenticated user. Skipping alarm restoration.");
                return;
            }

            ReminderNotificationHelper.createNotificationChannel(context);

            ReminderRepository repository = new ReminderRepository();
            repository.loadAllRemindersForUser(new ReminderRepository.RemindersCallback() {
                @Override
                public void onSuccess(List<Reminder> reminders) {
                    int rescheduledCount = 0;
                    long now = System.currentTimeMillis();

                    for (Reminder r : reminders) {
                        if (r.isEnabled() && !Reminder.STATUS_COMPLETED.equalsIgnoreCase(r.getStatus())) {
                            // If timestamp is still in future, or if repeating
                            if (r.getScheduledTimestamp() > now || !Reminder.FREQ_NONE.equalsIgnoreCase(r.getRepeatFrequency())) {
                                ReminderNotificationHelper.scheduleAlarm(context, r);
                                rescheduledCount++;
                            }
                        }
                    }
                    Log.i(TAG, "Successfully restored and rescheduled " + rescheduledCount + " reminder alarms.");
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to restore reminders on reboot", e);
                }
            });
        }
    }
}
