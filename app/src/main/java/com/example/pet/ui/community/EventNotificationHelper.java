package com.example.pet.ui.community;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.pet.R;
import com.example.pet.data.Event;
import com.example.pet.data.EventRegistration;

/**
 * Helper managing Android system notifications for PetSense Community & Events:
 * 1. Event registration confirmation notification.
 * 2. Event cancellation alerts.
 * 3. Organizer notifications on new registrations.
 */
public class EventNotificationHelper {

    private static final String TAG = "EventNotificationHelper";

    public static final String CHANNEL_ID = "petsense_community_events";
    public static final String CHANNEL_NAME = "PetSense Community Events";
    public static final String CHANNEL_DESC = "Notifications for event registrations, cancellations, and pet meetup updates";

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
                channel.enableVibration(true);
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void notifyRegistrationSuccess(@NonNull Context context,
                                                @NonNull EventRegistration registration,
                                                @NonNull Event event) {
        notifyRegistrationSuccess(context, event, registration);
    }

    public static void notifyRegistrationSuccess(@NonNull Context context,
                                                @NonNull Event event,
                                                @NonNull EventRegistration registration) {
        if (!hasNotificationPermission(context)) return;
        createNotificationChannel(context);

        Intent intent = new Intent(context, EventDetailsActivity.class);
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                event.getEventId().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        String petName = registration.getPetName() != null ? registration.getPetName() : "your pet";
        String dateStr = event.getFormattedDate() != null ? event.getFormattedDate() : "upcoming date";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ps_paw)
                .setContentTitle("Event Registration Confirmed! 🎉")
                .setContentText(petName + " is registered for '" + event.getTitle() + "' on " + dateStr)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("You and " + petName + " are confirmed for " + event.getTitle()
                                + ".\nVenue: " + (event.getVenue() != null ? event.getVenue() : event.getCity())
                                + "\nTime: " + (event.getStartTime() != null ? event.getStartTime() : "TBD")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(ContextCompat.getColor(context, R.color.pd_primary))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(event.getEventId().hashCode(), builder.build());
        }
    }

    public static void notifyEventCancelled(@NonNull Context context, @NonNull Event event) {
        if (!hasNotificationPermission(context)) return;
        createNotificationChannel(context);

        Intent intent = new Intent(context, EventDetailsActivity.class);
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (event.getEventId() + "_cancelled").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ps_paw)
                .setContentTitle("Event Cancelled")
                .setContentText("The event '" + event.getTitle() + "' has been cancelled by the organizer.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(ContextCompat.getColor(context, R.color.pd_red_text))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((event.getEventId() + "_cancelled").hashCode(), builder.build());
        }
    }

    private static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}
