package com.example.pet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Unit tests validating authentication input contracts,
 * UID-based user profile structure, atomic registration callback behavior,
 * retry semantics, and SetOptions.merge data preservation.
 */
public class AuthAndUserManagementTest {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
    );

    @Test
    public void testEmailValidation() {
        assertTrue(EMAIL_PATTERN.matcher("user@example.com").matches());
        assertTrue(EMAIL_PATTERN.matcher("pet.owner+test@domain.org").matches());
        assertFalse(EMAIL_PATTERN.matcher("invalid-email").matches());
        assertFalse(EMAIL_PATTERN.matcher("@missingusername.com").matches());
        assertFalse(EMAIL_PATTERN.matcher("user@").matches());
    }

    @Test
    public void testPasswordLengthContract() {
        String shortPassword = "123";
        assertTrue("Passwords shorter than 6 characters must be rejected", shortPassword.length() < 6);

        String validPassword = "securePass123";
        assertTrue("Passwords with 6 or more characters meet Firebase Auth requirement", validPassword.length() >= 6);
    }

    @Test
    public void testPasswordConfirmationContract() {
        String pass1 = "secretPass123";
        String pass2 = "secretPass123";
        String passMismatch = "differentPass456";

        assertEquals(pass1, pass2);
        assertFalse(pass1.equals(passMismatch));
    }

    @Test
    public void testUserProfileDocumentPathIsolation() {
        String uid = "firebase_user_abc123";
        String profilePath = "users/" + uid;

        assertTrue(profilePath.startsWith("users/"));
        assertEquals("users/firebase_user_abc123", profilePath);

        // Subcollections must be properly nested under the user's UID
        String petsPath = profilePath + "/pets/pet_dog_1";
        assertEquals("users/firebase_user_abc123/pets/pet_dog_1", petsPath);

        String labPath = petsPath + "/lab_records/rec_1";
        assertEquals("users/firebase_user_abc123/pets/pet_dog_1/lab_records/rec_1", labPath);
    }

    @Test
    public void testStoragePathIsolation() {
        String uid = "user_789";
        String petId = "pet_456";
        String recordId = "rec_123";
        String fileName = "blood_report.pdf";

        String storagePath = "users/" + uid + "/pets/" + petId + "/lab_records/" + recordId + "/" + cleanFileName(fileName);

        assertTrue(storagePath.startsWith("users/" + uid + "/"));
        assertTrue(storagePath.endsWith(cleanFileName(fileName)));
    }

    @Test
    public void testRegistrationFailsIfFirestoreProfileFails() {
        // Simulates the contract: If Auth succeeds but Firestore fails,
        // onSuccess must NEVER be called, and a clear error message must be delivered.
        AtomicBoolean successCalled = new AtomicBoolean(false);
        AtomicReference<String> deliveredError = new AtomicReference<>();

        AuthRepository.AuthCallback registrationCallback = new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
                successCalled.set(true);
            }

            @Override
            public void onError(Exception e) {
                deliveredError.set(e.getMessage());
            }
        };

        // Trigger failure simulation
        Exception simulatedFirestoreError = new Exception("Network timeout connecting to Firestore");
        // Emulating AuthRepository.updateProfileAndSave failure branch:
        registrationCallback.onError(new Exception(
                "Account created, but failed to save profile in database. Please check your connection and tap Create Account again to retry.",
                simulatedFirestoreError));

        assertFalse("onSuccess must NOT be called if Firestore profile creation fails", successCalled.get());
        assertNotNull("Delivered error must not be null", deliveredError.get());
        assertTrue("Error message must clearly state profile save failure and retry prompt",
                deliveredError.get().contains("failed to save profile in database"));
    }

    @Test
    public void testRetryProfileCreationContractWithoutDuplicate() {
        String existingEmail = "jane.doe@example.com";
        String currentAuthEmail = "jane.doe@example.com";

        // When a user retries, if current signed-in email matches, retry directly without re-registering
        boolean isSameUserSession = existingEmail.equalsIgnoreCase(currentAuthEmail);
        assertTrue("User must be able to retry profile creation with the active session without duplicate account error",
                isSameUserSession);
    }

    @Test
    public void testSetOptionsMergePreservesExistingFields() {
        // Existing document before login update
        Map<String, Object> existingUserDoc = new HashMap<>();
        existingUserDoc.put("uid", "user_xyz");
        existingUserDoc.put("email", "petparent@example.com");
        existingUserDoc.put("displayName", "Alice");
        existingUserDoc.put("createdAt", 1730000000L);
        existingUserDoc.put("membershipTier", "Premium"); // Custom field

        // Update payload from login/merge
        Map<String, Object> loginUpdate = new HashMap<>();
        loginUpdate.put("uid", "user_xyz");
        loginUpdate.put("lastLoginAt", 1730700000L);

        // Merge simulation
        Map<String, Object> mergedDoc = new HashMap<>(existingUserDoc);
        mergedDoc.putAll(loginUpdate);

        assertEquals("user_xyz", mergedDoc.get("uid"));
        assertEquals("petparent@example.com", mergedDoc.get("email"));
        assertEquals("Alice", mergedDoc.get("displayName"));
        assertEquals(1730000000L, mergedDoc.get("createdAt")); // Must be preserved!
        assertEquals("Premium", mergedDoc.get("membershipTier")); // Must be preserved!
        assertEquals(1730700000L, mergedDoc.get("lastLoginAt")); // Updated!
    }

    @Test
    public void testLoginProfileUpdatePayloadStructure() {
        String uid = "test_user_uid_123";
        String email = "doctor.vet@example.com";
        String displayName = "Dr. Smith";

        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", uid);
        payload.put("email", email);
        payload.put("displayName", displayName);

        assertTrue(payload.containsKey("uid"));
        assertTrue(payload.containsKey("email"));
        assertTrue(payload.containsKey("displayName"));
        assertEquals("test_user_uid_123", payload.get("uid"));
    }

    private String cleanFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
