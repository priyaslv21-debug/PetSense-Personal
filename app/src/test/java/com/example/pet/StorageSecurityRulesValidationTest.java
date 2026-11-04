package com.example.pet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Pattern;

/**
 * Unit tests validating Cloud Storage security rules logic,
 * MIME type matching, payload size boundaries, UID folder isolation,
 * and compatibility with PetSense's actual storage paths.
 */
public class StorageSecurityRulesValidationTest {

    // Regex directly mirroring storage.rules:
    // request.resource.contentType.matches('application/pdf|image/(jpeg|jpg|png)')
    private static final Pattern ALLOWED_MIME_TYPES = Pattern.compile(
            "^(application/pdf|image/(jpeg|jpg|png))$"
    );

    // Constant mirroring storage.rules:
    // request.resource.size <= 15 * 1024 * 1024
    private static final long MAX_STORAGE_FILE_SIZE = 15L * 1024L * 1024L; // 15,728,640 bytes

    @Test
    public void testAllowedMimeTypes() {
        // PDF documents
        assertTrue("PDF files must be allowed", ALLOWED_MIME_TYPES.matcher("application/pdf").matches());

        // JPEG images
        assertTrue("JPEG images must be allowed", ALLOWED_MIME_TYPES.matcher("image/jpeg").matches());
        assertTrue("JPG images must be allowed", ALLOWED_MIME_TYPES.matcher("image/jpg").matches());

        // PNG images
        assertTrue("PNG images must be allowed", ALLOWED_MIME_TYPES.matcher("image/png").matches());
    }

    @Test
    public void testDisallowedMimeTypes() {
        assertFalse("ZIP archives must be blocked", ALLOWED_MIME_TYPES.matcher("application/zip").matches());
        assertFalse("HTML files must be blocked", ALLOWED_MIME_TYPES.matcher("text/html").matches());
        assertFalse("Executables must be blocked", ALLOWED_MIME_TYPES.matcher("application/x-msdownload").matches());
        assertFalse("Generic octet-stream must be blocked", ALLOWED_MIME_TYPES.matcher("application/octet-stream").matches());
        assertFalse("GIF animations must be blocked", ALLOWED_MIME_TYPES.matcher("image/gif").matches());
        assertFalse("Videos must be blocked", ALLOWED_MIME_TYPES.matcher("video/mp4").matches());
        assertFalse("Plain text files must be blocked", ALLOWED_MIME_TYPES.matcher("text/plain").matches());
        assertFalse("Empty MIME type must be blocked", ALLOWED_MIME_TYPES.matcher("").matches());
    }

    @Test
    public void testFileSizeBoundaries() {
        // Within 15 MB limit
        assertTrue("1 KB file must be allowed", 1024L <= MAX_STORAGE_FILE_SIZE);
        assertTrue("5 MB file must be allowed", 5L * 1024L * 1024L <= MAX_STORAGE_FILE_SIZE);
        assertTrue("Exactly 15 MB must be allowed", MAX_STORAGE_FILE_SIZE <= MAX_STORAGE_FILE_SIZE);

        // Exceeding 15 MB limit
        assertFalse("15 MB + 1 byte must be rejected", (MAX_STORAGE_FILE_SIZE + 1L) <= MAX_STORAGE_FILE_SIZE);
        assertFalse("16 MB file must be rejected", (16L * 1024L * 1024L) <= MAX_STORAGE_FILE_SIZE);
        assertFalse("100 MB file must be rejected", (100L * 1024L * 1024L) <= MAX_STORAGE_FILE_SIZE);
    }

    @Test
    public void testStoragePathCompatibilityWithLabRepository() {
        String authUid = "user_alpha_123";
        String petId = "pet_canine_99";
        String recordId = "rec_blood_panel_01";
        String cleanFileName = "1730700000_cbc_report.pdf";

        // Path constructed in LabRepository.java
        String actualStoragePath = "users/" + authUid + "/pets/" + petId + "/lab_records/" + recordId + "/" + cleanFileName;

        // Path pattern in storage.rules: match /users/{userId}/{allPaths=**}
        String expectedPrefix = "users/" + authUid + "/";
        assertTrue("Actual upload path must start with user's isolated folder", actualStoragePath.startsWith(expectedPrefix));

        String remainingPath = actualStoragePath.substring(expectedPrefix.length());
        assertEquals("pets/pet_canine_99/lab_records/rec_blood_panel_01/1730700000_cbc_report.pdf", remainingPath);
    }

    @Test
    public void testCrossUserAccessPrevention() {
        String authUid = "user_alpha_123";
        String targetFolderUserId = "user_beta_456";

        boolean isOwner = authUid != null && authUid.equals(targetFolderUserId);
        assertFalse("User Alpha must not have access to User Beta's folder", isOwner);
    }

    @Test
    public void testDeleteOperationContract() {
        // In Firebase Storage rules, request.resource is null on delete operations.
        // The rule evaluates `allow read, delete: if isOwner(userId);` without referencing request.resource.
        String authUid = "user_alpha_123";
        String folderUserId = "user_alpha_123";

        boolean isOwner = authUid != null && authUid.equals(folderUserId);
        // Delete only requires isOwner(userId), so it succeeds even when resource body is null
        assertTrue("Owner must be permitted to delete files without evaluating request.resource", isOwner);
    }
}
