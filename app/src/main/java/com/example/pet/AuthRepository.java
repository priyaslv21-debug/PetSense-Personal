package com.example.pet;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles Firebase Authentication (Email/Password registration, login, logout, session persistence)
 * and persists user profile documents under users/{uid} in Cloud Firestore.
 *
 * Registration strictly reports success ONLY after both Firebase Auth and Firestore profile creation succeed.
 * Uses SetOptions.merge() to protect existing user profile data.
 */
public class AuthRepository {

    private static final String TAG = "AuthRepository";

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(Exception e);
    }

    public interface UserProfileCallback {
        void onSuccess(Map<String, Object> profile);
        void onError(Exception e);
    }

    public AuthRepository() {
        try {
            firebaseAuth = FirebaseAuth.getInstance();
            firestore = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Initialization error in AuthRepository", e);
        }
    }

    private FirebaseAuth getFirebaseAuth() {
        if (firebaseAuth == null) {
            firebaseAuth = FirebaseAuth.getInstance();
        }
        return firebaseAuth;
    }

    private FirebaseFirestore getFirestore() {
        if (firestore == null) {
            firestore = FirebaseFirestore.getInstance();
        }
        return firestore;
    }

    /**
     * Authenticates an existing user via Email & Password, updates last login timestamp,
     * and persists session state.
     */
    public MutableLiveData<FirebaseUser> login(String email, String password) {
        MutableLiveData<FirebaseUser> userLiveData = new MutableLiveData<>();

        try {
            FirebaseAuth auth = getFirebaseAuth();
            if (auth == null) {
                userLiveData.setValue(null);
                return userLiveData;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && auth.getCurrentUser() != null) {
                            FirebaseUser user = auth.getCurrentUser();
                            updateUserLastLogin(user);
                            userLiveData.setValue(user);
                        } else {
                            userLiveData.setValue(null);
                        }
                    });
        } catch (Exception e) {
            userLiveData.setValue(null);
        }
        return userLiveData;
    }

    /**
     * Registers a new user with email, password, and display name.
     * Reports success ONLY when both Firebase Auth account creation AND Firestore profile persistence succeed.
     * If the account already exists and is authenticated in the current session (e.g. from an earlier failed profile write),
     * it directly retries saving the profile without throwing a collision error.
     */
    public void register(@NonNull String email, @NonNull String password,
                         @Nullable String displayName, @NonNull AuthCallback callback) {
        try {
            FirebaseAuth auth = getFirebaseAuth();
            if (auth == null) {
                callback.onError(new Exception("Firebase Auth is unavailable."));
                return;
            }

            String cleanEmail = email.trim();
            FirebaseUser currentUser = auth.getCurrentUser();

            // If the user was already created in Auth during a previous attempt, retry profile creation directly
            if (currentUser != null && currentUser.getEmail() != null
                    && currentUser.getEmail().equalsIgnoreCase(cleanEmail)) {
                updateProfileAndSave(currentUser, displayName, callback);
                return;
            }

            auth.createUserWithEmailAndPassword(cleanEmail, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && auth.getCurrentUser() != null) {
                            FirebaseUser user = auth.getCurrentUser();
                            updateProfileAndSave(user, displayName, callback);
                        } else {
                            Exception ex = task.getException() != null
                                    ? task.getException()
                                    : new Exception("Registration failed.");

                            // If account collision occurs on retry, attempt to sign in with the provided credentials
                            // and complete the profile write if credentials match.
                            if (ex instanceof FirebaseAuthUserCollisionException) {
                                auth.signInWithEmailAndPassword(cleanEmail, password)
                                        .addOnCompleteListener(signInTask -> {
                                            if (signInTask.isSuccessful() && auth.getCurrentUser() != null) {
                                                updateProfileAndSave(auth.getCurrentUser(), displayName, callback);
                                            } else {
                                                callback.onError(new Exception("An account already exists with this email address. Please sign in instead."));
                                            }
                                        });
                            } else {
                                callback.onError(ex);
                            }
                        }
                    });
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Updates Firebase Auth display name and writes the user profile document to Firestore under users/{uid}.
     * Fails registration if Firestore profile creation fails.
     */
    private void updateProfileAndSave(@NonNull FirebaseUser user, @Nullable String displayName,
                                      @NonNull AuthCallback callback) {
        // Update user display name in Firebase Auth if provided
        if (displayName != null && !displayName.trim().isEmpty()) {
            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build();
            user.updateProfile(profileUpdates);
        }

        // Store user profile in Cloud Firestore under users/{uid}
        saveUserProfile(user, displayName, new AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser savedUser) {
                // Both Auth and Firestore succeeded
                callback.onSuccess(savedUser);
            }

            @Override
            public void onError(Exception e) {
                // Do NOT report success if Firestore write fails!
                Log.e(TAG, "Auth succeeded but Firestore profile creation failed: " + e.getMessage(), e);
                callback.onError(new Exception(
                        "Account created, but failed to save profile in database. Please check your connection and tap Create Account again to retry.", e));
            }
        });
    }

    /**
     * Stores user profile information using the authenticated Firebase UID in users/{uid}.
     * Uses SetOptions.merge() to protect existing fields.
     */
    public void saveUserProfile(@NonNull FirebaseUser user, @Nullable String displayName, @NonNull AuthCallback callback) {
        try {
            String uid = user.getUid();
            Map<String, Object> profile = new HashMap<>();
            profile.put("uid", uid);
            profile.put("email", user.getEmail() != null ? user.getEmail() : "");
            profile.put("displayName", (displayName != null && !displayName.trim().isEmpty())
                    ? displayName.trim()
                    : (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty() ? user.getDisplayName().trim() : "Pet Parent"));
            profile.put("createdAt", FieldValue.serverTimestamp());
            profile.put("lastLoginAt", FieldValue.serverTimestamp());

            getFirestore().collection("users").document(uid)
                    .set(profile, SetOptions.merge())
                    .addOnSuccessListener(unused -> callback.onSuccess(user))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to write user profile to Firestore for UID " + uid, e);
                        callback.onError(new Exception("Failed to save user profile: " + e.getMessage(), e));
                    });

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Retries profile creation for the currently authenticated user session.
     */
    public void retryProfileCreation(@Nullable String displayName, @NonNull AuthCallback callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            callback.onError(new Exception("No active authenticated session found. Please sign in first."));
            return;
        }
        updateProfileAndSave(user, displayName, callback);
    }

    /**
     * Retrieves the Firestore user profile for a given UID.
     */
    public void getUserProfile(@NonNull String uid, @NonNull UserProfileCallback callback) {
        try {
            getFirestore().collection("users").document(uid).get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists() && snapshot.getData() != null) {
                            callback.onSuccess(snapshot.getData());
                        } else {
                            callback.onError(new Exception("User profile document not found in Firestore for UID: " + uid));
                        }
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * Updates last login timestamp and email for existing user profile under users/{uid}.
     * Uses SetOptions.merge() to preserve all other fields and subcollections.
     */
    private void updateUserLastLogin(@NonNull FirebaseUser user) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("uid", user.getUid());
            update.put("lastLoginAt", FieldValue.serverTimestamp());
            if (user.getEmail() != null) {
                update.put("email", user.getEmail());
            }
            if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                update.put("displayName", user.getDisplayName().trim());
            }

            getFirestore().collection("users").document(user.getUid())
                    .set(update, SetOptions.merge())
                    .addOnFailureListener(e -> Log.w(TAG, "Could not update last login: " + e.getMessage()));
        } catch (Exception ignored) {
        }
    }

    /**
     * Signs out the current user session.
     */
    public void logout() {
        try {
            FirebaseAuth auth = getFirebaseAuth();
            if (auth != null) {
                auth.signOut();
            }
        } catch (Exception e) {
            Log.e(TAG, "Logout error", e);
        }
    }

    /**
     * Returns the currently authenticated user, or null if no session is active.
     */
    @Nullable
    public FirebaseUser getCurrentUser() {
        try {
            FirebaseAuth auth = getFirebaseAuth();
            return auth != null ? auth.getCurrentUser() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
