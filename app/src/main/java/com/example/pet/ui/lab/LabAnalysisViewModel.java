package com.example.pet.ui.lab;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.pet.data.LabRecord;
import com.example.pet.data.LabRepository;
import com.example.pet.data.LabTestItem;
import com.example.pet.lab.LabAiService;
import com.example.pet.lab.LabDocumentParser;
import com.example.pet.lab.LabRuleEngine;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ViewModel managing state for lab report upload, OCR extraction,
 * user verification/correction, AI explanation, and persistence.
 */
public class LabAnalysisViewModel extends ViewModel {

    private static final String TAG = "LabAnalysisViewModel";

    private final LabRepository repository = new LabRepository();
    private final LabAiService aiService = new LabAiService();

    // Pet Context
    private final MutableLiveData<String> petId = new MutableLiveData<>();
    private final MutableLiveData<String> petName = new MutableLiveData<>("Pet");
    private final MutableLiveData<String> species = new MutableLiveData<>("Dog");

    // Document State
    private Uri selectedFileUri;
    private String selectedFileName;
    private String selectedMimeType;
    private final MutableLiveData<Integer> pageCount = new MutableLiveData<>(1);
    private final MutableLiveData<String> rawExtractedText = new MutableLiveData<>("");

    // Test Items State (Editable during OCR Verification)
    private final MutableLiveData<List<LabTestItem>> testItems = new MutableLiveData<>(new ArrayList<>());

    // AI & Clinical Insights
    private final MutableLiveData<String> aiSummary = new MutableLiveData<>("");
    private final MutableLiveData<String> vetRecommendations = new MutableLiveData<>("");
    private final MutableLiveData<String> vetQuestions = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isAiAvailable = new MutableLiveData<>(true);

    // UI Status
    private final MutableLiveData<Boolean> isProcessing = new MutableLiveData<>(false);
    private final MutableLiveData<String> processingMessage = new MutableLiveData<>("");
    private final MutableLiveData<Integer> uploadProgress = new MutableLiveData<>(0);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<LabRecord> savedRecord = new MutableLiveData<>();

    // Getters
    public LiveData<String> getPetName() { return petName; }
    public LiveData<String> getSpecies() { return species; }
    public LiveData<Integer> getPageCount() { return pageCount; }
    public LiveData<String> getRawExtractedText() { return rawExtractedText; }
    public LiveData<List<LabTestItem>> getTestItems() { return testItems; }
    public LiveData<String> getAiSummary() { return aiSummary; }
    public LiveData<String> getVetRecommendations() { return vetRecommendations; }
    public LiveData<String> getVetQuestions() { return vetQuestions; }
    public LiveData<Boolean> getIsAiAvailable() { return isAiAvailable; }
    public LiveData<Boolean> getIsProcessing() { return isProcessing; }
    public LiveData<String> getProcessingMessage() { return processingMessage; }
    public LiveData<Integer> getUploadProgress() { return uploadProgress; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<LabRecord> getSavedRecord() { return savedRecord; }

    public Uri getSelectedFileUri() { return selectedFileUri; }
    public String getSelectedFileName() { return selectedFileName; }
    public String getSelectedMimeType() { return selectedMimeType; }

    public void initPetContext(String id, String name, String spec) {
        if (id != null) petId.setValue(id);
        if (name != null && !name.trim().isEmpty()) petName.setValue(name);
        if (spec != null && !spec.trim().isEmpty()) species.setValue(spec);
    }

    public void setSelectedDocument(Uri uri, String fileName, String mimeType) {
        this.selectedFileUri = uri;
        this.selectedFileName = fileName;
        this.selectedMimeType = mimeType;
    }

    /**
     * Executes OCR document parsing (PDF pages or Image) in background.
     */
    public void startExtraction(@NonNull Context context, @NonNull Runnable onExtractionComplete) {
        if (selectedFileUri == null) {
            errorMessage.setValue("Please select a valid report document.");
            return;
        }

        isProcessing.setValue(true);
        processingMessage.setValue("Reading document text with on-device OCR...");

        LabDocumentParser parser = new LabDocumentParser(context);
        parser.parseDocumentAsync(selectedFileUri, selectedMimeType != null ? selectedMimeType : "application/pdf",
                new LabDocumentParser.ParserCallback() {
                    @Override
                    public void onSuccess(List<LabTestItem> parsedItems, String rawText, int pages) {
                        isProcessing.setValue(false);
                        pageCount.setValue(pages);
                        rawExtractedText.setValue(rawText);
                        testItems.setValue(parsedItems);
                        parser.close();
                        onExtractionComplete.run();
                    }

                    @Override
                    public void onError(Exception e) {
                        isProcessing.setValue(false);
                        parser.close();
                        errorMessage.setValue(e.getMessage() != null ? e.getMessage() : "Failed to extract text from document.");
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    //  OCR Verification Editing Operations
    // ─────────────────────────────────────────────────────────────

    public void updateItem(int position, LabTestItem updatedItem) {
        List<LabTestItem> current = testItems.getValue() != null ? new ArrayList<>(testItems.getValue()) : new ArrayList<>();
        if (position >= 0 && position < current.size()) {
            LabRuleEngine.classifyTestItem(updatedItem);
            current.set(position, updatedItem);
            testItems.setValue(current);
        }
    }

    public void addItem(LabTestItem newItem) {
        List<LabTestItem> current = testItems.getValue() != null ? new ArrayList<>(testItems.getValue()) : new ArrayList<>();
        LabRuleEngine.classifyTestItem(newItem);
        current.add(newItem);
        testItems.setValue(current);
    }

    public void deleteItem(int position) {
        List<LabTestItem> current = testItems.getValue() != null ? new ArrayList<>(testItems.getValue()) : new ArrayList<>();
        if (position >= 0 && position < current.size()) {
            current.remove(position);
            testItems.setValue(current);
        }
    }

    /**
     * Saves the verified lab report directly using report reference ranges,
     * without triggering AI analysis or medical diagnosis.
     */
    public void saveVerifiedReport(String labName, Date reportDate, Runnable onComplete) {
        saveVerifiedReport(null, labName, reportDate, onComplete);
    }

    public void saveVerifiedReport(@Nullable Context context, String labName, Date reportDate, Runnable onComplete) {
        List<LabTestItem> current = testItems.getValue() != null ? testItems.getValue() : new ArrayList<>();
        if (current.isEmpty()) {
            errorMessage.setValue("No lab test parameters found. Please add at least one test.");
            return;
        }

        // Re-classify all items strictly with the rule engine using report reference ranges only
        for (LabTestItem item : current) {
            LabRuleEngine.classifyTestItem(item);
        }
        testItems.setValue(current);

        isProcessing.setValue(true);
        uploadAndSaveRecord(context, labName, reportDate, onComplete);
    }

    /**
     * Executes the deterministic rule engine re-check across all verified items,
     * calls the secure AI service, and persists the record and document locally.
     * If AI is unavailable, displays rule-based results WITHOUT generating a fake AI summary.
     */
    public void confirmAndAnalyze(@NonNull Context context, String labName, Date reportDate, Runnable onComplete) {
        List<LabTestItem> current = testItems.getValue() != null ? testItems.getValue() : new ArrayList<>();
        if (current.isEmpty()) {
            errorMessage.setValue("No lab test parameters found. Please add at least one test.");
            return;
        }

        // Re-classify all items strictly with the rule engine
        for (LabTestItem item : current) {
            LabRuleEngine.classifyTestItem(item);
        }
        testItems.setValue(current);

        isProcessing.setValue(true);
        processingMessage.setValue("Generating AI explanations and clinical summary...");

        String currentSpecies = species.getValue() != null ? species.getValue() : "Dog";
        String currentPetName = petName.getValue() != null ? petName.getValue() : "Pet";

        aiService.analyzeReportAsync(currentSpecies, currentPetName, current, new LabAiService.AiAnalysisCallback() {
            @Override
            public void onSuccess(String overallSummary, String recommendations, String questions, List<LabTestItem> itemsWithExplanations) {
                isAiAvailable.setValue(true);
                aiSummary.setValue(overallSummary);
                vetRecommendations.setValue(recommendations);
                vetQuestions.setValue(questions);
                testItems.setValue(itemsWithExplanations);

                uploadAndSaveRecord(context, labName, reportDate, onComplete);
            }

            @Override
            public void onAiUnavailable(String noticeMessage, List<LabTestItem> ruleBasedItemsOnly) {
                // If AI is unavailable, display the rule-based results without generating a fake AI summary.
                isAiAvailable.setValue(false);
                aiSummary.setValue(null);
                vetRecommendations.setValue(null);
                vetQuestions.setValue(null);
                testItems.setValue(ruleBasedItemsOnly);

                uploadAndSaveRecord(context, labName, reportDate, onComplete);
            }
        });
    }

    public void confirmAndAnalyze(String labName, Date reportDate, Runnable onComplete) {
        confirmAndAnalyze(null, labName, reportDate, onComplete);
    }

    private void uploadAndSaveRecord(@Nullable Context context, String labName, Date reportDate, Runnable onComplete) {
        String currentPetId = petId.getValue();
        if (currentPetId == null || currentPetId.trim().isEmpty()) {
            isProcessing.setValue(false);
            errorMessage.setValue("No active pet selected.");
            return;
        }

        processingMessage.setValue("Saving original document to secure local storage...");

        String tempRecordId = "lab_" + System.currentTimeMillis();
        String fileName = selectedFileName != null ? selectedFileName : "lab_report.pdf";

        if (context != null && selectedFileUri != null) {
            repository.saveReportFileLocally(context, currentPetId, tempRecordId, selectedFileUri, fileName, new LabRepository.UploadFileCallback() {
                @Override
                public void onSuccess(String localFilePath, String storagePath) {
                    finalizeFirestoreSave(currentPetId, labName, reportDate, localFilePath, storagePath, fileName, onComplete);
                }

                @Override
                public void onProgress(int progressPercent) {
                    uploadProgress.setValue(progressPercent);
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "Local file saving failed; saving record with local reference: " + e.getMessage());
                    finalizeFirestoreSave(currentPetId, labName, reportDate, selectedFileUri.toString(), null, fileName, onComplete);
                }
            });
        } else {
            finalizeFirestoreSave(currentPetId, labName, reportDate, selectedFileUri != null ? selectedFileUri.toString() : null, null, fileName, onComplete);
        }
    }

    private void finalizeFirestoreSave(String currentPetId, String labName, Date reportDate,
                                       String downloadUrl, String storagePath, String fileName,
                                       Runnable onComplete) {
        processingMessage.setValue("Saving report to pet medical record...");

        LabRecord record = new LabRecord(
                currentPetId,
                petName.getValue(),
                species.getValue(),
                reportDate != null ? reportDate : new Date(),
                labName != null ? labName : "Laboratory Diagnostics",
                downloadUrl,
                storagePath,
                fileName,
                selectedMimeType != null ? selectedMimeType : "application/pdf"
        );

        record.setAiSummary(aiSummary.getValue());
        record.setVetRecommendations(vetRecommendations.getValue());
        record.setVetQuestions(vetQuestions.getValue());
        record.setTests(testItems.getValue());

        repository.addLabRecord(currentPetId, record, new LabRepository.SaveLabRecordCallback() {
            @Override
            public void onSuccess(LabRecord saved) {
                isProcessing.setValue(false);
                savedRecord.setValue(saved);
                onComplete.run();
            }

            @Override
            public void onError(Exception e) {
                isProcessing.setValue(false);
                errorMessage.setValue("Failed to save report: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        aiService.shutdown();
    }
}
