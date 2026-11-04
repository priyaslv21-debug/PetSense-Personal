package com.example.pet.ui.insights;

import android.Manifest;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pet.MainActivity;
import com.example.pet.PetProfileActivity;
import com.example.pet.R;
import com.example.pet.data.Pet;
import com.example.pet.data.PetRepository;
import com.example.pet.data.Reminder;
import com.example.pet.data.ReminderRepository;
import com.example.pet.databinding.ActivityInsightsBinding;
import com.example.pet.reminder.AddReminderActivity;
import com.example.pet.reminder.ReminderAdapter;
import com.example.pet.reminder.ReminderNotificationHelper;
import com.example.pet.ui.behavior.BehaviorActivity;
import com.example.pet.ui.health.HealthActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InsightsActivity extends AppCompatActivity {

    private static final String TAG = "InsightsActivity";

    private ActivityInsightsBinding binding;
    private InsightsViewModel viewModel;
    private PetRepository petRepository;
    private ReminderRepository reminderRepository;
    private ReminderAdapter reminderAdapter;

    private final List<Reminder> allLoadedReminders = new ArrayList<>();
    private String activeCategoryFilter = "All";

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

    private final ActivityResultLauncher<String> notifPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Reminder notifications enabled!", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> addReminderLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadReminders();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityInsightsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(InsightsViewModel.class);
        petRepository = new PetRepository();
        reminderRepository = new ReminderRepository();

        ReminderNotificationHelper.createNotificationChannel(this);
        checkNotificationPermission();

        extractPetExtras(getIntent());

        setupClickListeners();
        setupBottomNavigation();
        setupRemindersRecyclerView();
        setupReminderFilterChips();
        setupReminderButtons();
        observeViewModel();

        checkTargetSection(getIntent());
    }

    // 🔴 Catch new Intents when switching tabs with FLAG_ACTIVITY_REORDER_TO_FRONT
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        extractPetExtras(intent);
        checkTargetSection(intent);
        loadReminders();
    }

    private void checkTargetSection(Intent intent) {
        if (intent != null && "reminders".equalsIgnoreCase(intent.getStringExtra("target_section"))) {
            binding.insightsScroll.post(() -> {
                int top = binding.sectionPetCareReminders.getTop();
                binding.insightsScroll.smoothScrollTo(0, top);
            });
        }
    }

    private void checkNotificationPermission() {
        if (!ReminderNotificationHelper.hasNotificationPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
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

    // 🔴 Always fetch data on resume, with auto-fallback to default pet if petId is missing
    @Override
    protected void onResume() {
        super.onResume();
        if (currentPetId != null && !currentPetId.isEmpty()) {
            viewModel.loadInsightsData(currentPetId, currentPetName, currentPetDetails);
        } else {
            fetchDefaultPetAndLoad();
        }
        loadReminders();
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

    private void setupRemindersRecyclerView() {
        reminderAdapter = new ReminderAdapter(new ReminderAdapter.OnReminderActionListener() {
            @Override
            public void onItemClick(Reminder reminder) {
                openEditReminder(reminder);
            }

            @Override
            public void onCompleteClick(Reminder reminder) {
                markReminderCompleted(reminder);
            }

            @Override
            public void onSnoozeClick(Reminder reminder) {
                snoozeReminder(reminder);
            }

            @Override
            public void onToggleEnabled(Reminder reminder, boolean isEnabled) {
                toggleReminderEnabled(reminder, isEnabled);
            }

            @Override
            public void onEditClick(Reminder reminder) {
                openEditReminder(reminder);
            }

            @Override
            public void onDeleteClick(Reminder reminder) {
                confirmDeleteReminder(reminder);
            }
        });

        binding.rvReminders.setLayoutManager(new LinearLayoutManager(this));
        binding.rvReminders.setAdapter(reminderAdapter);
    }

    private void setupReminderFilterChips() {
        binding.chipGroupReminderFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                activeCategoryFilter = "All";
            } else {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipFilterVaccine) {
                    activeCategoryFilter = Reminder.TYPE_VACCINATION;
                } else if (checkedId == R.id.chipFilterCheckup) {
                    activeCategoryFilter = Reminder.TYPE_CHECKUP;
                } else if (checkedId == R.id.chipFilterDeworming) {
                    activeCategoryFilter = Reminder.TYPE_DEWORMING;
                } else if (checkedId == R.id.chipFilterMedication) {
                    activeCategoryFilter = Reminder.TYPE_MEDICATION;
                } else {
                    activeCategoryFilter = "All";
                }
            }
            applyFilterAndRender();
        });
    }

    private void setupReminderButtons() {
        View.OnClickListener openAddReminder = v -> {
            Intent intent = new Intent(InsightsActivity.this, AddReminderActivity.class);
            if (currentPetId != null && !currentPetId.isEmpty()) {
                intent.putExtra(AddReminderActivity.EXTRA_PET_ID, currentPetId);
                intent.putExtra(AddReminderActivity.EXTRA_PET_NAME, currentPetName);
            }
            addReminderLauncher.launch(intent);
        };

        binding.btnQuickAddReminder.setOnClickListener(openAddReminder);
        binding.btnEmptyScheduleReminder.setOnClickListener(openAddReminder);
        binding.btnAddNewReminder.setOnClickListener(openAddReminder);
    }

    private void loadReminders() {
        if (reminderRepository == null) return;

        reminderRepository.loadAllRemindersForUser(new ReminderRepository.RemindersCallback() {
            @Override
            public void onSuccess(List<Reminder> reminders) {
                allLoadedReminders.clear();
                if (reminders != null) {
                    allLoadedReminders.addAll(reminders);
                }
                updateReminderSummaryMetrics(allLoadedReminders);
                applyFilterAndRender();
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Failed loading pet care reminders", e);
            }
        });
    }

    private void updateReminderSummaryMetrics(List<Reminder> reminders) {
        int upcomingCount = 0;
        int dueTodayCount = 0;
        int overdueCount = 0;

        for (Reminder r : reminders) {
            if (Reminder.STATUS_COMPLETED.equalsIgnoreCase(r.getStatus()) || !r.isEnabled()) {
                continue;
            }
            String currentStatus = r.calculateCurrentStatus();
            if (Reminder.STATUS_OVERDUE.equalsIgnoreCase(currentStatus)) {
                overdueCount++;
            } else if (Reminder.STATUS_DUE_TODAY.equalsIgnoreCase(currentStatus)) {
                dueTodayCount++;
            } else {
                upcomingCount++;
            }
        }

        binding.tvStatUpcomingCount.setText(String.valueOf(upcomingCount));
        binding.tvStatDueTodayCount.setText(String.valueOf(dueTodayCount));
        binding.tvStatOverdueCount.setText(String.valueOf(overdueCount));
    }

    private void applyFilterAndRender() {
        List<Reminder> filtered = new ArrayList<>();
        for (Reminder r : allLoadedReminders) {
            if ("All".equalsIgnoreCase(activeCategoryFilter)) {
                filtered.add(r);
            } else if (activeCategoryFilter.equalsIgnoreCase(r.getType())) {
                filtered.add(r);
            }
        }

        reminderAdapter.submitList(filtered);

        if (filtered.isEmpty()) {
            binding.cardEmptyReminders.setVisibility(View.VISIBLE);
            binding.rvReminders.setVisibility(View.GONE);
        } else {
            binding.cardEmptyReminders.setVisibility(View.GONE);
            binding.rvReminders.setVisibility(View.VISIBLE);
        }
    }

    private void openEditReminder(Reminder reminder) {
        Intent intent = new Intent(InsightsActivity.this, AddReminderActivity.class);
        intent.putExtra(AddReminderActivity.EXTRA_REMINDER_ID, reminder.getReminderId());
        intent.putExtra(AddReminderActivity.EXTRA_PET_ID, reminder.getPetId());
        intent.putExtra(AddReminderActivity.EXTRA_PET_NAME, reminder.getPetName());
        addReminderLauncher.launch(intent);
    }

    private void markReminderCompleted(Reminder reminder) {
        reminderRepository.markCompleted(reminder, new ReminderRepository.SaveReminderCallback() {
            @Override
            public void onSuccess(Reminder updated) {
                ReminderNotificationHelper.cancelAlarm(InsightsActivity.this, updated.getReminderId());
                if (!Reminder.FREQ_NONE.equalsIgnoreCase(updated.getRepeatFrequency())) {
                    ReminderNotificationHelper.scheduleAlarm(InsightsActivity.this, updated);
                    String nextDateStr = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            .format(new Date(updated.getScheduledTimestamp()));
                    Toast.makeText(InsightsActivity.this, "Completed! Next scheduled for " + nextDateStr, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(InsightsActivity.this, "Reminder marked as completed!", Toast.LENGTH_SHORT).show();
                }
                loadReminders();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(InsightsActivity.this, "Failed to complete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void snoozeReminder(Reminder reminder) {
        long oneHourMillis = 60 * 60 * 1000L;
        reminderRepository.snoozeReminder(reminder, oneHourMillis, new ReminderRepository.SaveReminderCallback() {
            @Override
            public void onSuccess(Reminder updated) {
                ReminderNotificationHelper.scheduleAlarm(InsightsActivity.this, updated);
                Toast.makeText(InsightsActivity.this, "Snoozed for 1 hour", Toast.LENGTH_SHORT).show();
                loadReminders();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(InsightsActivity.this, "Failed to snooze: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleReminderEnabled(Reminder reminder, boolean isEnabled) {
        reminderRepository.toggleEnabled(reminder, isEnabled, new ReminderRepository.SaveReminderCallback() {
            @Override
            public void onSuccess(Reminder updated) {
                if (isEnabled) {
                    ReminderNotificationHelper.scheduleAlarm(InsightsActivity.this, updated);
                    Toast.makeText(InsightsActivity.this, "Reminder activated", Toast.LENGTH_SHORT).show();
                } else {
                    ReminderNotificationHelper.cancelAlarm(InsightsActivity.this, updated.getReminderId());
                    Toast.makeText(InsightsActivity.this, "Reminder paused", Toast.LENGTH_SHORT).show();
                }
                loadReminders();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(InsightsActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteReminder(Reminder reminder) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Reminder")
                .setMessage("Are you sure you want to delete \"" + reminder.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    reminderRepository.deleteReminder(reminder.getPetId(), reminder.getReminderId(), new ReminderRepository.DeleteCallback() {
                        @Override
                        public void onSuccess() {
                            ReminderNotificationHelper.cancelAlarm(InsightsActivity.this, reminder.getReminderId());
                            Toast.makeText(InsightsActivity.this, "Reminder deleted", Toast.LENGTH_SHORT).show();
                            loadReminders();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(InsightsActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
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
