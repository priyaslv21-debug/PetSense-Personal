package com.example.pet.ui.lab;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.pet.data.LabTestItem;
import com.example.pet.data.Pet;
import com.example.pet.data.PetRepository;
import com.example.pet.databinding.ActivityLabReportUploadBinding;
import com.example.pet.lab.LabRuleEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen enabling users to upload pet laboratory reports (PDF, JPG, JPEG, PNG).
 * Handles file selection and triggers on-device OCR extraction before routing to verification.
 */
public class LabReportUploadActivity extends AppCompatActivity {

    public static final String EXTRA_PET_ID = "petId";
    public static final String EXTRA_PET_NAME = "petName";
    public static final String EXTRA_PET_SPECIES = "petSpecies";
    public static final String EXTRA_PET_BREED = "petBreed";
    public static final String EXTRA_PET_AGE = "petAge";
    public static final String EXTRA_PET_GENDER = "petGender";
    public static final String EXTRA_PET_WEIGHT = "petWeight";

    private ActivityLabReportUploadBinding binding;
    private LabAnalysisViewModel viewModel;
    private final PetRepository petRepository = new PetRepository();

    private String petId = "";
    private String petName = "Pet";
    private String species = "Dog";
    private String breed = "";
    private String age = "";
    private String gender = "";
    private String weight = "";

    private final ActivityResultLauncher<String[]> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::onFileSelected
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLabReportUploadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LabAnalysisViewModel.class);

        extractIntentExtras(getIntent());
        setupViews();
        observeViewModel();

        if (petId == null || petId.trim().isEmpty()) {
            fetchDefaultPet();
        }
    }

    private void extractIntentExtras(Intent intent) {
        if (intent != null) {
            if (intent.hasExtra(EXTRA_PET_ID)) petId = intent.getStringExtra(EXTRA_PET_ID);
            if (intent.hasExtra(EXTRA_PET_NAME)) petName = intent.getStringExtra(EXTRA_PET_NAME);
            if (intent.hasExtra(EXTRA_PET_SPECIES)) species = intent.getStringExtra(EXTRA_PET_SPECIES);
            if (intent.hasExtra(EXTRA_PET_BREED)) breed = intent.getStringExtra(EXTRA_PET_BREED);
            if (intent.hasExtra(EXTRA_PET_AGE)) age = intent.getStringExtra(EXTRA_PET_AGE);
            if (intent.hasExtra(EXTRA_PET_GENDER)) gender = intent.getStringExtra(EXTRA_PET_GENDER);
            if (intent.hasExtra(EXTRA_PET_WEIGHT)) weight = intent.getStringExtra(EXTRA_PET_WEIGHT);
        }
        viewModel.initPetContext(petId, petName, species, breed, age, gender, weight);
    }

    private void fetchDefaultPet() {
        petRepository.loadPets(new PetRepository.PetsCallback() {
            @Override
            public void onSuccess(List<Pet> pets) {
                if (pets != null && !pets.isEmpty()) {
                    Pet defaultPet = pets.get(0);
                    petId = defaultPet.getId();
                    petName = defaultPet.getName();
                    species = defaultPet.getSpecies();
                    breed = defaultPet.getBreed();
                    age = defaultPet.getAge();
                    gender = defaultPet.getGender();
                    weight = defaultPet.getWeight();
                    viewModel.initPetContext(petId, petName, species, breed, age, gender, weight);
                    updatePetDisplay();
                }
            }

            @Override
            public void onError(Exception e) {
                // Handled gracefully in UI
            }
        });
    }

    private void updatePetDisplay() {
        binding.tvPetContextName.setText(petName);
        boolean isCat = "Cat".equalsIgnoreCase(species);
        binding.tvSpeciesEmoji.setText(isCat ? "🐈" : "🐕");
        String metaLine = species + (breed != null && !breed.isEmpty() ? " · " + breed : "");
        binding.tvPetContextSpecies.setText(
                isCat ? "Feline (" + metaLine + ") · Evaluated for feline diagnostics"
                      : "Canine (" + metaLine + ") · Evaluated for canine diagnostics"
        );
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        updatePetDisplay();

        binding.cardUploadZone.setOnClickListener(v -> launchFilePicker());
        binding.btnSelectFile.setOnClickListener(v -> launchFilePicker());

        binding.btnClearFile.setOnClickListener(v -> {
            viewModel.setSelectedDocument(null, null, null);
            binding.cardFilePreview.setVisibility(View.GONE);
            binding.btnStartExtraction.setEnabled(false);
        });

        binding.btnStartExtraction.setOnClickListener(v -> {
            binding.layoutProcessing.setVisibility(View.VISIBLE);
            binding.btnStartExtraction.setEnabled(false);
            binding.cardUploadZone.setEnabled(false);

            viewModel.startExtraction(this, this::navigateToVerification);
        });
    }

    private void launchFilePicker() {
        String[] mimeTypes = {"application/pdf", "image/jpeg", "image/png", "image/jpg"};
        filePickerLauncher.launch(mimeTypes);
    }

    private void onFileSelected(@Nullable Uri uri) {
        if (uri == null) return;

        String displayName = "report_document";
        String mimeType = getContentResolver().getType(uri);

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0) {
                    displayName = cursor.getString(nameIdx);
                }
            }
        } catch (Exception ignored) {
        }

        viewModel.setSelectedDocument(uri, displayName, mimeType);

        binding.cardFilePreview.setVisibility(View.VISIBLE);
        binding.tvSelectedFileName.setText(displayName);
        boolean isPdf = displayName.endsWith(".pdf") || (mimeType != null && mimeType.contains("pdf"));
        binding.tvFileIcon.setText(isPdf ? "📄" : "🖼️");
        binding.tvSelectedFileType.setText(isPdf ? "PDF Document · Multi-page extraction supported" : "Image Document (OCR enabled)");
        binding.btnStartExtraction.setEnabled(true);
    }

    private void observeViewModel() {
        viewModel.getIsProcessing().observe(this, processing -> {
            binding.layoutProcessing.setVisibility(processing ? View.VISIBLE : View.GONE);
            binding.btnStartExtraction.setEnabled(!processing && viewModel.getSelectedFileUri() != null);
        });

        viewModel.getProcessingMessage().observe(this, msg -> {
            if (msg != null) binding.tvProcessingStatus.setText(msg);
        });

        viewModel.getErrorMessage().observe(this, err -> {
            if (err != null && !err.trim().isEmpty()) {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToVerification() {
        Intent intent = new Intent(this, LabOcrVerificationActivity.class);
        intent.putExtra(EXTRA_PET_ID, petId);
        intent.putExtra(EXTRA_PET_NAME, petName);
        intent.putExtra(EXTRA_PET_SPECIES, species);
        intent.putExtra(EXTRA_PET_BREED, breed);
        intent.putExtra(EXTRA_PET_AGE, age);
        intent.putExtra(EXTRA_PET_GENDER, gender);
        intent.putExtra(EXTRA_PET_WEIGHT, weight);
        intent.putExtra("rawText", viewModel.getRawExtractedText().getValue());
        intent.putExtra("fileName", viewModel.getSelectedFileName());
        intent.putExtra("fileMime", viewModel.getSelectedMimeType());
        if (viewModel.getSelectedFileUri() != null) {
            intent.putExtra("fileUri", viewModel.getSelectedFileUri().toString());
        }
        if (viewModel.getTestItems().getValue() != null) {
            intent.putExtra("extractedItems", new ArrayList<>(viewModel.getTestItems().getValue()));
        }

        LabRuleEngine.ReportMetadata meta = viewModel.getReportMetadata().getValue();
        if (meta != null) {
            if (meta.labName != null) intent.putExtra("detectedLabName", meta.labName);
            if (meta.reportDateStr != null) intent.putExtra("detectedReportDate", meta.reportDateStr);
            if (meta.sampleQualityWarnings != null && !meta.sampleQualityWarnings.isEmpty()) {
                intent.putStringArrayListExtra("sampleQualityWarnings", new ArrayList<>(meta.sampleQualityWarnings));
            }
        }

        startActivity(intent);
        finish();
    }
}
