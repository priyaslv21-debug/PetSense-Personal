package com.example.pet.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles Firestore persistence for pet care reminders under:
 *   users/{uid}/pets/{petId}/reminders/{reminderId}
 */
public class ReminderRepository {

    private static final String TAG = "ReminderRepository";

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    public interface RemindersCallback {
        void onSuccess(List<Reminder> reminders);
        void onError(Exception e);
    }

    public interface SaveReminderCallback {
        void onSuccess(Reminder reminder);
        void onError(Exception e);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public ReminderRepository() {
        try {
            firestore = FirebaseFirestore.getInstance();
            auth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Initialization error in ReminderRepository", e);
        }
    }

    private FirebaseFirestore getFirestore() {
        if (firestore == null) {
            firestore = FirebaseFirestore.getInstance();
        }
        return firestore;
    }

    private FirebaseAuth getAuth() {
        if (auth == null) {
            auth = FirebaseAuth.getInstance();
        }
        return auth;
    }

    private String currentUid() {
        FirebaseAuth a = getAuth();
        if (a == null || a.getCurrentUser() == null) {
            throw new IllegalStateException("ReminderRepository: No signed-in Firebase user.");
        }
        return a.getCurrentUser().getUid();
    }

    private CollectionReference remindersCollection(@NonNull String petId) {
        String uid = currentUid();
        return getFirestore().collection("users")
                .document(uid)
                .collection("pets")
                .document(petId)
                .collection("reminders");
    }

    /** Loads all reminders for a specific pet. */
    public void loadRemindersForPet(@NonNull String petId, @NonNull RemindersCallback callback) {
        try {
            remindersCollection(petId)
                    .orderBy("scheduledTimestamp", Query.Direction.ASCENDING)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<Reminder> reminders = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshot) {
                            Reminder r = doc.toObject(Reminder.class);
                            r.setReminderId(doc.getId());
                            // Update dynamic status based on current time
                            if (!Reminder.STATUS_COMPLETED.equalsIgnoreCase(r.getStatus())) {
                                r.setStatus(r.calculateCurrentStatus());
                            }
                            reminders.add(r);
                        }
                        callback.onSuccess(reminders);
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /** Loads reminders across all pets owned by the current user. */
    public void loadAllRemindersForUser(@NonNull RemindersCallback callback) {
        try {
            String uid = currentUid();
            getFirestore().collection("users").document(uid).collection("pets").get()
                    .addOnSuccessListener(petSnapshots -> {
                        if (petSnapshots.isEmpty()) {
                            callback.onSuccess(new ArrayList<>());
                            return;
                        }

                        List<String> petIds = new ArrayList<>();
                        for (QueryDocumentSnapshot petDoc : petSnapshots) {
                            petIds.add(petDoc.getId());
                        }

                        List<Reminder> allReminders = Collections.synchronizedList(new ArrayList<>());
                        AtomicInteger pendingQueries = new AtomicInteger(petIds.size());

                        for (String petId : petIds) {
                            loadRemindersForPet(petId, new RemindersCallback() {
                                @Override
                                public void onSuccess(List<Reminder> reminders) {
                                    allReminders.addAll(reminders);
                                    if (pendingQueries.decrementAndGet() == 0) {
                                        // Sort all reminders by timestamp ascending
                                        Collections.sort(allReminders, Comparator.comparingLong(Reminder::getScheduledTimestamp));
                                        callback.onSuccess(allReminders);
                                    }
                                }

                                @Override
                                public void onError(Exception e) {
                                    Log.w(TAG, "Failed loading reminders for pet: " + petId, e);
                                    if (pendingQueries.decrementAndGet() == 0) {
                                        Collections.sort(allReminders, Comparator.comparingLong(Reminder::getScheduledTimestamp));
                                        callback.onSuccess(allReminders);
                                    }
                                }
                            });
                        }
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /** Adds a new pet reminder to Firestore. */
    public void addReminder(@NonNull Reminder reminder, @NonNull SaveReminderCallback callback) {
        try {
            if (reminder.getPetId() == null || reminder.getPetId().trim().isEmpty()) {
                callback.onError(new IllegalArgumentException("Pet ID is required to save a reminder."));
                return;
            }

            CollectionReference col = remindersCollection(reminder.getPetId());
            DocumentReference ref = col.document();
            reminder.setReminderId(ref.getId());
            reminder.setCreatedAt(new Date());
            reminder.setUpdatedAt(new Date());

            ref.set(reminder)
                    .addOnSuccessListener(unused -> {
                        Log.i(TAG, "Reminder saved: " + reminder.getReminderId() + " for pet: " + reminder.getPetId());
                        callback.onSuccess(reminder);
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /** Updates an existing pet reminder. */
    public void updateReminder(@NonNull Reminder reminder, @NonNull SaveReminderCallback callback) {
        try {
            if (reminder.getPetId() == null || reminder.getReminderId() == null) {
                callback.onError(new IllegalArgumentException("Pet ID and Reminder ID are required for update."));
                return;
            }

            reminder.setUpdatedAt(new Date());
            remindersCollection(reminder.getPetId())
                    .document(reminder.getReminderId())
                    .set(reminder)
                    .addOnSuccessListener(unused -> {
                        Log.i(TAG, "Reminder updated: " + reminder.getReminderId());
                        callback.onSuccess(reminder);
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /** Deletes a reminder. */
    public void deleteReminder(@NonNull String petId, @NonNull String reminderId, @NonNull DeleteCallback callback) {
        try {
            remindersCollection(petId).document(reminderId).delete()
                    .addOnSuccessListener(unused -> {
                        Log.i(TAG, "Reminder deleted: " + reminderId);
                        callback.onSuccess();
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /** Marks a reminder completed; if recurring, advances to next occurrence. */
    public void markCompleted(@NonNull Reminder reminder, @NonNull SaveReminderCallback callback) {
        if (!Reminder.FREQ_NONE.equalsIgnoreCase(reminder.getRepeatFrequency())) {
            // Recurring: advance scheduled timestamp to next cycle
            long nextTime = reminder.computeNextScheduledTimestamp();
            reminder.setScheduledTimestamp(nextTime);
            reminder.setStatus(reminder.calculateCurrentStatus());
            reminder.setEnabled(true);
        } else {
            reminder.setStatus(Reminder.STATUS_COMPLETED);
            reminder.setEnabled(false);
        }
        updateReminder(reminder, callback);
    }

    /** Snoozes reminder by specified milliseconds (e.g. 1 hour = 3600_000). */
    public void snoozeReminder(@NonNull Reminder reminder, long snoozeMillis, @NonNull SaveReminderCallback callback) {
        reminder.setScheduledTimestamp(System.currentTimeMillis() + snoozeMillis);
        reminder.setStatus(Reminder.STATUS_UPCOMING);
        reminder.setEnabled(true);
        updateReminder(reminder, callback);
    }

    /** Toggles reminder active/disabled status. */
    public void toggleEnabled(@NonNull Reminder reminder, boolean isEnabled, @NonNull SaveReminderCallback callback) {
        reminder.setEnabled(isEnabled);
        updateReminder(reminder, callback);
    }
}
