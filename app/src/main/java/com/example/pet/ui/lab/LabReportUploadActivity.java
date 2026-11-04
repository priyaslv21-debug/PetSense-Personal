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
import com.example.pet.databinding.ActivityLabReportUploadBinding;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Screen enabling users to upload pet laboratory reports (PDF, JPG, JPEG, PNG).
 * Handles file selection and triggers on-device OCR extraction before routing to verification.
 */
public class LabReportUploadActivity extends AppCompatActivity {

    public static final String EXTRA_PET_ID = "petId";
    public static final String EXTRA_PET_NAME = "petName";
    public static final String EXTRA_PET_SPECIES = "petSpecies";

    private ActivityLabReportUploadBinding binding;
    private LabAnalysisViewModel viewModel;

    private String petId = "";
    private String petName = "Pet";
    private String species = "Dog";

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
    }

    private void extractIntentExtras(Intent intent) {
        if (intent != null) {
            if (intent.hasExtra(EXTRA_PET_ID)) petId = intent.getStringExtra(EXTRA_PET_ID);
            if (intent.hasExtra(EXTRA_PET_NAME)) petName = intent.getStringExtra(EXTRA_PET_NAME);
            if (intent.hasExtra(EXTRA_PET_SPECIES)) species = intent.getStringExtra(EXTRA_PET_SPECIES);
        }
        viewModel.initPetContext(petId, petName, species);
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Display pet context
        binding.tvPetContextName.setText(petName);
        boolean isCat = "Cat".equalsIgnoreCase(species);
        binding.tvSpeciesEmoji.setText(isCat ? "🐈" : "🐕");
        binding.tvPetContextSpecies.setText(
                isCat ? "Feline · Evaluated for cat diagnostic profiles (e.g., T4, Renal, Glycemia)"
                      : "Canine · Evaluated for dog diagnostic baselines (e.g., ALT, Creatinine, CBC)"
        );

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
        intent.putExtra("rawText", viewModel.getRawExtractedText().getValue());
        intent.putExtra("fileName", viewModel.getSelectedFileName());
        intent.putExtra("fileMime", viewModel.getSelectedMimeType());
        if (viewModel.getSelectedFileUri() != null) {
            intent.putExtra("fileUri", viewModel.getSelectedFileUri().toString());
        }
        if (viewModel.getTestItems().getValue() != null) {
            intent.putExtra("extractedItems", new ArrayList<>(viewModel.getTestItems().getValue()));
        }

        startActivity(intent);
        finish();
    }
}
