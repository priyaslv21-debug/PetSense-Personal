package com.example.pet.ui.lab;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pet.R;
import com.example.pet.data.LabRecord;
import com.example.pet.data.LabRepository;
import com.example.pet.data.LabTestItem;
import com.example.pet.databinding.ActivityLabReportDetailBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Screen displaying comprehensive hybrid laboratory analysis:
 * - Deterministic, report-range validated parameter classifications.
 * - Overall counts: Total, Normal, High, Low, and Unclassified.
 * - AI plain-language explanations of individual markers.
 * - Overall clinical summary and informational veterinary discussion points.
 * - Sharing report summaries, viewing original files, and deleting reports with confirmation.
 */
public class LabReportDetailActivity extends AppCompatActivity {

    public static final String EXTRA_LAB_RECORD = "extra_lab_record";
    public static final String EXTRA_RECORD_ID = "recordId";
    public static final String EXTRA_PET_ID = "petId";

    private ActivityLabReportDetailBinding binding;
    private LabTestItemAdapter adapter;
    private LabRecord currentRecord;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLabReportDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViews();
        extractAndDisplayRecord();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        adapter = new LabTestItemAdapter();
        binding.rvTestResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTestResults.setAdapter(adapter);

        // Filter chips logic
        binding.chipFilterAll.setOnClickListener(v -> adapter.filter("ALL"));
        binding.chipFilterAbnormal.setOnClickListener(v -> adapter.filter("ABNORMAL"));
        binding.chipFilterNormal.setOnClickListener(v -> adapter.filter("NORMAL"));

        // View original document in Firebase Storage
        binding.btnViewOriginalDoc.setOnClickListener(v -> viewOriginalDocument());

        // Share report summary
        binding.btnShareReport.setOnClickListener(v -> shareReportSummary());

        // Delete report with confirmation
        binding.btnDeleteReport.setOnClickListener(v -> confirmDeleteReport());
    }

    private void extractAndDisplayRecord() {
        Intent intent = getIntent();
        if (intent == null) return;

        if (intent.hasExtra(EXTRA_LAB_RECORD)) {
            currentRecord = (LabRecord) intent.getSerializableExtra(EXTRA_LAB_RECORD);
            if (currentRecord != null) {
                renderRecord(currentRecord);
                return;
            }
        }

        // Fetch from Firestore if IDs were passed instead
        String petId = intent.getStringExtra(EXTRA_PET_ID);
        String recordId = intent.getStringExtra(EXTRA_RECORD_ID);

        if (petId != null && recordId != null) {
            LabRepository repository = new LabRepository();
            repository.getLabRecord(petId, recordId, new LabRepository.LabRecordCallback() {
                @Override
                public void onSuccess(LabRecord record) {
                    currentRecord = record;
                    renderRecord(record);
                }

                @Override
                public void onError(Exception e) {
                    Toast.makeText(LabReportDetailActivity.this, "Could not load report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void renderRecord(LabRecord record) {
        record.recalculateCounts();

        String pet = record.getPetName() != null ? record.getPetName() : "Pet";
        String species = record.getSpecies() != null ? record.getSpecies() : "Canine/Feline";
        String dateStr = record.getReportDate() != null ? dateFormat.format(record.getReportDate()) : "Recent";

        binding.tvDetailTitle.setText(record.getLabName() != null ? record.getLabName() : "Laboratory Analysis");
        binding.tvDetailSubtitle.setText(pet + " · " + species + " · " + dateStr);

        // Metrics Grid (Total, Normal, High, Low, Unclassified)
        binding.tvMetricTotal.setText(String.valueOf(record.getTotalTestsCount()));
        binding.tvMetricNormal.setText(String.valueOf(record.getNormalCount()));
        binding.tvMetricHigh.setText(String.valueOf(record.getHighCount()));
        binding.tvMetricLow.setText(String.valueOf(record.getLowCount()));
        binding.tvMetricUnclassified.setText(String.valueOf(record.getUnclassifiedCount()));

        // Abnormal Count Badges
        int abnormal = record.getAbnormalCount();
        if (abnormal > 0) {
            binding.tvAbnormalBadge.setText(abnormal + " Abnormal");
            binding.tvAbnormalBadge.setTextColor(ContextCompat.getColor(this, R.color.pd_red_text));
            binding.tvAbnormalBadge.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pd_red_bg));

            binding.tvAbnormalBadgeOffline.setText(abnormal + " Abnormal");
            binding.tvAbnormalBadgeOffline.setTextColor(ContextCompat.getColor(this, R.color.pd_red_text));
            binding.tvAbnormalBadgeOffline.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pd_red_bg));
        } else {
            binding.tvAbnormalBadge.setText("All Normal");
            binding.tvAbnormalBadge.setTextColor(ContextCompat.getColor(this, R.color.pd_green_text));
            binding.tvAbnormalBadge.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pd_green_bg));

            binding.tvAbnormalBadgeOffline.setText("All Normal");
            binding.tvAbnormalBadgeOffline.setTextColor(ContextCompat.getColor(this, R.color.pd_green_text));
            binding.tvAbnormalBadgeOffline.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pd_green_bg));
        }

        // AI Summary vs Offline Handling (no fake summary if null)
        String summary = record.getAiSummary();
        if (summary != null && !summary.trim().isEmpty()) {
            binding.cardAiSummary.setVisibility(View.VISIBLE);
            binding.cardAiOfflineNotice.setVisibility(View.GONE);
            binding.tvAiSummaryText.setText(summary);
        } else {
            binding.cardAiSummary.setVisibility(View.GONE);
            binding.cardAiOfflineNotice.setVisibility(View.VISIBLE);
        }

        // Veterinary Recommendations
        String recommendations = record.getVetRecommendations();
        if (recommendations != null && !recommendations.trim().isEmpty()) {
            binding.cardVetRecommendations.setVisibility(View.VISIBLE);
            binding.tvVetRecommendations.setText(recommendations);
        } else {
            binding.cardVetRecommendations.setVisibility(View.GONE);
        }

        // Veterinary Questions
        String questions = record.getVetQuestions();
        if (questions != null && !questions.trim().isEmpty()) {
            binding.cardVetQuestions.setVisibility(View.VISIBLE);
            binding.tvVetQuestions.setText(questions);
        } else {
            binding.cardVetQuestions.setVisibility(View.GONE);
        }

        // Disclaimer
        if (record.getDisclaimer() != null) {
            binding.tvDisclaimer.setText("⚠️ " + record.getDisclaimer());
        }

        // Test items
        adapter.setItems(record.getTests());

        // Update chip counts
        int total = record.getTotalTestsCount();
        binding.chipFilterAll.setText("All (" + total + ")");
        binding.chipFilterAbnormal.setText("Abnormal (" + abnormal + ")");
        binding.chipFilterNormal.setText("Normal (" + record.getNormalCount() + ")");
    }

    private void viewOriginalDocument() {
        if (currentRecord == null) {
            Toast.makeText(this, "No record selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        File localFile = null;
        if (currentRecord.getStoragePath() != null && !currentRecord.getStoragePath().trim().isEmpty()) {
            File f = LabRepository.getLocalDocumentFile(this, currentRecord.getStoragePath());
            if (f.exists()) {
                localFile = f;
            }
        }
        if (localFile == null && currentRecord.getOriginalFileUrl() != null && !currentRecord.getOriginalFileUrl().trim().isEmpty()) {
            File f = new File(currentRecord.getOriginalFileUrl());
            if (f.exists()) {
                localFile = f;
            }
        }

        if (localFile != null && localFile.exists()) {
            try {
                Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", localFile);
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                String mime = currentRecord.getFileType();
                if (mime == null || mime.trim().isEmpty()) {
                    mime = localFile.getName().toLowerCase(Locale.ROOT).endsWith(".pdf") ? "application/pdf" : "image/*";
                }
                viewIntent.setDataAndType(contentUri, mime);
                viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(viewIntent, "Open Lab Document"));
                return;
            } catch (Exception e) {
                Toast.makeText(this, "Could not open document viewer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (currentRecord.getOriginalFileUrl() != null && currentRecord.getOriginalFileUrl().startsWith("http")) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentRecord.getOriginalFileUrl()));
                startActivity(browserIntent);
                return;
            } catch (Exception e) {
                Toast.makeText(this, "Failed to open document link: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Toast.makeText(this, "Original document attachment not found in local storage.", Toast.LENGTH_SHORT).show();
    }

    private void shareReportSummary() {
        if (currentRecord == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("🐾 PetSense Lab Report Summary\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("Pet: ").append(currentRecord.getPetName() != null ? currentRecord.getPetName() : "Pet");
        sb.append(" (").append(currentRecord.getSpecies() != null ? currentRecord.getSpecies() : "Pet").append(")\n");
        sb.append("Laboratory: ").append(currentRecord.getLabName() != null ? currentRecord.getLabName() : "Veterinary Diagnostics").append("\n");
        if (currentRecord.getReportDate() != null) {
            sb.append("Date: ").append(dateFormat.format(currentRecord.getReportDate())).append("\n");
        }
        sb.append("Overview: ").append(currentRecord.getTotalTestsCount()).append(" Tests Analyzed (");
        sb.append(currentRecord.getNormalCount()).append(" Normal, ");
        sb.append(currentRecord.getHighCount()).append(" High, ");
        sb.append(currentRecord.getLowCount()).append(" Low, ");
        sb.append(currentRecord.getUnclassifiedCount()).append(" Unclassified)\n\n");

        sb.append("📋 Test Parameters:\n");
        for (LabTestItem item : currentRecord.getTests()) {
            sb.append("• ").append(item.getTestName()).append(": ").append(item.getValue());
            if (item.getUnit() != null && !item.getUnit().isEmpty()) sb.append(" ").append(item.getUnit());
            if (item.getRefRangeText() != null && !item.getRefRangeText().isEmpty()) {
                sb.append(" (Range: ").append(item.getRefRangeText()).append(")");
            }
            sb.append(" [").append(item.getStatus()).append("]\n");
        }

        if (currentRecord.getAiSummary() != null && !currentRecord.getAiSummary().trim().isEmpty()) {
            sb.append("\n✨ Clinical Summary:\n").append(currentRecord.getAiSummary()).append("\n");
        }

        if (currentRecord.getVetQuestions() != null && !currentRecord.getVetQuestions().trim().isEmpty()) {
            sb.append("\n❓ Questions for Veterinarian:\n").append(currentRecord.getVetQuestions()).append("\n");
        }

        sb.append("\n⚠️ Disclaimer: ").append(currentRecord.getDisclaimer() != null ? currentRecord.getDisclaimer() : LabRecord.DEFAULT_DISCLAIMER);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "PetSense Lab Report - " + currentRecord.getPetName());
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share Lab Report"));
    }

    private void confirmDeleteReport() {
        if (currentRecord == null) return;

        String petName = currentRecord.getPetName() != null ? currentRecord.getPetName() : "your pet";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Lab Report?")
                .setMessage("Are you sure you want to delete this laboratory report from " + petName + "'s medical records? This action cannot be undone and will remove the file from secure storage.")
                .setPositiveButton("Delete", (dialog, which) -> executeDelete())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeDelete() {
        if (currentRecord == null) return;

        String petId = currentRecord.getPetId();
        String recordId = currentRecord.getId();
        String storagePath = currentRecord.getStoragePath();

        if (petId == null || recordId == null) {
            Toast.makeText(this, "Cannot delete report: missing identifier.", Toast.LENGTH_SHORT).show();
            return;
        }

        LabRepository repository = new LabRepository();
        repository.deleteLabRecord(this, petId, recordId, storagePath, new LabRepository.DeleteLabRecordCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(LabReportDetailActivity.this, "Report deleted successfully.", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(LabReportDetailActivity.this, "Failed to delete report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
