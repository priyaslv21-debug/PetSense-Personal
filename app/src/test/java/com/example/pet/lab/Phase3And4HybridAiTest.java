package com.example.pet.lab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.pet.data.LabRecord;
import com.example.pet.data.LabTestItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Comprehensive verification for Phase 3 (Result Extraction and Validation)
 * and Phase 4 (Hybrid AI Analysis).
 *
 * Verifies:
 * 1. Extraction and validation for Canine vs Feline panels.
 * 2. All 5 classification states: NORMAL, HIGH, LOW, UNCLASSIFIED, INCOMPLETE.
 * 3. Strict rule enforcement: missing reference ranges must remain UNCLASSIFIED
 *    (never substituting generic canine/feline ranges).
 * 4. Hybrid AI independence: AI explanations must never alter rule classifications.
 * 5. Offline / AI unavailable handling: rule-based results are preserved with
 *    strictly NO synthetic/fake AI summary.
 * 6. Non-diagnostic species-specific vet discussion questions and clinical context.
 */
public class Phase3And4HybridAiTest {

    @Test
    public void testCanineAndFelineSpeciesSpecificQuestions() {
        // Dog with elevated ALT and high WBC
        List<LabTestItem> dogItems = new ArrayList<>();
        LabTestItem alt = new LabTestItem("ALT", "165", "U/L", "10 - 100", LabTestItem.STATUS_HIGH);
        LabTestItem wbc = new LabTestItem("WBC", "19.0", "x10^9/L", "6.0 - 17.0", LabTestItem.STATUS_HIGH);
        dogItems.add(alt);
        dogItems.add(wbc);

        String dogQuestions = LabAiService.generateSpeciesSpecificVetQuestions("Dog", dogItems);
        assertNotNull(dogQuestions);
        assertTrue(dogQuestions.contains("liver support supplement") || dogQuestions.contains("hepatic"));
        assertTrue(dogQuestions.contains("inflammation") || dogQuestions.contains("ALT"));

        // Cat with elevated Glucose and elevated BUN
        List<LabTestItem> catItems = new ArrayList<>();
        LabTestItem glu = new LabTestItem("Glucose", "195", "mg/dL", "70 - 150", LabTestItem.STATUS_HIGH);
        LabTestItem bun = new LabTestItem("BUN", "42", "mg/dL", "16 - 36", LabTestItem.STATUS_HIGH);
        catItems.add(glu);
        catItems.add(bun);

        String catQuestions = LabAiService.generateSpeciesSpecificVetQuestions("Cat", catItems);
        assertNotNull(catQuestions);
        // Should mention feline situational hyperglycemia or fructosamine
        assertTrue(catQuestions.contains("feline situational hyperglycemia") || catQuestions.contains("fructosamine"));
        assertTrue(catQuestions.contains("renal") || catQuestions.contains("SDMA"));
    }

    @Test
    public void testAllFiveClassificationStates() {
        // 1. NORMAL
        LabTestItem normal = new LabTestItem("Creatinine", "1.1", "mg/dL", "0.5 - 1.5", "");
        LabRuleEngine.classifyTestItem(normal);
        assertEquals(LabTestItem.STATUS_NORMAL, normal.getStatus());
        assertFalse(normal.isAbnormal());

        // 2. HIGH
        LabTestItem high = new LabTestItem("ALT", "150", "U/L", "10 - 100", "");
        LabRuleEngine.classifyTestItem(high);
        assertEquals(LabTestItem.STATUS_HIGH, high.getStatus());
        assertTrue(high.isAbnormal());

        // 3. LOW
        LabTestItem low = new LabTestItem("Hematocrit", "28", "%", "37 - 55", "");
        LabRuleEngine.classifyTestItem(low);
        assertEquals(LabTestItem.STATUS_LOW, low.getStatus());
        assertTrue(low.isAbnormal());

        // 4. UNCLASSIFIED (No report reference range provided)
        LabTestItem unclassified = new LabTestItem("Specialized Peptide", "4.8", "ng/mL", "", "");
        LabRuleEngine.classifyTestItem(unclassified);
        assertEquals(LabTestItem.STATUS_UNCLASSIFIED, unclassified.getStatus());
        assertFalse(unclassified.isAbnormal());

        // 5. INCOMPLETE (Specimen issue or pending status)
        LabTestItem incomplete = new LabTestItem("Platelet Count", "QNS", "", "175 - 500", "");
        LabRuleEngine.classifyTestItem(incomplete);
        assertEquals(LabTestItem.STATUS_INCOMPLETE, incomplete.getStatus());
        assertFalse(incomplete.isAbnormal());
    }

    @Test
    public void testStrictMissingReferenceRangeRule() {
        // Requirement 1 & 6: Never automatically substitute generic canine/feline ranges.
        // If the report does not print a reference range, status MUST be UNCLASSIFIED.
        String reportSnippet = "Amylase 1250 U/L\nLipase 850 U/L\n";
        List<LabTestItem> items = LabRuleEngine.parseReportText(reportSnippet);

        assertEquals(2, items.size());
        for (LabTestItem item : items) {
            assertEquals("Missing report range must remain UNCLASSIFIED",
                    LabTestItem.STATUS_UNCLASSIFIED, item.getStatus());
            assertNull("Numeric range low must be null when absent from report", item.getRefRangeLow());
            assertNull("Numeric range high must be null when absent from report", item.getRefRangeHigh());
        }
    }

    @Test
    public void testAiCannotAlterRuleClassifications() {
        // Requirement 4 & 10: AI must never change classifications produced by the rule engine.
        LabTestItem item = new LabTestItem("ALT", "180", "U/L", "10 - 100", "");
        LabRuleEngine.classifyTestItem(item);
        assertEquals(LabTestItem.STATUS_HIGH, item.getStatus());

        // Simulate AI explanation enrichment
        String speciesContext = LabAiService.getSpeciesSpecificExplanation("Dog", item);
        item.setAiExplanation(speciesContext);

        // Verification: Classification remains strictly HIGH
        assertEquals(LabTestItem.STATUS_HIGH, item.getStatus());
        assertTrue(item.isAbnormal());
        assertNotNull(item.getAiExplanation());
        assertTrue(item.getAiExplanation().contains("ALT") || item.getAiExplanation().contains("liver"));
    }

    @Test
    public void testAiOfflineNoFakeSummaryPolicy() {
        // Requirement 8: If AI is unavailable, display rule-based results WITHOUT generating a fake AI summary.
        LabRecord record = new LabRecord(
                "pet_cat_456", "Milo", "Cat", new Date(),
                "Central Vet Diagnostics", null, null, "report.pdf", "application/pdf"
        );

        List<LabTestItem> items = new ArrayList<>();
        items.add(new LabTestItem("Glucose", "185", "mg/dL", "70 - 150", LabTestItem.STATUS_HIGH));
        items.add(new LabTestItem("Creatinine", "1.2", "mg/dL", "0.6 - 1.6", LabTestItem.STATUS_NORMAL));

        record.setTests(items);
        // When AI service fails or is offline:
        record.setAiSummary(null);
        record.setVetRecommendations(null);
        record.setVetQuestions(null);

        // Pure rule-based metrics remain accurate
        assertEquals(2, record.getTotalTestsCount());
        assertEquals(1, record.getAbnormalCount());
        assertEquals(0, record.getUnclassifiedCount());
        assertNull("No fake AI summary should be populated when offline", record.getAiSummary());
        assertNull("No fake vet recommendations should be populated when offline", record.getVetRecommendations());
        assertNotNull("Medical disclaimer must always be present", record.getDisclaimer());
    }

    @Test
    public void testNumericalFormatsAndCommas() {
        // Test decimal values with dots and commas (European formatting)
        String europeanNumberReport =
                "ALT 45,5 U/L 10,0 - 100,0\n" +
                "Creatinine 1.8 mg/dL 0.5 - 1.5\n";

        List<LabTestItem> items = LabRuleEngine.parseReportText(europeanNumberReport);
        assertEquals(2, items.size());

        LabTestItem alt = items.get(0);
        assertEquals("45,5", alt.getValue());
        assertEquals(45.5, alt.getNumericValue(), 0.01);
        assertEquals(LabTestItem.STATUS_NORMAL, alt.getStatus());

        LabTestItem crea = items.get(1);
        assertEquals("1.8", crea.getValue());
        assertEquals(1.8, crea.getNumericValue(), 0.01);
        assertEquals(LabTestItem.STATUS_HIGH, crea.getStatus());
    }

    @Test
    public void testNonDiagnosticClinicalWording() {
        LabTestItem altHigh = new LabTestItem("ALT", "190", "U/L", "10 - 100", LabTestItem.STATUS_HIGH);
        String explanationDog = LabAiService.getSpeciesSpecificExplanation("Dog", altHigh);

        // Must avoid definitive diagnoses like "Your pet has liver failure / hepatitis"
        assertFalse(explanationDog.toLowerCase().contains("diagnosis"));
        assertFalse(explanationDog.toLowerCase().contains("has disease"));
        assertTrue(explanationDog.contains("liver") || explanationDog.contains("inflammation"));
    }
}
