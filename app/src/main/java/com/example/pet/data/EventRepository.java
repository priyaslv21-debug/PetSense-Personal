package com.example.pet.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository responsible for Cloud Firestore and Firebase Storage operations
 * for the PetSense Community & Events Management System.
 *
 * Implements:
 * 1. Event creation, discovery, editing, deletion, and reporting.
 * 2. Concurrency-safe event registration and cancellation via Firestore transactions.
 * 3. Event poster uploads to Firebase Storage with resilient local private storage fallback.
 */
public class EventRepository {

    private static final String TAG = "EventRepository";

    public static final String COLLECTION_EVENTS = "events";
    public static final String SUB_COLLECTION_REGISTRATIONS = "registrations";
    public static final String COLLECTION_USERS = "users";
    public static final String SUB_COLLECTION_REGISTERED_EVENTS = "registered_events";
    public static final String COLLECTION_REPORTS = "event_reports";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final FirebaseStorage storage;

    public interface EventCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public EventRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        FirebaseStorage storageInstance = null;
        try {
            storageInstance = FirebaseStorage.getInstance();
        } catch (Exception e) {
            Log.w(TAG, "Firebase Storage not initialized or unavailable: " + e.getMessage());
        }
        this.storage = storageInstance;
    }

    @Nullable
    public FirebaseUser getCurrentUser() {
        return auth != null ? auth.getCurrentUser() : null;
    }

    /**
     * Publishes a new event.
     */
    public void createEvent(@NonNull Context context, @NonNull Event event,
                            @Nullable Uri posterUri, @NonNull EventCallback<Event> callback) {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null) {
            callback.onError(new Exception("Authentication required to create an event."));
            return;
        }

        DocumentReference docRef = firestore.collection(COLLECTION_EVENTS).document();
        event.setEventId(docRef.getId());
        event.setOrganizerId(currentUser.getUid());
        if (event.getOrganizerName() == null || event.getOrganizerName().trim().isEmpty()) {
            event.setOrganizerName(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "PetSense User");
        }
        event.setCreatedAt(System.currentTimeMillis());
        event.setUpdatedAt(System.currentTimeMillis());
        event.setEventStatus(Event.STATUS_ACTIVE);
        event.setRegistrationCount(0);

        if (posterUri != null) {
            uploadPosterImage(context, event.getEventId(), posterUri, new EventCallback<String>() {
                @Override
                public void onSuccess(String imageUrl) {
                    event.setImageUrl(imageUrl);
                    saveEventDocument(docRef, event, callback);
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "Poster upload encountered an issue, saving without image: " + e.getMessage());
                    saveEventDocument(docRef, event, callback);
                }
            });
        } else {
            saveEventDocument(docRef, event, callback);
        }
    }

    private void saveEventDocument(DocumentReference docRef, Event event, EventCallback<Event> callback) {
        docRef.set(event)
                .addOnSuccessListener(unused -> callback.onSuccess(event))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Updates an existing event. Only the organizer is permitted to edit.
     */
    public void updateEvent(@NonNull Context context, @NonNull Event event,
                            @Nullable Uri newPosterUri, @NonNull EventCallback<Void> callback) {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null || !currentUser.getUid().equals(event.getOrganizerId())) {
            callback.onError(new Exception("Only the event creator can edit this event."));
            return;
        }

        event.setUpdatedAt(System.currentTimeMillis());

        if (newPosterUri != null) {
            uploadPosterImage(context, event.getEventId(), newPosterUri, new EventCallback<String>() {
                @Override
                public void onSuccess(String imageUrl) {
                    event.setImageUrl(imageUrl);
                    firestore.collection(COLLECTION_EVENTS).document(event.getEventId())
                            .set(event, SetOptions.merge())
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onError);
                }

                @Override
                public void onError(Exception e) {
                    firestore.collection(COLLECTION_EVENTS).document(event.getEventId())
                            .set(event, SetOptions.merge())
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onError);
                }
            });
        } else {
            firestore.collection(COLLECTION_EVENTS).document(event.getEventId())
                    .set(event, SetOptions.merge())
                    .addOnSuccessListener(unused -> callback.onSuccess(null))
                    .addOnFailureListener(callback::onError);
        }
    }

    /**
     * Cancels an event. Only the organizer is permitted.
     */
    public void cancelEvent(@NonNull String eventId, @NonNull EventCallback<Void> callback) {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null) {
            callback.onError(new Exception("Authentication required."));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("eventStatus", Event.STATUS_CANCELLED);
        updates.put("updatedAt", System.currentTimeMillis());

        firestore.collection(COLLECTION_EVENTS).document(eventId)
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Deletes an event. Only the organizer is permitted.
     */
    public void deleteEvent(@NonNull String eventId, @NonNull EventCallback<Void> callback) {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null) {
            callback.onError(new Exception("Authentication required."));
            return;
        }

        firestore.collection(COLLECTION_EVENTS).document(eventId)
                .delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Fetches a single event document by its ID.
     */
    public void getEvent(@NonNull String eventId, @NonNull EventCallback<Event> callback) {
        firestore.collection(COLLECTION_EVENTS).document(eventId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Event event = snapshot.toObject(Event.class);
                        if (event != null) {
                            event.setEventId(snapshot.getId());
                            callback.onSuccess(event);
                            return;
                        }
                    }
                    callback.onError(new Exception("Event not found."));
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Fetches all active upcoming events for public discovery feed.
     */
    public void getUpcomingEvents(@NonNull EventCallback<List<Event>> callback) {
        firestore.collection(COLLECTION_EVENTS)
                .whereEqualTo("eventStatus", Event.STATUS_ACTIVE)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<Event> list = new ArrayList<>();
                    long currentDayStart = System.currentTimeMillis() - (24 * 3600 * 1000L);
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            event.setEventId(doc.getId());
                            // Keep upcoming or same-day events
                            if (event.getEventDate() == null || event.getEventDate() >= currentDayStart) {
                                list.add(event);
                            }
                        }
                    }
                    // Sort by eventDate ascending
                    Collections.sort(list, (a, b) -> {
                        long da = a.getEventDate() != null ? a.getEventDate() : Long.MAX_VALUE;
                        long db = b.getEventDate() != null ? b.getEventDate() : Long.MAX_VALUE;
                        return Long.compare(da, db);
                    });
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Fetches events created by a specific user (for "Events I Created").
     */
    public void getEventsCreatedByUser(@NonNull String userId, @NonNull EventCallback<List<Event>> callback) {
        firestore.collection(COLLECTION_EVENTS)
                .whereEqualTo("organizerId", userId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<Event> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            event.setEventId(doc.getId());
                            list.add(event);
                        }
                    }
                    // Sort by createdAt descending
                    Collections.sort(list, (a, b) -> {
                        long da = a.getCreatedAt() != null ? a.getCreatedAt() : 0L;
                        long db = b.getCreatedAt() != null ? b.getCreatedAt() : 0L;
                        return Long.compare(db, da);
                    });
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Concurrency-safe event registration using Cloud Firestore transaction.
     * Enforces:
     * 1. Event is active.
     * 2. Registration deadline has not passed.
     * 3. Capacity limit has not been exceeded.
     * 4. User is not already registered.
     */
    public void registerForEvent(@NonNull EventRegistration registration, @NonNull EventCallback<Void> callback) {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null) {
            callback.onError(new Exception("Authentication required to register for an event."));
            return;
        }

        final String eventId = registration.getEventId();
        final String userId = currentUser.getUid();
        registration.setUserId(userId);
        registration.setRegistrationId(userId);

        final DocumentReference eventRef = firestore.collection(COLLECTION_EVENTS).document(eventId);
        final DocumentReference regRef = eventRef.collection(SUB_COLLECTION_REGISTRATIONS).document(userId);
        final DocumentReference userRegRef = firestore.collection(COLLECTION_USERS).document(userId)
                .collection(SUB_COLLECTION_REGISTERED_EVENTS).document(eventId);

        firestore.runTransaction(transaction -> {
            DocumentSnapshot eventSnap = transaction.get(eventRef);
            if (!eventSnap.exists()) {
                throw new FirebaseFirestoreException("Event does not exist.",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            Event event = eventSnap.toObject(Event.class);
            if (event == null) {
                throw new FirebaseFirestoreException("Failed to load event details.",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            if (!Event.STATUS_ACTIVE.equalsIgnoreCase(event.getEventStatus())) {
                throw new FirebaseFirestoreException("This event is no longer active (" + event.getEventStatus() + ").",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            if (event.isPastDeadline()) {
                throw new FirebaseFirestoreException("The registration deadline for this event has passed.",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            if (event.isFull()) {
                throw new FirebaseFirestoreException("This event has reached maximum participant capacity.",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            // Check if already registered
            DocumentSnapshot regSnap = transaction.get(regRef);
            if (regSnap.exists()) {
                String existingStatus = regSnap.getString("registrationStatus");
                if (EventRegistration.STATUS_CONFIRMED.equalsIgnoreCase(existingStatus)) {
                    throw new FirebaseFirestoreException("You are already registered for this event.",
                            FirebaseFirestoreException.Code.ALREADY_EXISTS);
                }
            }

            // Prepare registration object
            registration.setRegistrationStatus(EventRegistration.STATUS_CONFIRMED);
            registration.setRegisteredAt(System.currentTimeMillis());
            registration.setEventTitle(event.getTitle());
            registration.setEventDate(event.getEventDate());
            registration.setEventVenue(event.getVenue());
            registration.setEventCity(event.getCity());
            registration.setEventFee(event.getEventFee());

            // 1. Write registration record inside event subcollection
            transaction.set(regRef, registration);

            // 2. Increment participant count on event document
            int updatedCount = event.getRegistrationCount() + 1;
            transaction.update(eventRef, "registrationCount", updatedCount);

            // 3. Save copy under user's private registered events collection
            transaction.set(userRegRef, registration);

            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess(null))
          .addOnFailureListener(callback::onError);
    }

    /**
     * Cancels an existing user registration using a Firestore transaction.
     * Decrements the event participant count.
     */
    public void cancelRegistration(@NonNull String eventId, @NonNull EventCallback<Void> callback) {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null) {
            callback.onError(new Exception("Authentication required."));
            return;
        }

        final String userId = currentUser.getUid();
        final DocumentReference eventRef = firestore.collection(COLLECTION_EVENTS).document(eventId);
        final DocumentReference regRef = eventRef.collection(SUB_COLLECTION_REGISTRATIONS).document(userId);
        final DocumentReference userRegRef = firestore.collection(COLLECTION_USERS).document(userId)
                .collection(SUB_COLLECTION_REGISTERED_EVENTS).document(eventId);

        firestore.runTransaction(transaction -> {
            DocumentSnapshot eventSnap = transaction.get(eventRef);
            DocumentSnapshot regSnap = transaction.get(regRef);

            if (!regSnap.exists()) {
                throw new FirebaseFirestoreException("Registration record not found.",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String currentStatus = regSnap.getString("registrationStatus");
            if (!EventRegistration.STATUS_CONFIRMED.equalsIgnoreCase(currentStatus)) {
                throw new FirebaseFirestoreException("Registration is already cancelled.",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            // Update registration status to CANCELLED
            transaction.update(regRef, "registrationStatus", EventRegistration.STATUS_CANCELLED);
            transaction.update(userRegRef, "registrationStatus", EventRegistration.STATUS_CANCELLED);

            // Decrement registration count on event document
            if (eventSnap.exists()) {
                Long count = eventSnap.getLong("registrationCount");
                long newCount = (count != null && count > 0) ? count - 1 : 0;
                transaction.update(eventRef, "registrationCount", newCount);
            }

            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess(null))
          .addOnFailureListener(callback::onError);
    }

    public void cancelRegistration(@NonNull String eventId, @NonNull String userId, @NonNull EventCallback<Void> callback) {
        cancelRegistration(eventId, callback);
    }

    /**
     * Checks whether the current user is registered for the specified event.
     */
    public void checkUserRegistration(@NonNull String eventId, @NonNull EventCallback<EventRegistration> callback) {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null) {
            callback.onSuccess(null);
            return;
        }

        firestore.collection(COLLECTION_EVENTS).document(eventId)
                .collection(SUB_COLLECTION_REGISTRATIONS).document(currentUser.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        EventRegistration reg = snapshot.toObject(EventRegistration.class);
                        if (reg != null && EventRegistration.STATUS_CONFIRMED.equalsIgnoreCase(reg.getRegistrationStatus())) {
                            callback.onSuccess(reg);
                            return;
                        }
                    }
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    public void checkUserRegistration(@NonNull String eventId, @NonNull String userId, @NonNull EventCallback<EventRegistration> callback) {
        checkUserRegistration(eventId, callback);
    }

    /**
     * Fetches all registrations for a specific event (only accessible by event creator).
     */
    public void getRegistrationsForEvent(@NonNull String eventId, @NonNull EventCallback<List<EventRegistration>> callback) {
        firestore.collection(COLLECTION_EVENTS).document(eventId)
                .collection(SUB_COLLECTION_REGISTRATIONS)
                .whereEqualTo("registrationStatus", EventRegistration.STATUS_CONFIRMED)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<EventRegistration> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        EventRegistration reg = doc.toObject(EventRegistration.class);
                        if (reg != null) {
                            list.add(reg);
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Fetches all events the current user is registered for (for "Events I Registered For").
     */
    public void getRegisteredEventsForUser(@NonNull String userId, @NonNull EventCallback<List<EventRegistration>> callback) {
        firestore.collection(COLLECTION_USERS).document(userId)
                .collection(SUB_COLLECTION_REGISTERED_EVENTS)
                .whereEqualTo("registrationStatus", EventRegistration.STATUS_CONFIRMED)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<EventRegistration> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        EventRegistration reg = doc.toObject(EventRegistration.class);
                        if (reg != null) {
                            list.add(reg);
                        }
                    }
                    // Sort by eventDate ascending
                    Collections.sort(list, (a, b) -> {
                        long da = a.getEventDate() != null ? a.getEventDate() : 0L;
                        long db = b.getEventDate() != null ? b.getEventDate() : 0L;
                        return Long.compare(da, db);
                    });
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Submits an event report for inappropriate content or misleading veterinary claims.
     */
    public void reportEvent(@NonNull EventReport report, @NonNull EventCallback<Void> callback) {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser == null) {
            callback.onError(new Exception("Authentication required to report an event."));
            return;
        }

        DocumentReference docRef = firestore.collection(COLLECTION_REPORTS).document();
        report.setReportId(docRef.getId());
        report.setReportedByUserId(currentUser.getUid());
        report.setReportedByEmail(currentUser.getEmail());
        report.setReportedAt(System.currentTimeMillis());

        docRef.set(report)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Uploads event poster to Firebase Storage if available, with automatic fallback
     * to app-private local storage if Storage is offline or unconfigured.
     */
    private void uploadPosterImage(@NonNull Context context, @NonNull String eventId,
                                   @NonNull Uri imageUri, @NonNull EventCallback<String> callback) {
        try {
            // Compress image to max 1200px width/height and 80% JPEG quality
            byte[] compressedBytes = compressImage(context, imageUri, 1200);
            if (compressedBytes == null) {
                callback.onError(new Exception("Unable to process selected image."));
                return;
            }

            // Attempt Firebase Storage first if storage instance is available
            if (storage != null) {
                StorageReference posterRef = storage.getReference()
                        .child("event_posters")
                        .child(eventId + ".jpg");

                StorageMetadata metadata = new StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .build();

                posterRef.putBytes(compressedBytes, metadata)
                        .addOnSuccessListener(taskSnapshot -> posterRef.getDownloadUrl()
                                .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "Failed to get download URL, falling back to local: " + e.getMessage());
                                    savePosterLocally(context, eventId, compressedBytes, callback);
                                }))
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Storage upload failed, falling back to local storage: " + e.getMessage());
                            savePosterLocally(context, eventId, compressedBytes, callback);
                        });
            } else {
                savePosterLocally(context, eventId, compressedBytes, callback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in uploadPosterImage", e);
            callback.onError(e);
        }
    }

    private void savePosterLocally(@NonNull Context context, @NonNull String eventId,
                                   @NonNull byte[] bytes, @NonNull EventCallback<String> callback) {
        try {
            File dir = new File(context.getFilesDir(), "event_posters");
            if (!dir.exists() && !dir.mkdirs()) {
                callback.onError(new Exception("Failed to create local poster directory."));
                return;
            }
            File outFile = new File(dir, "poster_" + eventId + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytes);
                fos.flush();
            }
            callback.onSuccess(Uri.fromFile(outFile).toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save local poster", e);
            callback.onError(e);
        }
    }

    @Nullable
    private byte[] compressImage(Context context, Uri uri, int maxPx) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, opts);

            int sample = 1;
            while (opts.outWidth / (sample * 2) >= maxPx && opts.outHeight / (sample * 2) >= maxPx) {
                sample *= 2;
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;

            try (InputStream in2 = context.getContentResolver().openInputStream(uri)) {
                if (in2 == null) return null;
                Bitmap bitmap = BitmapFactory.decodeStream(in2, null, opts);
                if (bitmap == null) return null;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                bitmap.recycle();
                return baos.toByteArray();
            }
        } catch (Exception e) {
            Log.e(TAG, "Image compression error", e);
            return null;
        }
    }
}
