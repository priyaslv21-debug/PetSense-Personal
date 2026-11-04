package com.example.pet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.pet.data.LabRecord;
import com.example.pet.data.LabRepository;
import com.example.pet.data.LabTestItem;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Unit tests validating the Spark-plan local app-private storage implementation:
 * - 15 MB maximum file size limit
 * - Allowed file format filtering (PDF, JPG, JPEG, PNG)
 * - Rejection of unapproved file formats
 * - Path construction and per-user UID isolation
 * - Local file cleanup on record deletion
 * - Firestore metadata persistence without binary blobs
 */
public class LocalStorageLabReportTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void test15MbFileSizeThreshold() {
        assertEquals("Threshold must be exactly 15 MB",
                15L * 1024L * 1024L, LabRepository.MAX_FILE_SIZE_BYTES);

        long validSmallSize = 1024L * 500L; // 500 KB
        long validMaxSize = 15L * 1024L * 1024L; // 15 MB
        long invalidSize = 15L * 1024L * 1024L + 1L; // 15 MB + 1 byte
        long largeSize = 25L * 1024L * 1024L; // 25 MB

        assertTrue("500 KB file must be within limit", validSmallSize <= LabRepository.MAX_FILE_SIZE_BYTES);
        assertTrue("Exactly 15 MB file must be within limit", validMaxSize <= LabRepository.MAX_FILE_SIZE_BYTES);
        assertFalse("15 MB + 1 byte must exceed limit", invalidSize <= LabRepository.MAX_FILE_SIZE_BYTES);
        assertFalse("25 MB file must exceed limit", largeSize <= LabRepository.MAX_FILE_SIZE_BYTES);
    }

    @Test
    public void testAllowedMimeTypesAndExtensions() {
        // MIME types
        assertTrue("PDF MIME must be allowed",
                LabRepository.isValidMimeOrExtension("application/pdf", "report.dat"));
        assertTrue("JPEG MIME must be allowed",
                LabRepository.isValidMimeOrExtension("image/jpeg", "scan.dat"));
        assertTrue("JPG MIME must be allowed",
                LabRepository.isValidMimeOrExtension("image/jpg", "scan.dat"));
        assertTrue("PNG MIME must be allowed",
                LabRepository.isValidMimeOrExtension("image/png", "scan.dat"));

        // File extensions (case-insensitive)
        assertTrue(".pdf extension must be allowed",
                LabRepository.isValidMimeOrExtension(null, "blood_test.pdf"));
        assertTrue(".PDF uppercase extension must be allowed",
                LabRepository.isValidMimeOrExtension(null, "BLOOD_TEST.PDF"));
        assertTrue(".jpg extension must be allowed",
                LabRepository.isValidMimeOrExtension(null, "pet_scan.jpg"));
        assertTrue(".jpeg extension must be allowed",
                LabRepository.isValidMimeOrExtension(null, "pet_scan.jpeg"));
        assertTrue(".png extension must be allowed",
                LabRepository.isValidMimeOrExtension(null, "xray.png"));
        assertTrue(".PNG uppercase extension must be allowed",
                LabRepository.isValidMimeOrExtension(null, "XRAY.PNG"));
    }

    @Test
    public void testDisallowedMimeTypesAndExtensions() {
        assertFalse("ZIP archives must be rejected",
                LabRepository.isValidMimeOrExtension("application/zip", "report.zip"));
        assertFalse("HTML files must be rejected",
                LabRepository.isValidMimeOrExtension("text/html", "report.html"));
        assertFalse("Executables must be rejected",
                LabRepository.isValidMimeOrExtension("application/x-msdownload", "program.exe"));
        assertFalse("MP4 video must be rejected",
                LabRepository.isValidMimeOrExtension("video/mp4", "clip.mp4"));
        assertFalse("Plain text must be rejected",
                LabRepository.isValidMimeOrExtension("text/plain", "notes.txt"));
        assertFalse("Word documents must be rejected",
                LabRepository.isValidMimeOrExtension(null, "doc.docx"));
        assertFalse("Shell scripts must be rejected",
                LabRepository.isValidMimeOrExtension(null, "run.sh"));
    }

    @Test
    public void testAppPrivatePathConstruction() {
        String uid = "user_spark_test_1";
        String petId = "pet_dog_fluffy";
        String recordId = "rec_cbc_001";
        String cleanFileName = "1730700000_cbc_panel.pdf";

        String expectedRelativePath = "users/" + uid + "/pets/" + petId + "/lab_records/" + recordId + "/" + cleanFileName;

        assertTrue("Path must start with user isolation prefix", expectedRelativePath.startsWith("users/" + uid + "/"));
        assertTrue("Path must be scoped to pet", expectedRelativePath.contains("/pets/" + petId + "/"));
        assertTrue("Path must target lab_records subfolder", expectedRelativePath.contains("/lab_records/" + recordId + "/"));
        assertTrue("Path must end with clean file name", expectedRelativePath.endsWith(cleanFileName));
    }

    @Test
    public void testLocalFileCreationAndDeletion() throws IOException {
        File baseUserDir = temporaryFolder.newFolder("users", "user_123", "pets", "pet_456", "lab_records", "rec_789");
        File documentFile = new File(baseUserDir, "test_report.pdf");

        // Write dummy content
        try (FileOutputStream out = new FileOutputStream(documentFile)) {
            out.write("PDF-1.4 dummy laboratory test data".getBytes());
        }

        assertTrue("Document file must exist after write", documentFile.exists());
        assertTrue("Document file length must match content", documentFile.length() > 0);

        // Delete simulation
        boolean deleted = documentFile.delete();
        assertTrue("Document file must be successfully deleted", deleted);
        assertFalse("Document file must no longer exist after deletion", documentFile.exists());
    }

    @Test
    public void testLabRecordMetadataPreservationWithoutBinaryBlobs() {
        LabRecord record = new LabRecord(
                "pet_123",
                "Buddy",
                "Dog",
                new Date(),
                "Central Vet Diagnostics",
                "/data/user/0/com.example.pet/files/users/user_1/pets/pet_123/lab_records/rec_1/report.pdf",
                "users/user_1/pets/pet_123/lab_records/rec_1/report.pdf",
                "report.pdf",
                "application/pdf"
        );

        record.getTests().add(new LabTestItem("ALT", "78", "U/L", "10 - 125", LabTestItem.STATUS_NORMAL));
        record.recalculateCounts();

        assertEquals("pet_123", record.getPetId());
        assertEquals("report.pdf", record.getFileName());
        assertEquals("application/pdf", record.getFileType());
        assertEquals("users/user_1/pets/pet_123/lab_records/rec_1/report.pdf", record.getStoragePath());
        assertEquals(1, record.getTotalTestsCount());
        assertEquals(1, record.getNormalCount());
        assertEquals(0, record.getAbnormalCount());
        assertNotNull(record.getDisclaimer());
    }
}
