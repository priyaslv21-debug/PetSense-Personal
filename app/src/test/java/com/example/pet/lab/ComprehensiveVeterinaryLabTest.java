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
 * Comprehensive verification for:
 * 1. Metadata extraction: Lab Name, Report Date, Pet demographics (Breed, Age, Gender, Weight).
 * 2. Sample Quality Warnings separation (Hemolysis, Lipemia, Icterus).
 * 3. Spatial table reconstruction and OCR artifact tolerance (negative ranges, spaced decimals).
 * 4. Veterinary Attention triage (Urgent vs Prompt vs Routine).
 * 5. Precautions generation strictly without unprescribed drug or dosage recommendations.
 * 6. Non-lab line rejection (addresses, phone numbers, postal codes, barcodes).
 * 7. RecalculateCounts verification (excluding sample quality warnings).
 */
public class ComprehensiveVeterinaryLabTest {

    @Test
    public void testMetadataAndDemographicsExtraction() {
        String reportHeader =
                "IDEXX Reference Laboratories\n" +
                "Date: 11/02/2026\n" +
                "Patient: Buster\n" +
                "Species: Canine\n" +
                "Breed: Golden Retriever\n" +
                "Age: 4 years\n" +
                "Sex: Male Intact\n" +
                "Weight: 31.5 kg\n" +
                "Hemolysis: 2+ (Moderate hemolysis present in specimen)\n" +
                "ALT 145 U/L 10 - 100 HIGH\n";

        LabRuleEngine.ReportMetadata meta = LabRuleEngine.extractReportMetadata(reportHeader);
        assertNotNull(meta);
        assertEquals("IDEXX Reference Laboratories", meta.labName);
        assertEquals("11/02/2026", meta.reportDateStr);
        assertEquals("Buster", meta.petName);
        assertEquals("Dog", meta.species);
        assertEquals("Golden Retriever", meta.breed);
        assertEquals("4 years", meta.age);
        assertEquals("Male Intact", meta.gender);
        assertEquals("31.5 kg", meta.weight);

        // Verify Sample Quality warning was extracted into metadata
        assertEquals(1, meta.sampleQualityWarnings.size());
        assertTrue(meta.sampleQualityWarnings.get(0).contains("HEMOLYSIS"));
    }

    @Test
    public void testSampleQualitySeparationFromDiseaseMetrics() {
        LabRecord record = new LabRecord(
                "pet_123", "Luna", "Cat", new Date(),
                "Antech Diagnostics", null, null, "doc.pdf", "application/pdf"
        );

        List<LabTestItem> items = new ArrayList<>();
        items.add(new LabTestItem("ALT", "45", "U/L", "10 - 100", LabTestItem.STATUS_NORMAL));
        items.add(new LabTestItem("Creatinine", "1.1", "mg/dL", "0.5 - 1.5", LabTestItem.STATUS_NORMAL));

        // Sample quality item
        LabTestItem sqItem = new LabTestItem(
                "Lipemia Index", "General Vitals", "3+", null, "",
                null, null, "", LabTestItem.STATUS_UNCLASSIFIED, ""
        );
        sqItem.setSampleQualityWarning(true);
        items.add(sqItem);

        record.setTests(items);
        record.recalculateCounts();

        // Sample quality items must NOT distort normal/abnormal counts
        assertEquals(2, record.getTotalTestsCount()); // 2 clinical tests, excluding sample quality
        assertEquals(2, record.getNormalCount());
        assertEquals(0, record.getAbnormalCount());
        assertEquals(0, record.getUnclassifiedCount());
    }

    @Test
    public void testNegativeReferenceRangeAndDecimalsWithOcrSpacing() {
        // Test negative ranges like Base Excess: "-4 to +4" or "-4 - +4"
        // Also test OCR spaced decimal: "1 . 2"
        String rawText =
                "Base Excess -1.5 mmol/L -4 to +4\n" +
                "Creatinine 1 . 2 mg/dL 0.5 - 1.5\n";

        List<LabTestItem> items = LabRuleEngine.parseReportText(rawText);
        assertEquals(2, items.size());

        LabTestItem be = items.get(0);
        assertEquals("Base Excess", be.getTestName());
        assertEquals(-1.5, be.getNumericValue(), 0.01);
        assertEquals(LabTestItem.STATUS_NORMAL, be.getStatus());
        assertEquals(-4.0, be.getRefRangeLow(), 0.01);
        assertEquals(4.0, be.getRefRangeHigh(), 0.01);

        LabTestItem crea = items.get(1);
        assertEquals("Creatinine", crea.getTestName());
        assertEquals(1.2, crea.getNumericValue(), 0.01);
        assertEquals(LabTestItem.STATUS_NORMAL, crea.getStatus());
    }

    @Test
    public void testRejectionOfAddressesAndAdministrativeBlocks() {
        String noisyClinicText =
                "Metropolitan Animal Specialty Hospital\n" +
                "1234 Highland Boulevard Suite 400\n" +
                "Springfield, IL 62704\n" +
                "Phone: (217) 555-0199   Fax: (217) 555-0198\n" +
                "Account # 9948210   Order # 88472\n" +
                "Accession ID: ACC-2026-99381\n" +
                "-----------------------------------------\n" +
                "WBC 12.5 x10^9/L 6.0 - 17.0\n" +
                "RBC 7.1 x10^12/L 5.5 - 8.5\n";

        List<LabTestItem> items = LabRuleEngine.parseReportText(noisyClinicText);
        // None of the address, phone, or accession numbers should be parsed as lab tests
        assertEquals(2, items.size());
        assertEquals("WBC", items.get(0).getTestName());
        assertEquals("RBC", items.get(1).getTestName());
    }

    @Test
    public void testVeterinaryAttentionTriageLevels() {
        // 1. Critical Hypoglycemia (<50 mg/dL) -> URGENT
        List<LabTestItem> urgentList = new ArrayList<>();
        urgentList.add(new LabTestItem("Glucose", "38", "mg/dL", "70 - 150", LabTestItem.STATUS_LOW));
        urgentList.add(new LabTestItem("Creatinine", "1.2", "mg/dL", "0.5 - 1.5", LabTestItem.STATUS_NORMAL));

        String attention1 = LabAiService.assessVeterinaryAttention(urgentList);
        assertEquals("Urgent Veterinary Attention Required", attention1);

        // 2. Severe Azotemia (Creatinine > 5.0) -> URGENT
        List<LabTestItem> urgentList2 = new ArrayList<>();
        urgentList2.add(new LabTestItem("Creatinine", "6.2", "mg/dL", "0.5 - 1.5", LabTestItem.STATUS_HIGH));
        String attention2 = LabAiService.assessVeterinaryAttention(urgentList2);
        assertEquals("Urgent Veterinary Attention Required", attention2);

        // 3. Prompt review with non-critical abnormal parameter
        List<LabTestItem> promptList = new ArrayList<>();
        promptList.add(new LabTestItem("ALT", "135", "U/L", "10 - 100", LabTestItem.STATUS_HIGH));
        String attentionPrompt = LabAiService.assessVeterinaryAttention(promptList);
        assertEquals("Prompt Veterinary Consultation Recommended", attentionPrompt);

        // 4. Routine review with all normal parameters
        List<LabTestItem> normalList = new ArrayList<>();
        String attentionRoutine = LabAiService.assessVeterinaryAttention(normalList);
        assertEquals("Routine Wellness Review", attentionRoutine);
    }

    @Test
    public void testPrecautionsContainNoPrescriptionsOrDosages() {
        List<LabTestItem> abnormalItems = new ArrayList<>();
        abnormalItems.add(new LabTestItem("ALT", "195", "U/L", "10 - 100", LabTestItem.STATUS_HIGH));
        abnormalItems.add(new LabTestItem("BUN", "45", "mg/dL", "9 - 29", LabTestItem.STATUS_HIGH));
        abnormalItems.add(new LabTestItem("Creatinine", "2.8", "mg/dL", "0.5 - 1.5", LabTestItem.STATUS_HIGH));

        List<String> precautions = LabAiService.generateTailoredPrecautions("Dog", abnormalItems);
        assertNotNull(precautions);
        assertFalse(precautions.isEmpty());

        for (String precaution : precautions) {
            String lower = precaution.toLowerCase();
            // Must never prescribe medications, mg/kg, or administer unprescribed drugs
            assertFalse(lower.contains("mg/kg"));
            assertFalse(lower.contains("prescribe 10mg") || lower.contains("give 5mg"));
            assertFalse(lower.contains("administer amoxicillin"));
            assertFalse(lower.contains("administer steroid"));
            assertFalse(lower.contains("give meloxicam"));
        }
    }
}
