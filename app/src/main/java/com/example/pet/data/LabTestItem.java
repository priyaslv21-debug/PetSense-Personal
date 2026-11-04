package com.example.pet.data;

import androidx.annotation.Keep;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a single diagnostic or laboratory test parameter extracted from a lab report
 * (e.g., ALT, Creatinine, Hemoglobin, WBC).
 *
 * Firestore requires a public no-arg constructor and public getters/setters
 * to serialize and deserialize documents automatically.
 */
@Keep
public class LabTestItem implements Serializable {

    // Status Constants
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_HIGH = "HIGH";
    public static final String STATUS_LOW = "LOW";
    public static final String STATUS_UNCLASSIFIED = "UNCLASSIFIED";
    public static final String STATUS_INCOMPLETE = "INCOMPLETE";

    private String testName;
    private String category;            // e.g., "Liver Panel", "CBC", "Kidney Panel"
    private String value;               // Display value (numeric string or qualitative e.g. "Negative")
    private Double numericValue;        // Numeric representation for calculations (null for qualitative)
    private String unit;                // Measurement unit e.g. "U/L", "mg/dL", "%"
    private Double refRangeLow;         // Lower reference boundary from report
    private Double refRangeHigh;        // Upper reference boundary from report
    private String refRangeText;        // Raw reference interval string e.g. "10 - 100", "< 5.0"
    private String status;              // NORMAL | HIGH | LOW | UNCLASSIFIED | INCOMPLETE
    private String aiExplanation;       // Plain-language medical context
    private String clinicalSignificance;// Possible medical significance of abnormal finding
    private String recommendedFollowUp; // Actionable veterinary advice/follow-up
    private boolean isUncertain;        // Flagged if OCR or range extraction has low confidence
    private String uncertainReason;     // Explanation for why extraction was flagged uncertain
    private boolean isSampleQualityWarning; // True for sample-level observations like Hemolysis, Lipemia

    /**
     * Required no-arg constructor for Firestore deserialization.
     */
    public LabTestItem() {
        this.status = STATUS_UNCLASSIFIED;
        this.isUncertain = false;
        this.isSampleQualityWarning = false;
    }

    public LabTestItem(String testName, String value, String unit, String refRangeText, String status) {
        this();
        this.testName = testName;
        this.value = value;
        this.unit = unit;
        this.refRangeText = refRangeText;
        this.status = status != null ? status : STATUS_UNCLASSIFIED;
        if (value != null) {
            try {
                this.numericValue = Double.parseDouble(value.trim().replace(',', '.'));
            } catch (Exception ignored) {}
        }
    }

    public LabTestItem(String testName, String category, String value, Double numericValue,
                       String unit, Double refRangeLow, Double refRangeHigh, String refRangeText,
                       String status, String aiExplanation) {
        this(testName, value, unit, refRangeText, status);
        this.category = category;
        this.numericValue = numericValue;
        this.refRangeLow = refRangeLow;
        this.refRangeHigh = refRangeHigh;
        this.aiExplanation = aiExplanation;
    }

    // Getters and Setters
    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Double getNumericValue() {
        if (numericValue != null) return numericValue;
        if (value != null) {
            try {
                return Double.parseDouble(value.trim().replace(',', '.'));
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void setNumericValue(Double numericValue) {
        this.numericValue = numericValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getRefRangeLow() {
        return refRangeLow;
    }

    public void setRefRangeLow(Double refRangeLow) {
        this.refRangeLow = refRangeLow;
    }

    public Double getRefRangeHigh() {
        return refRangeHigh;
    }

    public void setRefRangeHigh(Double refRangeHigh) {
        this.refRangeHigh = refRangeHigh;
    }

    public String getRefRangeText() {
        return refRangeText;
    }

    public void setRefRangeText(String refRangeText) {
        this.refRangeText = refRangeText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAiExplanation() {
        return aiExplanation;
    }

    public void setAiExplanation(String aiExplanation) {
        this.aiExplanation = aiExplanation;
    }

    public String getClinicalSignificance() {
        return clinicalSignificance;
    }

    public void setClinicalSignificance(String clinicalSignificance) {
        this.clinicalSignificance = clinicalSignificance;
    }

    public String getRecommendedFollowUp() {
        return recommendedFollowUp;
    }

    public void setRecommendedFollowUp(String recommendedFollowUp) {
        this.recommendedFollowUp = recommendedFollowUp;
    }

    public boolean isUncertain() {
        return isUncertain;
    }

    public void setUncertain(boolean uncertain) {
        isUncertain = uncertain;
    }

    public String getUncertainReason() {
        return uncertainReason;
    }

    public void setUncertainReason(String uncertainReason) {
        this.uncertainReason = uncertainReason;
    }

    public boolean isSampleQualityWarning() {
        return isSampleQualityWarning;
    }

    public void setSampleQualityWarning(boolean sampleQualityWarning) {
        isSampleQualityWarning = sampleQualityWarning;
    }

    /**
     * Checks whether this result deviates from the report reference range.
     */
    public boolean isAbnormal() {
        return STATUS_HIGH.equalsIgnoreCase(status) || STATUS_LOW.equalsIgnoreCase(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabTestItem that = (LabTestItem) o;
        return Objects.equals(testName, that.testName) &&
                Objects.equals(value, that.value) &&
                Objects.equals(unit, that.unit) &&
                Objects.equals(refRangeText, that.refRangeText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testName, value, unit, refRangeText);
    }
}
