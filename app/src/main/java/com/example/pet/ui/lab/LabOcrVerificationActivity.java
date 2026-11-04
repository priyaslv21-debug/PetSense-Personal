package com.example.pet.ui.lab;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import com.example.pet.databinding.ActivityLabOcrVerificationBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Human-in-the-loop OCR Verification Activity.
 * Enables pet owners to review and manually correct extracted test names, measured values,
 * units, and reference ranges before clinical classification and AI analysis.
 */
public class LabOcrVerificationActivity extends AppCompatActivity {

    private ActivityLabOcrVerificationBinding binding;
    private LabAnalysisViewModel viewModel;
    private LabOcrEditAdapter adapter;
    private ProgressDialog progressDialog;
    private final PetRepository petRepository = new PetRepository();

    private String petId = "";
    private String petName = "Pet";
    private String species = "Dog";
    private String breed = "";
    private String age = "";
    private String gender = "";
    private String weight = "";

    private final Calendar selectedDate = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private final List<String> sampleQualityWarnings = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLabOcrVerificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LabAnalysisViewModel.class);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Analyzing parameters and securing report...");
        progressDialog.setCancelable(false);

        extractIntentExtras(getIntent());
        setupViews();
        observeViewModel();

        if (petId == null || petId.trim().isEmpty()) {
            promptPetSelection();
        }
    }

    private void extractIntentExtras(Intent intent) {
        if (intent == null) return;

        petId = intent.getStringExtra(LabReportUploadActivity.EXTRA_PET_ID);
        petName = intent.getStringExtra(LabReportUploadActivity.EXTRA_PET_NAME);
        species = intent.getStringExtra(LabReportUploadActivity.EXTRA_PET_SPECIES);
        breed = intent.getStringExtra(LabReportUploadActivity.EXTRA_PET_BREED);
        age = intent.getStringExtra(LabReportUploadActivity.EXTRA_PET_AGE);
        gender = intent.getStringExtra(LabReportUploadActivity.EXTRA_PET_GENDER);
        weight = intent.getStringExtra(LabReportUploadActivity.EXTRA_PET_WEIGHT);

        viewModel.initPetContext(petId, petName, species, breed, age, gender, weight);

        String uriStr = intent.getStringExtra("fileUri");
        if (uriStr != null) {
            viewModel.setSelectedDocument(
                    Uri.parse(uriStr),
                    intent.getStringExtra("fileName"),
                    intent.getStringExtra("fileMime")
            );
        }

        // Pre-fill extracted laboratory name if detected
        String detectedLab = intent.getStringExtra("detectedLabName");
        if (detectedLab != null && !detectedLab.trim().isEmpty()) {
            binding.etLabProvider.setText(detectedLab);
        }

        // Pre-fill extracted report date if detected
        String detectedDate = intent.getStringExtra("detectedReportDate");
        if (detectedDate != null && !detectedDate.trim().isEmpty()) {
            binding.etReportDate.setText(detectedDate);
        }

        // Restore sample quality warnings (Hemolysis, Lipemia, etc.)
        ArrayList<String> warnings = intent.getStringArrayListExtra("sampleQualityWarnings");
        if (warnings != null && !warnings.isEmpty()) {
            sampleQualityWarnings.addAll(warnings);
        }

        // Restore extracted items
        @SuppressWarnings("unchecked")
        List<LabTestItem> items = (List<LabTestItem>) intent.getSerializableExtra("extractedItems");
        if (items != null && !items.isEmpty()) {
            for (LabTestItem item : items) {
                viewModel.addItem(item);
            }
        }
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        updatePetContextUI();

        // Sample Quality Warnings Card
        if (!sampleQualityWarnings.isEmpty()) {
            binding.cardSampleQuality.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (String w : sampleQualityWarnings) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("• ").append(w);
            }
            binding.tvSampleQualityWarnings.setText(sb.toString());
        } else {
            binding.cardSampleQuality.setVisibility(View.GONE);
        }

        // Setup Date Picker
        if (binding.etReportDate.getText() == null || binding.etReportDate.getText().toString().isEmpty()) {
            binding.etReportDate.setText(dateFormat.format(selectedDate.getTime()));
        }
        binding.etReportDate.setOnClickListener(v -> showDatePicker());

        // Setup Pet Switching
        binding.btnChangePet.setOnClickListener(v -> showPetSelectionDialog());
        binding.btnSelectPetPrompt.setOnClickListener(v -> showPetSelectionDialog());

        // Setup RecyclerView & Adapter
        adapter = new LabOcrEditAdapter(position -> updateEmptyState());
        binding.rvOcrItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvOcrItems.setAdapter(adapter);

        // Add missing test manually
        binding.btnAddMissingTest.setOnClickListener(v -> {
            LabTestItem blank = new LabTestItem("", "", "", "", LabTestItem.STATUS_UNCLASSIFIED);
            adapter.getItems().add(0, blank);
            adapter.notifyItemInserted(0);
            binding.rvOcrItems.smoothScrollToPosition(0);
            updateEmptyState();
        });

        // Confirm & Run Analysis
        binding.btnConfirmAndAnalyze.setOnClickListener(v -> {
            if (petId == null || petId.trim().isEmpty()) {
                Toast.makeText(this, "Please select an active pet before proceeding.", Toast.LENGTH_SHORT).show();
                showPetSelectionDialog();
                return;
            }

            List<LabTestItem> currentItems = adapter.getItems();
            if (currentItems.isEmpty()) {
                Toast.makeText(this, "Please add or retain at least one test parameter.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Sync edited items back to viewModel
            for (int i = 0; i < currentItems.size(); i++) {
                viewModel.updateItem(i, currentItems.get(i));
            }

            String labName = binding.etLabProvider.getText() != null
                    ? binding.etLabProvider.getText().toString().trim()
                    : "Veterinary Diagnostics";
            if (labName.isEmpty()) labName = "Veterinary Diagnostics";

            progressDialog.show();
            viewModel.confirmAndAnalyze(this, labName, selectedDate.getTime(), this::navigateToDetail);
        });
    }

    private void updatePetContextUI() {
        if (petId != null && !petId.trim().isEmpty()) {
            binding.layoutPetContext.setVisibility(View.VISIBLE);
            binding.layoutNoPetWarning.setVisibility(View.GONE);

            boolean isCat = "Cat".equalsIgnoreCase(species);
            binding.tvVerifyPetEmoji.setText(isCat ? "🐈" : "🐕");
            binding.tvVerifyPetName.setText("Patient: " + (petName != null ? petName : "Pet"));
            String metaStr = (species != null ? species : "Canine") +
                    (breed != null && !breed.isEmpty() ? " · " + breed : "") +
                    (age != null && !age.isEmpty() ? " · " + age : "");
            binding.tvVerifyPetMeta.setText(metaStr);
        } else {
            binding.layoutPetContext.setVisibility(View.GONE);
            binding.layoutNoPetWarning.setVisibility(View.VISIBLE);
        }
    }

    private void promptPetSelection() {
        petRepository.loadPets(new PetRepository.PetsCallback() {
            @Override
            public void onSuccess(List<Pet> pets) {
                if (pets != null && !pets.isEmpty()) {
                    Pet defaultPet = pets.get(0);
                    selectPet(defaultPet);
                } else {
                    updatePetContextUI();
                }
            }

            @Override
            public void onError(Exception e) {
                updatePetContextUI();
            }
        });
    }

    private void showPetSelectionDialog() {
        petRepository.loadPets(new PetRepository.PetsCallback() {
            @Override
            public void onSuccess(List<Pet> pets) {
                if (pets == null || pets.isEmpty()) {
                    Toast.makeText(LabOcrVerificationActivity.this, "No registered pets found. Please create a pet profile first.", Toast.LENGTH_LONG).show();
                    return;
                }

                String[] petNames = new String[pets.size()];
                for (int i = 0; i < pets.size(); i++) {
                    Pet p = pets.get(i);
                    petNames[i] = p.getName() + " (" + p.getSpecies() + (p.getBreed() != null ? " - " + p.getBreed() : "") + ")";
                }

                new MaterialAlertDialogBuilder(LabOcrVerificationActivity.this)
                        .setTitle("Select Associated Pet")
                        .setItems(petNames, (dialog, which) -> selectPet(pets.get(which)))
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(LabOcrVerificationActivity.this, "Failed to load pets: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void selectPet(Pet pet) {
        if (pet == null) return;
        petId = pet.getId();
        petName = pet.getName();
        species = pet.getSpecies();
        breed = pet.getBreed();
        age = pet.getAge();
        gender = pet.getGender();
        weight = pet.getWeight();

        viewModel.initPetContext(petId, petName, species, breed, age, gender, weight);
        updatePetContextUI();
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(year, month, dayOfMonth);
                    binding.etReportDate.setText(dateFormat.format(selectedDate.getTime()));
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void observeViewModel() {
        viewModel.getTestItems().observe(this, items -> {
            adapter.setItems(items);
            updateEmptyState();
        });

        viewModel.getIsProcessing().observe(this, processing -> {
            if (processing) {
                progressDialog.show();
            } else if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });

        viewModel.getProcessingMessage().observe(this, msg -> {
            if (msg != null && progressDialog.isShowing()) {
                progressDialog.setMessage(msg);
            }
        });

        viewModel.getErrorMessage().observe(this, err -> {
            if (err != null && !err.trim().isEmpty()) {
                if (progressDialog.isShowing()) progressDialog.dismiss();
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateEmptyState() {
        boolean empty = adapter.getItemCount() == 0;
        binding.layoutEmptyVerification.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvOcrItems.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void navigateToDetail() {
        if (progressDialog.isShowing()) progressDialog.dismiss();

        LabRecord record = viewModel.getSavedRecord().getValue();
        if (record == null) {
            Toast.makeText(this, "Report saved successfully!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent = new Intent(this, LabReportDetailActivity.class);
        intent.putExtra(LabReportDetailActivity.EXTRA_LAB_RECORD, record);
        startActivity(intent);
        finish();
    }
}
