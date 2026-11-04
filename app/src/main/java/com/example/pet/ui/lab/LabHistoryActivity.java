package com.example.pet.ui.lab;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pet.data.LabRecord;
import com.example.pet.data.LabTestItem;
import com.example.pet.data.Pet;
import com.example.pet.data.PetRepository;
import com.example.pet.databinding.ActivityLabHistoryBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Screen displaying the historical archive of pet laboratory reports.
 * Allows pet owners to browse past blood panels, filter by abnormal/normal results,
 * search parameters, share reports, delete reports with confirmation,
 * and launch new report uploads.
 */
public class LabHistoryActivity extends AppCompatActivity implements LabHistoryAdapter.OnReportActionListener {

    public static final String EXTRA_PET_ID = "petId";
    public static final String EXTRA_PET_NAME = "petName";
    public static final String EXTRA_PET_SPECIES = "petSpecies";

    private ActivityLabHistoryBinding binding;
    private LabHistoryViewModel viewModel;
    private LabHistoryAdapter adapter;

    private String petId = "";
    private String petName = "Pet";
    private String species = "Dog";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLabHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LabHistoryViewModel.class);

        extractIntentExtras(getIntent());
        setupViews();
        observeViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (petId != null && !petId.isEmpty()) {
            viewModel.loadRecordsForPet(petId);
        } else {
            fetchDefaultPetAndLoad();
        }
    }

    private void extractIntentExtras(Intent intent) {
        if (intent != null) {
            if (intent.hasExtra(EXTRA_PET_ID)) petId = intent.getStringExtra(EXTRA_PET_ID);
            if (intent.hasExtra(EXTRA_PET_NAME)) petName = intent.getStringExtra(EXTRA_PET_NAME);
            if (intent.hasExtra(EXTRA_PET_SPECIES)) species = intent.getStringExtra(EXTRA_PET_SPECIES);
        }
    }

    private void fetchDefaultPetAndLoad() {
        PetRepository petRepository = new PetRepository();
        try {
            petRepository.loadPets(new PetRepository.PetsCallback() {
                @Override
                public void onSuccess(List<Pet> pets) {
                    if (pets != null && !pets.isEmpty()) {
                        Pet defaultPet = pets.get(0);
                        petId = defaultPet.getId();
                        petName = defaultPet.getName();
                        species = defaultPet.getSpecies();
                        viewModel.loadRecordsForPet(petId);
                    }
                }

                @Override
                public void onError(Exception e) {
                    // Handled in ViewModel
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRefresh.setOnClickListener(v -> {
            if (petId != null && !petId.isEmpty()) {
                viewModel.loadRecordsForPet(petId);
            }
        });

        adapter = new LabHistoryAdapter(this);
        binding.rvLabHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLabHistory.setAdapter(adapter);

        binding.fabUploadNew.setOnClickListener(v -> openUploadScreen());
        binding.btnEmptyUpload.setOnClickListener(v -> openUploadScreen());

        // Filter chips
        binding.chipFilterAllHistory.setOnClickListener(v ->
                viewModel.setFilter(LabHistoryViewModel.HistoryFilter.ALL));
        binding.chipFilterAbnormalHistory.setOnClickListener(v ->
                viewModel.setFilter(LabHistoryViewModel.HistoryFilter.ABNORMAL_ONLY));
        binding.chipFilterNormalHistory.setOnClickListener(v ->
                viewModel.setFilter(LabHistoryViewModel.HistoryFilter.NORMAL_ONLY));

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setSearchQuery(s != null ? s.toString() : "");
            }
        });
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading ->
                binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.getDisplayedRecords().observe(this, records -> {
            boolean empty = records == null || records.isEmpty();
            binding.layoutEmptyHistory.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvLabHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
            adapter.setReports(records);
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.trim().isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openUploadScreen() {
        Intent intent = new Intent(this, LabReportUploadActivity.class);
        intent.putExtra(EXTRA_PET_ID, petId);
        intent.putExtra(EXTRA_PET_NAME, petName);
        intent.putExtra(EXTRA_PET_SPECIES, species);
        startActivity(intent);
    }

    @Override
    public void onReportClick(LabRecord record) {
        Intent intent = new Intent(this, LabReportDetailActivity.class);
        intent.putExtra(LabReportDetailActivity.EXTRA_LAB_RECORD, record);
        startActivity(intent);
    }

    @Override
    public void onReportShare(LabRecord record) {
        if (record == null) return;

        record.recalculateCounts();
        StringBuilder sb = new StringBuilder();
        sb.append("🐾 PetSense Lab Report Summary\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("Pet: ").append(record.getPetName() != null ? record.getPetName() : "Pet");
        sb.append(" (").append(record.getSpecies() != null ? record.getSpecies() : "Pet").append(")\n");
        sb.append("Laboratory: ").append(record.getLabName() != null ? record.getLabName() : "Veterinary Diagnostics").append("\n");
        if (record.getReportDate() != null) {
            sb.append("Date: ").append(dateFormat.format(record.getReportDate())).append("\n");
        }
        sb.append("Overview: ").append(record.getTotalTestsCount()).append(" Tests Analyzed (");
        sb.append(record.getNormalCount()).append(" Normal, ");
        sb.append(record.getHighCount()).append(" High, ");
        sb.append(record.getLowCount()).append(" Low, ");
        sb.append(record.getUnclassifiedCount()).append(" Unclassified)\n\n");

        sb.append("📋 Test Parameters:\n");
        for (LabTestItem item : record.getTests()) {
            sb.append("• ").append(item.getTestName()).append(": ").append(item.getValue());
            if (item.getUnit() != null && !item.getUnit().isEmpty()) sb.append(" ").append(item.getUnit());
            if (item.getRefRangeText() != null && !item.getRefRangeText().isEmpty()) {
                sb.append(" (Range: ").append(item.getRefRangeText()).append(")");
            }
            sb.append(" [").append(item.getStatus()).append("]\n");
        }

        if (record.getAiSummary() != null && !record.getAiSummary().trim().isEmpty()) {
            sb.append("\n✨ Clinical Summary:\n").append(record.getAiSummary()).append("\n");
        }

        if (record.getVetQuestions() != null && !record.getVetQuestions().trim().isEmpty()) {
            sb.append("\n❓ Questions for Veterinarian:\n").append(record.getVetQuestions()).append("\n");
        }

        sb.append("\n⚠️ Disclaimer: ").append(record.getDisclaimer() != null ? record.getDisclaimer() : LabRecord.DEFAULT_DISCLAIMER);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "PetSense Lab Report - " + record.getPetName());
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share Lab Report"));
    }

    @Override
    public void onReportDelete(LabRecord record) {
        if (record == null) return;

        String pName = record.getPetName() != null ? record.getPetName() : "your pet";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Lab Report?")
                .setMessage("Are you sure you want to delete this laboratory report from " + pName + "'s medical records? The report and its uploaded document in storage will be permanently deleted.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteRecord(this, record, () ->
                            Toast.makeText(this, "Report deleted successfully.", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
