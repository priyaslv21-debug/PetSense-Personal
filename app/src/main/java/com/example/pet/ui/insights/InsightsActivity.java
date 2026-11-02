package com.example.pet.ui.insights;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.pet.MainActivity;
import com.example.pet.PetProfileActivity;
import com.example.pet.R;
import com.example.pet.data.Pet;
import com.example.pet.data.PetRepository;
import com.example.pet.databinding.ActivityInsightsBinding;
import com.example.pet.ui.behavior.BehaviorActivity;
import com.example.pet.ui.health.HealthActivity;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InsightsActivity extends AppCompatActivity {

    private ActivityInsightsBinding binding;
    private InsightsViewModel viewModel;
    private PetRepository petRepository;

    private String currentPetId = "";
    private String currentPetName = "";
    private String currentPetDetails = "";

    private final ActivityResultLauncher<String> savePdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"),
            uri -> {
                if (uri != null) {
                    generateAndSavePdf(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityInsightsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(InsightsViewModel.class);
        petRepository = new PetRepository();

        extractPetExtras(getIntent());

        setupClickListeners();
        setupBottomNavigation();
        observeViewModel();
    }

    // 🔴 FIX 1: Catch new Intents when switching tabs with FLAG_ACTIVITY_REORDER_TO_FRONT
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        extractPetExtras(intent);
    }

    private void extractPetExtras(Intent intent) {
        if (intent != null && intent.hasExtra("petId")) {
            String id = intent.getStringExtra("petId");
            if (id != null && !id.isEmpty()) {
                currentPetId = id;
                currentPetName = intent.getStringExtra("petName");
                currentPetDetails = intent.getStringExtra("petDetails");
            }
        }
    }

    // 🔴 FIX 2: Always fetch data on resume, with auto-fallback to default pet if petId is missing
    @Override
    protected void onResume() {
        super.onResume();
        if (currentPetId != null && !currentPetId.isEmpty()) {
            viewModel.loadInsightsData(currentPetId, currentPetName, currentPetDetails);
        } else {
            fetchDefaultPetAndLoad();
        }
    }

    private void fetchDefaultPetAndLoad() {
        try {
            petRepository.loadPets(new PetRepository.PetsCallback() {
                @Override
                public void onSuccess(List<Pet> pets) {
                    if (pets != null && !pets.isEmpty()) {
                        Pet defaultPet = pets.get(0);
                        currentPetId = defaultPet.getId();
                        currentPetName = defaultPet.getName();
                        currentPetDetails = defaultPet.breedAgeLine();
                        viewModel.loadInsightsData(currentPetId, currentPetName, currentPetDetails);
                    }
                }

                @Override
                public void onError(Exception e) {
                    // Fallback handled gracefully in ViewModel
                }
            });
        } catch (Exception ignored) {}
    }

    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        if (binding.btnExportPdf != null) {
            binding.btnExportPdf.setOnClickListener(v -> {
                String fileName = (currentPetName.isEmpty() ? "Pet" : currentPetName) + "_HealthReport.pdf";
                savePdfLauncher.launch(fileName);
            });
        }
    }

    private void generateAndSavePdf(Uri uri) {
        try {
            PdfDocument document = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            Paint paint = new Paint();
            TextPaint textPaint = new TextPaint();

            int startX = 50;
            int startY = 80;

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(24);
            paint.setColor(Color.BLACK);
            canvas.drawText("PetSense Health Report", startX, startY, paint);

            startY += 30;
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            paint.setTextSize(14);
            paint.setColor(Color.DKGRAY);
            String dateStr = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(new Date());
            canvas.drawText("Generated on: " + dateStr, startX, startY, paint);

            startY += 50;
            paint.setTextSize(18);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setColor(Color.BLACK);
            canvas.drawText("Patient: " + (currentPetName.isEmpty() ? "Pet" : currentPetName), startX, startY, paint);
            startY += 25;
            paint.setTextSize(14);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            canvas.drawText(currentPetDetails, startX, startY, paint);

            startY += 20;
            paint.setColor(Color.LTGRAY);
            canvas.drawLine(startX, startY, 545, startY, paint);

            startY += 40;
            paint.setColor(Color.BLACK);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Vital Metrics:", startX, startY, paint);

            startY += 30;
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

            String activityVal = viewModel.getActivityMetric().getValue();
            String sleepVal = viewModel.getSleepMetric().getValue();
            String nutritionVal = viewModel.getNutritionMetric().getValue();
            String behaviorVal = viewModel.getBehaviorMetric().getValue();

            canvas.drawText("Activity Level: " + (activityVal != null ? activityVal : "N/A"), startX, startY, paint);
            startY += 25;
            canvas.drawText("Sleep Duration: " + (sleepVal != null ? sleepVal : "N/A"), startX, startY, paint);
            startY += 25;
            canvas.drawText("Appetite: " + (nutritionVal != null ? nutritionVal : "N/A"), startX, startY, paint);
            startY += 25;
            canvas.drawText("Observed Behavior: " + (behaviorVal != null ? behaviorVal : "N/A"), startX, startY, paint);

            startY += 30;
            paint.setColor(Color.LTGRAY);
            canvas.drawLine(startX, startY, 545, startY, paint);

            startY += 40;
            paint.setColor(Color.BLACK);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Clinical Insight:", startX, startY, paint);

            startY += 25;
            textPaint.setTextSize(14);
            textPaint.setColor(Color.DKGRAY);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

            String insightText = viewModel.getAiInsight().getValue();
            if (insightText == null || insightText.isEmpty()) insightText = "No data available.";

            StaticLayout textLayout = StaticLayout.Builder.obtain(insightText, 0, insightText.length(), textPaint, 495)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0.0f, 1.2f)
                    .setIncludePad(false)
                    .build();

            canvas.save();
            canvas.translate(startX, startY);
            textLayout.draw(canvas);
            canvas.restore();

            document.finishPage(page);

            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                document.writeTo(outputStream);
                document.close();
                outputStream.close();
                Toast.makeText(this, "Report saved successfully!", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_insights);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_health) {
                Intent intent = new Intent(this, HealthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_behavior) {
                Intent intent = new Intent(this, BehaviorActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_insights) {
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(this, PetProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void observeViewModel() {
        viewModel.getPetName().observe(this, name -> {
            if (name != null && !name.isEmpty()) binding.tvHeroTitle.setText("Personalized\ninsights for " + name);
        });
        viewModel.getPetDetails().observe(this, details -> {
            if (details != null && !details.isEmpty()) binding.tvHeroSubtitle.setText(details);
        });
        viewModel.getAiInsight().observe(this, text -> {
            if (text != null) binding.tvAiInsightText.setText(text);
        });
        viewModel.getAiInsightContext().observe(this, context -> {
            if (context != null) binding.tvAiInsightContext.setText(context);
        });
        viewModel.getActivityMetric().observe(this, binding.tvThisWeekActivityValue::setText);
        viewModel.getActivityDesc().observe(this, binding.tvThisWeekActivityDesc::setText);
        viewModel.getSleepMetric().observe(this, binding.tvThisWeekSleepValue::setText);
        viewModel.getSleepDesc().observe(this, binding.tvThisWeekSleepDesc::setText);
        viewModel.getNutritionMetric().observe(this, binding.tvThisWeekNutritionValue::setText);
        viewModel.getNutritionDesc().observe(this, binding.tvThisWeekNutritionDesc::setText);
        viewModel.getBehaviorMetric().observe(this, binding.tvThisWeekBehaviorValue::setText);
        viewModel.getBehaviorDesc().observe(this, binding.tvThisWeekBehaviorDesc::setText);
        viewModel.getPatternTitle().observe(this, binding.tvPatternTitle::setText);
        viewModel.getPatternDesc().observe(this, binding.tvPatternDesc::setText);
        viewModel.getRecommendation().observe(this, binding.tvRecommendation::setText);
        viewModel.getTrendLevels().observe(this, this::renderTrend);
    }

    private void renderTrend(List<Integer> levels) {
        binding.trendBarsContainer.removeAllViews();
        if (levels == null || levels.size() < 2) {
            binding.trendBarsContainer.setVisibility(View.GONE);
            binding.tvTrendEmpty.setVisibility(View.VISIBLE);
            return;
        }
        binding.trendBarsContainer.setVisibility(View.VISIBLE);
        binding.tvTrendEmpty.setVisibility(View.GONE);
        int barWidthPx = dpToPx(10);
        int barMarginPx = dpToPx(6);
        int lowHeightPx = dpToPx(14);
        int normalHeightPx = dpToPx(32);
        int highHeightPx = dpToPx(56);
        for (int level : levels) {
            View bar = new View(this);
            int heightPx;
            int colorRes;
            if (level == 0) {
                heightPx = lowHeightPx;
                colorRes = R.color.ps_pastel_pink_on;
            } else if (level == 2) {
                heightPx = highHeightPx;
                colorRes = R.color.ps_primary;
            } else {
                heightPx = normalHeightPx;
                colorRes = R.color.ps_pastel_blue_on;
            }
            bar.setBackgroundColor(getColor(colorRes));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(barWidthPx, heightPx);
            params.setMarginEnd(barMarginPx);
            params.gravity = android.view.Gravity.BOTTOM;
            bar.setLayoutParams(params);
            binding.trendBarsContainer.addView(bar);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}