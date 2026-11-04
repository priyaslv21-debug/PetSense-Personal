package com.example.pet.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BroadcastReceiver triggered by AlarmManager when a scheduled reminder alarm fires.
 * Displays the system notification with action buttons even when the app is closed.
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String reminderId = intent.getStringExtra(ReminderNotificationHelper.EXTRA_REMINDER_ID);
        String title = intent.getStringExtra(ReminderNotificationHelper.EXTRA_REMINDER_TITLE);

        Log.i(TAG, "Alarm received for reminder ID: " + reminderId + " (" + title + ")");

        // Display high-priority notification with sound and actions
        ReminderNotificationHelper.showNotification(context, intent);
    }
}
