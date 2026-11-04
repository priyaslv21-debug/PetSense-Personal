package com.example.pet.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists pet profiles to Cloud Firestore, under:
 *   users/{uid}/pets/{petId}
 */
public class PetRepository {

    private static final String TAG = "PetRepository";

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    public PetRepository() {
        try {
            firestore = FirebaseFirestore.getInstance();
            auth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Initialization error in PetRepository", e);
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

    public interface PetsCallback {
        void onSuccess(List<Pet> pets);
        void onError(Exception e);
    }

    public interface SavePetCallback {
        void onSuccess(Pet savedPet);
        void onError(Exception e);
    }

    private CollectionReference petsCollection() {
        String uid = currentUid();
        return getFirestore().collection("users").document(uid).collection("pets");
    }

    private String currentUid() {
        FirebaseAuth a = getAuth();
        if (a == null || a.getCurrentUser() == null) {
            String errorMsg = "PetRepository: No signed-in Firebase user when accessing pet repository.";
            Log.e(TAG, errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        return a.getCurrentUser().getUid();
    }

    public void loadPets(@NonNull PetsCallback callback) {
        try {
            FirebaseAuth a = getAuth();
            if (a == null || a.getCurrentUser() == null) {
                String errorMsg = "PetRepository: No signed-in Firebase user when attempting to load pets.";
                Log.e(TAG, errorMsg);
                callback.onError(new IllegalStateException(errorMsg));
                return;
            }

            String uid = a.getCurrentUser().getUid();
            Log.i(TAG, "Authenticated Firebase UID for loadPets: " + uid);
            Log.i(TAG, "Target Firestore read path: users/" + uid + "/pets");

            petsCollection().get()
                    .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                        @Override
                        public void onSuccess(QuerySnapshot snapshot) {
                            List<Pet> pets = new ArrayList<>();
                            snapshot.forEach(doc -> {
                                Pet pet = doc.toObject(Pet.class);
                                pet.setId(doc.getId());
                                pets.add(pet);
                            });
                            Log.i(TAG, "Loaded " + pets.size() + " pets from Firestore for UID: " + uid);
                            callback.onSuccess(pets);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Firestore read failed at users/" + uid + "/pets for UID: " + uid, e);
                            callback.onError(e);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception in loadPets", e);
            callback.onError(e);
        }
    }

    public void addPet(@NonNull Pet pet, @NonNull SavePetCallback callback) {
        try {
            FirebaseAuth a = getAuth();
            if (a == null || a.getCurrentUser() == null) {
                String errorMsg = "PetRepository: No signed-in Firebase user when attempting to add pet.";
                Log.e(TAG, errorMsg);
                callback.onError(new IllegalStateException(errorMsg));
                return;
            }

            String uid = a.getCurrentUser().getUid();
            Log.i(TAG, "Authenticated Firebase UID for addPet: " + uid);

            DocumentReference ref = petsCollection().document(); // pre-generates an id
            pet.setId(ref.getId());

            String path = "users/" + uid + "/pets/" + pet.getId();
            Log.i(TAG, "Target Firestore write path: " + path);

            ref.set(pet)
                    .addOnSuccessListener(unused -> {
                        Log.i(TAG, "Successfully persisted pet profile to Firestore at path: " + path);
                        callback.onSuccess(pet);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Firestore write failed at path: " + path + " for UID: " + uid, e);
                        callback.onError(e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception in addPet", e);
            callback.onError(e);
        }
    }

    public void updatePet(@NonNull Pet pet, @NonNull SavePetCallback callback) {
        try {
            FirebaseAuth a = getAuth();
            if (a == null || a.getCurrentUser() == null) {
                String errorMsg = "PetRepository: No signed-in Firebase user when attempting to update pet.";
                Log.e(TAG, errorMsg);
                callback.onError(new IllegalStateException(errorMsg));
                return;
            }

            String uid = a.getCurrentUser().getUid();
            Log.i(TAG, "Authenticated Firebase UID for updatePet: " + uid);

            if (pet.getId() == null) {
                addPet(pet, callback);
                return;
            }

            String path = "users/" + uid + "/pets/" + pet.getId();
            Log.i(TAG, "Target Firestore update path: " + path);

            petsCollection().document(pet.getId()).set(pet)
                    .addOnSuccessListener(unused -> {
                        Log.i(TAG, "Successfully updated pet profile at path: " + path);
                        callback.onSuccess(pet);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Firestore update failed at path: " + path + " for UID: " + uid, e);
                        callback.onError(e);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception in updatePet", e);
            callback.onError(e);
        }
    }

    public interface ActivityRecordsCallback {
        void onSuccess(List<ActivityRecord> records);
        void onError(Exception e);
    }

    public interface SaveActivityRecordCallback {
        void onSuccess(ActivityRecord savedRecord);
        void onError(Exception e);
    }

    private CollectionReference activityRecordsCollection(String petId) {
        return petsCollection().document(petId).collection("activityRecords");
    }

    public void loadActivityRecords(@NonNull String petId, @NonNull ActivityRecordsCallback callback) {
        try {
            activityRecordsCollection(petId).orderBy("date", Query.Direction.DESCENDING).get()
                    .addOnSuccessListener(snapshot -> {
                        List<ActivityRecord> records = new ArrayList<>();
                        snapshot.forEach(doc -> {
                            ActivityRecord record = doc.toObject(ActivityRecord.class);
                            record.setId(doc.getId());
                            records.add(record);
                        });
                        callback.onSuccess(records);
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public void addActivityRecord(@NonNull String petId, @NonNull ActivityRecord record, @NonNull SaveActivityRecordCallback callback) {
        try {
            DocumentReference ref = activityRecordsCollection(petId).document();
            record.setId(ref.getId());
            ref.set(record)
                    .addOnSuccessListener(unused -> callback.onSuccess(record))
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }
}
