package com.example.pet.data;

import androidx.annotation.Keep;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents a complete laboratory report document persisted in Cloud Firestore under:
 *   users/{uid}/pets/{petId}/lab_records/{labRecordId}
 *
 * POJO compliant with Firestore automatic serialization and deserialization
 * via DocumentSnapshot.toObject(LabRecord.class).
 */
@Keep
public class LabRecord implements Serializable {

    public static final String DEFAULT_DISCLAIMER =
            "Informational health summary only. Does not replace professional veterinary examination, diagnosis, or treatment.";

    private String id;
    private String petId;
    private String petName;
    private String species;                 // "Dog" or "Cat"
    private String petBreed;
    private String petAge;
    private String petGender;
    private String petWeight;
    private Date createdAt;
    private Date reportDate;
    private String labName;
    private String originalFileUrl;
    private String storagePath;
    private String fileName;
    private String fileType;                // "application/pdf" | "image/jpeg" | "image/png"
    private int totalTestsCount;
    private int abnormalCount;
    private int normalCount;
    private int highCount;
    private int lowCount;
    private int unclassifiedCount;
    private String aiSummary;
    private String vetRecommendations;
    private String vetQuestions;            // Useful questions for vet consultation
    private List<String> precautions = new ArrayList<>();
    private String veterinaryAttention;     // "Urgent", "Prompt", "Routine"
    private String veterinaryAttentionDetails;
    private List<String> sampleQualityWarnings = new ArrayList<>(); // Hemolysis, Lipemia, etc.
    private String disclaimer;
    private List<LabTestItem> tests = new ArrayList<>();

    /**
     * Required no-arg constructor for Firestore deserialization.
     */
    public LabRecord() {
        this.createdAt = new Date();
        this.disclaimer = DEFAULT_DISCLAIMER;
        this.precautions = new ArrayList<>();
        this.sampleQualityWarnings = new ArrayList<>();
        this.tests = new ArrayList<>();
    }

    public LabRecord(String petId, String petName, String species, Date reportDate,
                     String labName, String originalFileUrl, String storagePath,
                     String fileName, String fileType) {
        this();
        this.petId = petId;
        this.petName = petName;
        this.species = species;
        this.reportDate = reportDate != null ? reportDate : new Date();
        this.labName = labName;
        this.originalFileUrl = originalFileUrl;
        this.storagePath = storagePath;
        this.fileName = fileName;
        this.fileType = fileType;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPetId() {
        return petId;
    }

    public void setPetId(String petId) {
        this.petId = petId;
    }

    public String getPetName() {
        return petName;
    }

    public void setPetName(String petName) {
        this.petName = petName;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getPetBreed() {
        return petBreed;
    }

    public void setPetBreed(String petBreed) {
        this.petBreed = petBreed;
    }

    public String getPetAge() {
        return petAge;
    }

    public void setPetAge(String petAge) {
        this.petAge = petAge;
    }

    public String getPetGender() {
        return petGender;
    }

    public void setPetGender(String petGender) {
        this.petGender = petGender;
    }

    public String getPetWeight() {
        return petWeight;
    }

    public void setPetWeight(String petWeight) {
        this.petWeight = petWeight;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getReportDate() {
        return reportDate;
    }

    public void setReportDate(Date reportDate) {
        this.reportDate = reportDate;
    }

    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }

    public String getOriginalFileUrl() {
        return originalFileUrl;
    }

    public void setOriginalFileUrl(String originalFileUrl) {
        this.originalFileUrl = originalFileUrl;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public int getTotalTestsCount() {
        return totalTestsCount;
    }

    public void setTotalTestsCount(int totalTestsCount) {
        this.totalTestsCount = totalTestsCount;
    }

    public int getAbnormalCount() {
        return abnormalCount;
    }

    public void setAbnormalCount(int abnormalCount) {
        this.abnormalCount = abnormalCount;
    }

    public int getNormalCount() {
        return normalCount;
    }

    public void setNormalCount(int normalCount) {
        this.normalCount = normalCount;
    }

    public int getHighCount() {
        return highCount;
    }

    public void setHighCount(int highCount) {
        this.highCount = highCount;
    }

    public int getLowCount() {
        return lowCount;
    }

    public void setLowCount(int lowCount) {
        this.lowCount = lowCount;
    }

    public int getUnclassifiedCount() {
        return unclassifiedCount;
    }

    public void setUnclassifiedCount(int unclassifiedCount) {
        this.unclassifiedCount = unclassifiedCount;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public String getVetRecommendations() {
        return vetRecommendations;
    }

    public void setVetRecommendations(String vetRecommendations) {
        this.vetRecommendations = vetRecommendations;
    }

    public String getVetQuestions() {
        return vetQuestions;
    }

    public void setVetQuestions(String vetQuestions) {
        this.vetQuestions = vetQuestions;
    }

    public List<String> getPrecautions() {
        return precautions != null ? precautions : new ArrayList<>();
    }

    public void setPrecautions(List<String> precautions) {
        this.precautions = precautions != null ? precautions : new ArrayList<>();
    }

    public String getVeterinaryAttention() {
        return veterinaryAttention;
    }

    public void setVeterinaryAttention(String veterinaryAttention) {
        this.veterinaryAttention = veterinaryAttention;
    }

    public String getVeterinaryAttentionDetails() {
        return veterinaryAttentionDetails;
    }

    public void setVeterinaryAttentionDetails(String veterinaryAttentionDetails) {
        this.veterinaryAttentionDetails = veterinaryAttentionDetails;
    }

    public List<String> getSampleQualityWarnings() {
        return sampleQualityWarnings != null ? sampleQualityWarnings : new ArrayList<>();
    }

    public void setSampleQualityWarnings(List<String> sampleQualityWarnings) {
        this.sampleQualityWarnings = sampleQualityWarnings != null ? sampleQualityWarnings : new ArrayList<>();
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }

    public List<LabTestItem> getTests() {
        return tests != null ? tests : new ArrayList<>();
    }

    public void setTests(List<LabTestItem> tests) {
        this.tests = tests != null ? tests : new ArrayList<>();
        recalculateCounts();
    }

    /**
     * Updates total, abnormal, normal, high, low, and unclassified test metrics.
     * Sample quality warnings are excluded from normal/abnormal test counts.
     */
    public void recalculateCounts() {
        if (tests == null || tests.isEmpty()) {
            totalTestsCount = 0;
            abnormalCount = 0;
            normalCount = 0;
            highCount = 0;
            lowCount = 0;
            unclassifiedCount = 0;
            return;
        }

        int total = 0;
        int abnormal = 0;
        int normal = 0;
        int high = 0;
        int low = 0;
        int unclassified = 0;

        for (LabTestItem item : tests) {
            if (item == null) continue;
            // Exclude sample-quality warnings from test metrics
            if (item.isSampleQualityWarning()) continue;

            total++;
            String status = item.getStatus() != null ? item.getStatus() : LabTestItem.STATUS_UNCLASSIFIED;
            if (LabTestItem.STATUS_HIGH.equalsIgnoreCase(status)) {
                high++;
                abnormal++;
            } else if (LabTestItem.STATUS_LOW.equalsIgnoreCase(status)) {
                low++;
                abnormal++;
            } else if (LabTestItem.STATUS_NORMAL.equalsIgnoreCase(status)) {
                normal++;
            } else if (LabTestItem.STATUS_UNCLASSIFIED.equalsIgnoreCase(status)) {
                unclassified++;
            }
        }

        this.totalTestsCount = total;
        this.abnormalCount = abnormal;
        this.normalCount = normal;
        this.highCount = high;
        this.lowCount = low;
        this.unclassifiedCount = unclassified;
    }
}
