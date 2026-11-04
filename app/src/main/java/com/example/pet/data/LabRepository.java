package com.example.pet.data;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Repository handling all persistence operations for the Pet Lab Report Analysis module.
 *
 * Persists reports under an isolated Cloud Firestore subcollection:
 *   users/{uid}/pets/{petId}/lab_records/{labRecordId}
 *
 * Persists original PDF/Image documents in secure app-private local storage under:
 *   Context.getFilesDir() + "/users/" + uid + "/pets/" + petId + "/lab_records/" + labRecordId + "/" + fileName
 *
 * Fully operates on the Firebase Spark (Free) plan without requiring Cloud Storage or billing.
 * Strictly follows the existing authentication and asynchronous callback patterns established
 * in PetRepository and AuthRepository, ensuring zero disruption to existing collections.
 */
public class LabRepository {

    private static final String TAG = "LabRepository";

    /**
     * Maximum allowed file size for lab document uploads (15 MB).
     */
    public static final long MAX_FILE_SIZE_BYTES = 15L * 1024L * 1024L;

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    public LabRepository() {
        try {
            firestore = FirebaseFirestore.getInstance();
            auth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Initialization error in LabRepository", e);
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

    /**
     * Resolves the authenticated user's UID.
     * Throws an IllegalStateException if accessed when no user session is active.
     */
    private String currentUid() {
        FirebaseAuth a = getAuth();
        if (a == null || a.getCurrentUser() == null) {
            throw new IllegalStateException(
                    "LabRepository used with no signed-in user — authenticate before accessing lab reports.");
        }
        return a.getCurrentUser().getUid();
    }

    /**
     * Returns the isolated subcollection reference for a specific pet's lab reports.
     */
    private CollectionReference labRecordsCollection(@NonNull String petId) {
        String uid = currentUid();
        return getFirestore()
                .collection("users")
                .document(uid)
                .collection("pets")
                .document(petId)
                .collection("lab_records");
    }

    /**
     * Validates whether a file is an allowed format (PDF, JPG, JPEG, PNG).
     */
    public static boolean isValidMimeOrExtension(@Nullable String mimeType, @NonNull String fileName) {
        if (mimeType != null && !mimeType.trim().isEmpty()) {
            String cleanMime = mimeType.trim().toLowerCase(Locale.ROOT);
            if (cleanMime.equals("application/pdf") ||
                cleanMime.equals("image/jpeg") ||
                cleanMime.equals("image/jpg") ||
                cleanMime.equals("image/png")) {
                return true;
            }
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".jpg") ||
               lower.endsWith(".jpeg") || lower.endsWith(".png");
    }

    /**
     * Resolves the local File from app-private storage for a given relative storage path.
     */
    @NonNull
    public static File getLocalDocumentFile(@NonNull Context context, @NonNull String storagePath) {
        return new File(context.getFilesDir(), storagePath);
    }

    // ─────────────────────────────────────────────────────────────
    //  Callback Interfaces
    // ─────────────────────────────────────────────────────────────

    public interface LabRecordsCallback {
        void onSuccess(List<LabRecord> records);
        void onError(Exception e);
    }

    public interface LabRecordCallback {
        void onSuccess(LabRecord record);
        void onError(Exception e);
    }

    public interface SaveLabRecordCallback {
        void onSuccess(LabRecord savedRecord);
        void onError(Exception e);
    }

    public interface DeleteLabRecordCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public interface UploadFileCallback {
        void onSuccess(String localFilePath, String storagePath);
        void onProgress(int progressPercent);
        void onError(Exception e);
    }

    // ─────────────────────────────────────────────────────────────
    //  Firestore CRUD Operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Loads all lab reports for the specified pet, sorted by report date descending.
     */
    public void loadLabRecords(@NonNull String petId, @NonNull LabRecordsCallback callback) {
        try {
            labRecordsCollection(petId)
                    .orderBy("reportDate", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<LabRecord> records = new ArrayList<>();
                        snapshot.forEach(doc -> {
                            try {
                                LabRecord record = doc.toObject(LabRecord.class);
                                record.setId(doc.getId());
                                records.add(record);
                            } catch (Exception parseEx) {
                                Log.w(TAG, "Failed to parse document " + doc.getId(), parseEx);
                            }
                        });
                        callback.onSuccess(records);
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Fetches a single lab report by ID.
     */
    public void getLabRecord(@NonNull String petId, @NonNull String recordId, @NonNull LabRecordCallback callback) {
        try {
            labRecordsCollection(petId).document(recordId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            LabRecord record = doc.toObject(LabRecord.class);
                            if (record != null) {
                                record.setId(doc.getId());
                                callback.onSuccess(record);
                                return;
                            }
                        }
                        callback.onError(new Exception("Lab report not found."));
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Adds a new lab report to Firestore under users/{uid}/pets/{petId}/lab_records.
     */
    public void addLabRecord(@NonNull String petId, @NonNull LabRecord record, @NonNull SaveLabRecordCallback callback) {
        try {
            record.recalculateCounts();
            DocumentReference ref = labRecordsCollection(petId).document();
            record.setId(ref.getId());
            record.setPetId(petId);

            ref.set(record)
                    .addOnSuccessListener(unused -> callback.onSuccess(record))
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Updates an existing lab report in Firestore.
     */
    public void updateLabRecord(@NonNull String petId, @NonNull LabRecord record, @NonNull SaveLabRecordCallback callback) {
        try {
            if (record.getId() == null || record.getId().trim().isEmpty()) {
                addLabRecord(petId, record, callback);
                return;
            }

            record.recalculateCounts();
            labRecordsCollection(petId).document(record.getId()).set(record)
                    .addOnSuccessListener(unused -> callback.onSuccess(record))
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Deletes a lab report document from Firestore and removes its associated local document file.
     */
    public void deleteLabRecord(@Nullable Context context, @NonNull String petId, @NonNull String recordId,
                                @Nullable String storagePath, @NonNull DeleteLabRecordCallback callback) {
        try {
            labRecordsCollection(petId).document(recordId).delete()
                    .addOnSuccessListener(unused -> {
                if (context != null && storagePath != null && !storagePath.trim().isEmpty()) {
                    try {
                        File localFile = new File(context.getFilesDir(), storagePath);
                        if (localFile.exists()) {
                            localFile.delete();
                        }
                        File parent = localFile.getParentFile();
                        if (parent != null && parent.exists() && parent.isDirectory()) {
                            String[] children = parent.list();
                            if (children == null || children.length == 0) {
                                parent.delete();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                callback.onSuccess();
            })
            .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Overload for backwards compatibility when Context is not directly provided.
     */
    public void deleteLabRecord(@NonNull String petId, @NonNull String recordId,
                                @Nullable String storagePath, @NonNull DeleteLabRecordCallback callback) {
        deleteLabRecord(null, petId, recordId, storagePath, callback);
    }

    // ─────────────────────────────────────────────────────────────
    //  Secure App-Private Local Document Operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Persists the original PDF or image document in secure app-private storage under:
     *   Context.getFilesDir() + "/users/" + uid + "/pets/" + petId + "/lab_records/" + recordId + "/" + fileName
     *
     * Enforces the 15 MB maximum size limit and PDF/JPG/JPEG/PNG format restrictions.
     */
    public void saveReportFileLocally(@NonNull Context context, @NonNull String petId,
                                      @NonNull String recordId, @NonNull Uri fileUri,
                                      @NonNull String fileName, @NonNull UploadFileCallback callback) {
        new Thread(() -> {
            File destinationFile = null;
            try {
                String uid = currentUid();

                String resolvedMime = null;
                try {
                    resolvedMime = context.getContentResolver().getType(fileUri);
                } catch (Exception ignored) {
                }

                if (!isValidMimeOrExtension(resolvedMime, fileName)) {
                    callback.onError(new IllegalArgumentException(
                            "Unsupported file type. Only PDF, JPG, JPEG, and PNG files are allowed."));
                    return;
                }

                String cleanFileName = System.currentTimeMillis() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                String relativePath = "users/" + uid + "/pets/" + petId + "/lab_records/" + recordId + "/" + cleanFileName;

                File baseDir = new File(context.getFilesDir(), "users/" + uid + "/pets/" + petId + "/lab_records/" + recordId);
                if (!baseDir.exists() && !baseDir.mkdirs()) {
                    throw new IllegalStateException("Failed to create local storage directory for lab documents.");
                }

                destinationFile = new File(baseDir, cleanFileName);

                try (InputStream in = context.getContentResolver().openInputStream(fileUri);
                     FileOutputStream out = new FileOutputStream(destinationFile)) {

                    if (in == null) {
                        throw new IllegalArgumentException("Unable to open stream for selected file URI.");
                    }

                    byte[] buffer = new byte[16384];
                    long totalBytes = 0;
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        totalBytes += bytesRead;
                        if (totalBytes > MAX_FILE_SIZE_BYTES) {
                            if (destinationFile.exists()) destinationFile.delete();
                            callback.onError(new IllegalArgumentException(
                                    "File exceeds maximum allowed size of 15 MB."));
                            return;
                        }
                        out.write(buffer, 0, bytesRead);
                        callback.onProgress((int) Math.min(100, (totalBytes * 100) / Math.max(1, totalBytes)));
                    }
                    out.flush();
                }

                String localFilePath = destinationFile.getAbsolutePath();
                callback.onProgress(100);
                callback.onSuccess(localFilePath, relativePath);

            } catch (Exception e) {
                if (destinationFile != null && destinationFile.exists()) {
                    destinationFile.delete();
                }
                callback.onError(e);
            }
        }).start();
    }

    /**
     * Persists raw bytes (e.g. from generated image or processed stream) in secure app-private storage.
     */
    public void saveReportBytesLocally(@NonNull Context context, @NonNull String petId,
                                       @NonNull String recordId, @NonNull byte[] data,
                                       @NonNull String fileName, @NonNull UploadFileCallback callback) {
        new Thread(() -> {
            try {
                String uid = currentUid();
                if (data.length > MAX_FILE_SIZE_BYTES) {
                    callback.onError(new IllegalArgumentException(
                            "File exceeds maximum allowed size of 15 MB."));
                    return;
                }
                if (!isValidMimeOrExtension(null, fileName)) {
                    callback.onError(new IllegalArgumentException(
                            "Unsupported file type. Only PDF, JPG, JPEG, and PNG files are allowed."));
                    return;
                }

                String cleanFileName = System.currentTimeMillis() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                String relativePath = "users/" + uid + "/pets/" + petId + "/lab_records/" + recordId + "/" + cleanFileName;

                File baseDir = new File(context.getFilesDir(), "users/" + uid + "/pets/" + petId + "/lab_records/" + recordId);
                if (!baseDir.exists() && !baseDir.mkdirs()) {
                    throw new IllegalStateException("Failed to create local storage directory for lab documents.");
                }

                File destinationFile = new File(baseDir, cleanFileName);
                try (FileOutputStream out = new FileOutputStream(destinationFile)) {
                    out.write(data);
                    out.flush();
                }

                callback.onProgress(100);
                callback.onSuccess(destinationFile.getAbsolutePath(), relativePath);

            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }
}
