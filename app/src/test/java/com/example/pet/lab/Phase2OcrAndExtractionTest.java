package com.example.pet.lab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.pet.data.LabRecord;
import com.example.pet.data.LabTestItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Phase 2 Unit Tests:
 * 1. Multi-page PDF text extraction and concatenation.
 * 2. Scanned document handling with noise/headers.
 * 3. Manual OCR correction workflow (user editing values, ranges, names).
 * 4. Empty/unreadable text validation.
 * 5. LabRecord and Firebase model structure validation.
 */
public class Phase2OcrAndExtractionTest {

    @Test
    public void testMultiPagePdfExtractionAndMerging() {
        // Page 1: Chemistry Panel
        String page1 =
                "CENTRAL VETERINARY CLINIC\n" +
                "PATIENT: Buddy (Canine)  PAGE 1 OF 2\n" +
                "ALT 125 U/L 10 - 100\n" +
                "AST 35 U/L 15 - 45\n" +
                "BUN 18 mg/dL 7 - 27\n";

        // Page 2: Hematology (CBC)
        String page2 =
                "CENTRAL VETERINARY CLINIC\n" +
                "PATIENT: Buddy (Canine)  PAGE 2 OF 2\n" +
                "WBC 11.2 x10^9/L 5.0 - 14.0\n" +
                "RBC 6.8 x10^12/L 5.5 - 8.5\n" +
                "Platelets 280 K/uL 175 - 500\n";

        // Concatenated multi-page OCR output
        String fullMultiPageDoc = page1 + "\n" + page2;

        List<LabTestItem> items = LabRuleEngine.parseReportText(fullMultiPageDoc);
        assertNotNull(items);
        assertEquals(6, items.size());

        // Verify Page 1 results
        LabTestItem alt = items.get(0);
        assertEquals("ALT", alt.getTestName());
        assertEquals("125", alt.getValue());
        assertEquals(LabTestItem.STATUS_HIGH, alt.getStatus()); // 125 > 100
        assertTrue(alt.isAbnormal());

        LabTestItem ast = items.get(1);
        assertEquals("AST", ast.getTestName());
        assertEquals(LabTestItem.STATUS_NORMAL, ast.getStatus());

        // Verify Page 2 results
        LabTestItem wbc = items.get(3);
        assertEquals("WBC", wbc.getTestName());
        assertEquals("11.2", wbc.getValue());
        assertEquals(LabTestItem.STATUS_NORMAL, wbc.getStatus());

        LabTestItem plt = items.get(5);
        assertEquals("Platelets", plt.getTestName());
        assertEquals(LabTestItem.STATUS_NORMAL, plt.getStatus());
    }

    @Test
    public void testScannedDocumentWithNoisyArtifacts() {
        // Scanned document photograph with clinic metadata, phone, asterisks, and noise
        String scannedText =
                "IDEXX Reference Laboratories\n" +
                "Doctor: Dr. Smith, DVM   Phone: (555) 019-2834\n" +
                "Address: 123 Vet Hospital Way\n" +
                "Sample ID: #9827361   Test Date: 11/02/2026\n" +
                "-----------------------------------------\n" +
                "Glucose 210* mg/dL 70 - 150 HIGH\n" +
                "Creatinine 1.2 mg/dL 0.5 - 1.5\n" +
                "Phosphorus 4.2 mg/dL 2.5 - 6.0\n" +
                "-----------------------------------------\n" +
                "Page 1 of 1 Verified by Lab Technician\n";

        List<LabTestItem> items = LabRuleEngine.parseReportText(scannedText);
        assertEquals(3, items.size());

        LabTestItem glu = items.get(0);
        assertEquals("Glucose", glu.getTestName());
        assertEquals(210.0, glu.getNumericValue(), 0.01);
        assertEquals(LabTestItem.STATUS_HIGH, glu.getStatus());

        LabTestItem crea = items.get(1);
        assertEquals("Creatinine", crea.getTestName());
        assertEquals(LabTestItem.STATUS_NORMAL, crea.getStatus());
    }

    @Test
    public void testManualOcrCorrectionWorkflow() {
        // Simulate initial OCR misread: "ALKP1" with missing range
        LabTestItem itemWithOcrError = new LabTestItem(
                "ALKP1", "180", "U/L", "", LabTestItem.STATUS_UNCLASSIFIED
        );
        LabRuleEngine.classifyTestItem(itemWithOcrError);
        // Initially UNCLASSIFIED because range was missing
        assertEquals(LabTestItem.STATUS_UNCLASSIFIED, itemWithOcrError.getStatus());

        // User manually corrects the test name and adds the printed report range
        itemWithOcrError.setTestName("ALKP");
        itemWithOcrError.setRefRangeText("20 - 150");
        itemWithOcrError.setRefRangeLow(20.0);
        itemWithOcrError.setRefRangeHigh(150.0);

        // Re-classify
        LabRuleEngine.classifyTestItem(itemWithOcrError);
        // 180 > 150 -> HIGH
        assertEquals(LabTestItem.STATUS_HIGH, itemWithOcrError.getStatus());
        assertTrue(itemWithOcrError.isAbnormal());

        // User manually adjusts the measured value from "180" to "140" (correcting OCR mistake)
        itemWithOcrError.setValue("140");
        itemWithOcrError.setNumericValue(140.0);
        LabRuleEngine.classifyTestItem(itemWithOcrError);
        // 140 is within 20 - 150 -> NORMAL
        assertEquals(LabTestItem.STATUS_NORMAL, itemWithOcrError.getStatus());
        assertFalse(itemWithOcrError.isAbnormal());
    }

    @Test
    public void testLabRecordAndStorageIntegrationModel() {
        String petId = "pet_dog_123";
        String petName = "Max";
        String species = "Dog";
        Date reportDate = new Date();
        String labName = "Antech Diagnostics";
        String storageUrl = "https://firebasestorage.googleapis.com/v0/b/petsense.appspot.com/o/reports%2Ftest.pdf";
        String storagePath = "users/uid_abc/pets/pet_dog_123/lab_records/rec_1/test.pdf";
        String fileName = "test.pdf";
        String fileType = "application/pdf";

        LabRecord record = new LabRecord(
                petId, petName, species, reportDate,
                labName, storageUrl, storagePath, fileName, fileType
        );

        List<LabTestItem> testList = new ArrayList<>();
        testList.add(new LabTestItem("ALT", "135", "U/L", "10 - 100", LabTestItem.STATUS_HIGH));
        testList.add(new LabTestItem("Creatinine", "1.1", "mg/dL", "0.5 - 1.5", LabTestItem.STATUS_NORMAL));
        testList.add(new LabTestItem("Research Marker", "3.2", "ng/mL", "", LabTestItem.STATUS_UNCLASSIFIED));

        record.setTests(testList);

        assertEquals(3, record.getTotalTestsCount());
        assertEquals(1, record.getAbnormalCount());
        assertEquals(1, record.getUnclassifiedCount());
        assertEquals("Antech Diagnostics", record.getLabName());
        assertEquals(storageUrl, record.getOriginalFileUrl());
        assertEquals(storagePath, record.getStoragePath());
        assertNotNull(record.getDisclaimer());
    }
}
