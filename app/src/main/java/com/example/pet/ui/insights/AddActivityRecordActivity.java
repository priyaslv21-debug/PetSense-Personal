package com.example.pet.ui.insights;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pet.data.ActivityRecord;
import com.example.pet.data.PetRepository;
import com.example.pet.databinding.ActivityAddRecordBinding;

import java.util.Date;

public class AddActivityRecordActivity extends AppCompatActivity {

    private ActivityAddRecordBinding binding;
    private PetRepository petRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddRecordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        petRepository = new PetRepository();

        setupDropdowns();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSaveRecord.setOnClickListener(v -> saveRecord());
    }

    private void setupDropdowns() {
        String[] levels = {"Low", "Normal", "High"};
        String[] behaviors = {"Resting", "Walking", "Running", "Eating", "Happy", "Anxious", "Agitated"};

        binding.inputActivity.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, levels));
        binding.inputNutrition.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, levels));
        binding.inputBehavior.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, behaviors));
    }

    /**
     * Calculates a 0-100 Health Score based on AAHA and HHHHHMM veterinary guidelines.
     */
    private int calculateHealthScore(double sleep, String nutrition, String activity) {
        // 1. Nutrition Component (Max 40 points)
        // Highest weight because hyporexia/anorexia is a primary indicator of critical illness.
        int nutritionScore = 40; // Default to Normal
        if ("Low".equalsIgnoreCase(nutrition)) {
            nutritionScore = 10; // Severe penalty for lack of appetite
        } else if ("High".equalsIgnoreCase(nutrition)) {
            nutritionScore = 30; // Minor penalty for potential polyphagia (overeating)
        }

        // 2. Activity Component (Max 30 points)
        // Measures musculoskeletal comfort. Lethargy indicates pain or infection.
        int activityScore = 30; // Default to Normal/High
        if ("Low".equalsIgnoreCase(activity)) {
            activityScore = 15; // Moderate penalty for lethargy
        }

        // 3. Sleep Component (Max 30 points)
        // Optimal sleep is ~12 hours. We deduct 3 points for every hour away from 12.
        double sleepDeviation = Math.abs(sleep - 12.0);
        int sleepScore = 30 - (int)(sleepDeviation * 3);

        // Clamp sleep score so it doesn't go below 0 or above 30
        if (sleepScore < 0) sleepScore = 0;
        if (sleepScore > 30) sleepScore = 30;

        // Summation for final score (Max 100)
        return sleepScore + nutritionScore + activityScore;
    }

    private void saveRecord() {
        String activity = binding.inputActivity.getText().toString();
        String sleepStr = binding.inputSleep.getText().toString();
        String nutrition = binding.inputNutrition.getText().toString();
        String behavior = binding.inputBehavior.getText().toString();

        if (activity.isEmpty() || sleepStr.isEmpty() || nutrition.isEmpty() || behavior.isEmpty()) {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double sleep;
        try {
            sleep = Double.parseDouble(sleepStr);
        } catch (NumberFormatException e) {
            binding.inputSleepLayout.setError("Invalid sleep hours");
            return;
        }

        String petId = getIntent().getStringExtra("petId");
        if (petId == null || petId.isEmpty()) {
            Toast.makeText(this, "Error: No pet selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔴 Calculate the Medically-Backed Health Score Baseline
        int generatedHealthScore = calculateHealthScore(sleep, nutrition, activity);

        ActivityRecord record = new ActivityRecord(new Date(), activity, sleep, nutrition, behavior, generatedHealthScore);

        binding.btnSaveRecord.setEnabled(false);
        binding.btnSaveRecord.setText("Saving...");

        petRepository.addActivityRecord(petId, record, new PetRepository.SaveActivityRecordCallback() {
            @Override
            public void onSuccess(ActivityRecord savedRecord) {
                Toast.makeText(AddActivityRecordActivity.this, "Score: " + generatedHealthScore + "/100 Saved!", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(Exception e) {
                binding.btnSaveRecord.setEnabled(true);
                binding.btnSaveRecord.setText("Save Today's Record");
                Toast.makeText(AddActivityRecordActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}