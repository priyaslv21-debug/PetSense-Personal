package com.example.pet.lab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.pet.data.LabTestItem;

import org.junit.Test;

import java.util.List;

public class LabRuleEngineTest {

    @Test
    public void testDogLabReportExtractionAndClassification() {
        String canineReport =
                "VETERINARY CLINIC LAB REPORT - CANINE\n" +
                "Patient: Buster (Canine / Golden Retriever)\n" +
                "Test Name      Result   Unit    Reference Interval\n" +
                "ALT            145      U/L     10 - 100\n" +
                "Creatinine     1.1      mg/dL   0.5 - 1.5\n" +
                "BUN            6        mg/dL   9 - 29\n" +
                "HCT            42       %       37 - 55\n" +
                "WBC            18.5     x10^9/L 6.0 - 17.0\n";

        List<LabTestItem> items = LabRuleEngine.parseReportText(canineReport);
        assertNotNull(items);
        assertEquals(5, items.size());

        // ALT (Elevated above report range 10-100)
        LabTestItem alt = items.get(0);
        assertEquals("ALT", alt.getTestName());
        assertEquals("145", alt.getValue());
        assertEquals("U/L", alt.getUnit());
        assertEquals(LabTestItem.STATUS_HIGH, alt.getStatus());
        assertTrue(alt.isAbnormal());

        // Creatinine (Normal within 0.5-1.5)
        LabTestItem crea = items.get(1);
        assertEquals("Creatinine", crea.getTestName());
        assertEquals("1.1", crea.getValue());
        assertEquals(LabTestItem.STATUS_NORMAL, crea.getStatus());
        assertFalse(crea.isAbnormal());

        // BUN (Low below 9-29)
        LabTestItem bun = items.get(2);
        assertEquals("BUN", bun.getTestName());
        assertEquals("6", bun.getValue());
        assertEquals(LabTestItem.STATUS_LOW, bun.getStatus());
        assertTrue(bun.isAbnormal());

        // HCT (Normal within 37-55)
        LabTestItem hct = items.get(3);
        assertEquals("HCT", hct.getTestName());
        assertEquals(LabTestItem.STATUS_NORMAL, hct.getStatus());

        // WBC (High above 6.0-17.0)
        LabTestItem wbc = items.get(4);
        assertEquals("WBC", wbc.getTestName());
        assertEquals(LabTestItem.STATUS_HIGH, wbc.getStatus());
    }

    @Test
    public void testCatLabReportExtractionAndClassification() {
        String felineReport =
                "ANIMAL HOSPITAL - FELINE WELLNESS PANEL\n" +
                "Patient: Luna (Feline / Domestic Shorthair)\n" +
                "GLU            185      mg/dL   70 - 150\n" +
                "Total T4       2.4      ug/dL   0.8 - 4.0\n" +
                "Phosphorus     4.5      mg/dL   2.5 - 6.0\n";

        List<LabTestItem> items = LabRuleEngine.parseReportText(felineReport);
        assertEquals(3, items.size());

        // Glucose elevated (e.g. stress hyperglycemia in cats)
        LabTestItem glu = items.get(0);
        assertEquals("GLU", glu.getTestName());
        assertEquals(LabTestItem.STATUS_HIGH, glu.getStatus());

        // Total T4 normal
        LabTestItem t4 = items.get(1);
        assertEquals("Total T4", t4.getTestName());
        assertEquals(LabTestItem.STATUS_NORMAL, t4.getStatus());
    }

    @Test
    public void testMissingReportRangeClassifiedAsUnclassified() {
        // Requirement 1: NEVER substitute generic ranges if report range is missing.
        // Must be marked UNCLASSIFIED.
        String rawLine = "Special Enzyme 55.4 U/L";

        List<LabTestItem> items = LabRuleEngine.parseReportText(rawLine);
        assertEquals(1, items.size());

        LabTestItem item = items.get(0);
        assertEquals("Special Enzyme", item.getTestName());
        assertEquals("55.4", item.getValue());
        assertEquals(LabTestItem.STATUS_UNCLASSIFIED, item.getStatus());
        assertFalse(item.isAbnormal());
    }

    @Test
    public void testQualitativeResults() {
        String text =
                "Giardia Antigen     Negative\n" +
                "FeLV Antigen        Negative\n" +
                "FIV Antibody        Positive\n";

        List<LabTestItem> items = LabRuleEngine.parseReportText(text);
        assertEquals(3, items.size());

        assertEquals(LabTestItem.STATUS_NORMAL, items.get(0).getStatus());
        assertEquals(LabTestItem.STATUS_NORMAL, items.get(1).getStatus());
        assertEquals(LabTestItem.STATUS_HIGH, items.get(2).getStatus());
    }

    @Test
    public void testIncompleteSpecimenResults() {
        String text =
                "Platelet Count      QNS\n" +
                "Bilirubin           Hemolyzed\n";

        List<LabTestItem> items = LabRuleEngine.parseReportText(text);
        assertEquals(2, items.size());

        assertEquals(LabTestItem.STATUS_INCOMPLETE, items.get(0).getStatus());
        assertEquals(LabTestItem.STATUS_INCOMPLETE, items.get(1).getStatus());
    }

    @Test
    public void testSingleSidedRanges() {
        String text =
                "Bile Acids     14.5     umol/L  < 10.0\n" +
                "Cobalamin      650      ng/L    > 250\n";

        List<LabTestItem> items = LabRuleEngine.parseReportText(text);
        assertEquals(2, items.size());

        // 14.5 is > 10.0 -> HIGH
        assertEquals(LabTestItem.STATUS_HIGH, items.get(0).getStatus());
        // 650 is > 250 -> NORMAL
        assertEquals(LabTestItem.STATUS_NORMAL, items.get(1).getStatus());
    }

    @Test
    public void testCategoryInference() {
        assertEquals("Liver Panel", LabRuleEngine.inferCategory("ALT"));
        assertEquals("Liver Panel", LabRuleEngine.inferCategory("ALKP"));
        assertEquals("Kidney Panel", LabRuleEngine.inferCategory("Creatinine"));
        assertEquals("Kidney Panel", LabRuleEngine.inferCategory("BUN"));
        assertEquals("Hematology (CBC)", LabRuleEngine.inferCategory("WBC"));
        assertEquals("Hematology (CBC)", LabRuleEngine.inferCategory("Platelet Count"));
        assertEquals("Electrolytes", LabRuleEngine.inferCategory("Sodium"));
        assertEquals("Endocrinology", LabRuleEngine.inferCategory("Total T4"));
    }
}
