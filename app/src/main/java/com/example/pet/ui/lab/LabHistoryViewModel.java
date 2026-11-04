package com.example.pet.ui.lab;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.pet.data.LabRecord;
import com.example.pet.data.LabRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel managing the historical archive of laboratory records for a selected pet.
 */
public class LabHistoryViewModel extends ViewModel {

    public enum HistoryFilter {
        ALL,
        ABNORMAL_ONLY,
        NORMAL_ONLY
    }

    private final LabRepository repository = new LabRepository();

    private final MutableLiveData<List<LabRecord>> allRecords = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<LabRecord>> displayedRecords = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private HistoryFilter currentFilter = HistoryFilter.ALL;
    private String currentQuery = "";

    public LiveData<List<LabRecord>> getDisplayedRecords() {
        return displayedRecords;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void loadRecordsForPet(@NonNull String petId) {
        if (petId.trim().isEmpty()) {
            errorMessage.setValue("Invalid Pet ID.");
            return;
        }

        isLoading.setValue(true);
        repository.loadLabRecords(petId, new LabRepository.LabRecordsCallback() {
            @Override
            public void onSuccess(List<LabRecord> records) {
                isLoading.setValue(false);
                allRecords.setValue(records != null ? records : new ArrayList<>());
                applyFilterAndSearch();
            }

            @Override
            public void onError(Exception e) {
                isLoading.setValue(false);
                errorMessage.setValue("Failed to load records: " + e.getMessage());
            }
        });
    }

    public void setFilter(HistoryFilter filter) {
        this.currentFilter = filter != null ? filter : HistoryFilter.ALL;
        applyFilterAndSearch();
    }

    public void setSearchQuery(String query) {
        this.currentQuery = query != null ? query.trim().toLowerCase() : "";
        applyFilterAndSearch();
    }

    public void deleteRecord(LabRecord record, Runnable onDone) {
        deleteRecord(null, record, onDone);
    }

    public void deleteRecord(Context context, LabRecord record, Runnable onDone) {
        if (record == null) return;
        isLoading.setValue(true);
        repository.deleteLabRecord(context, record.getPetId(), record.getId(), record.getStoragePath(), new LabRepository.DeleteLabRecordCallback() {
            @Override
            public void onSuccess() {
                isLoading.setValue(false);
                List<LabRecord> current = allRecords.getValue() != null ? new ArrayList<>(allRecords.getValue()) : new ArrayList<>();
                current.remove(record);
                allRecords.setValue(current);
                applyFilterAndSearch();
                if (onDone != null) onDone.run();
            }

            @Override
            public void onError(Exception e) {
                isLoading.setValue(false);
                errorMessage.setValue("Failed to delete report: " + e.getMessage());
            }
        });
    }

    private void applyFilterAndSearch() {
        List<LabRecord> source = allRecords.getValue() != null ? allRecords.getValue() : new ArrayList<>();
        List<LabRecord> filtered = new ArrayList<>();

        for (LabRecord record : source) {
            if (currentFilter == HistoryFilter.ABNORMAL_ONLY && record.getAbnormalCount() == 0) {
                continue;
            }
            if (currentFilter == HistoryFilter.NORMAL_ONLY && record.getAbnormalCount() > 0) {
                continue;
            }

            if (!currentQuery.isEmpty()) {
                String lab = record.getLabName() != null ? record.getLabName().toLowerCase() : "";
                String file = record.getFileName() != null ? record.getFileName().toLowerCase() : "";
                String pet = record.getPetName() != null ? record.getPetName().toLowerCase() : "";
                if (!lab.contains(currentQuery) && !file.contains(currentQuery) && !pet.contains(currentQuery)) {
                    continue;
                }
            }

            filtered.add(record);
        }

        displayedRecords.setValue(filtered);
    }
}
