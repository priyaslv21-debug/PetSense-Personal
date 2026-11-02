package com.example.pet.ui.health;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pet.MainActivity;
import com.example.pet.PetProfileActivity;
import com.example.pet.R;
import com.example.pet.data.ActivityRecord;
import com.example.pet.data.Pet;
import com.example.pet.data.PetRepository;
import com.example.pet.databinding.ActivityHealthBinding;
import com.example.pet.ui.behavior.BehaviorActivity;
import com.example.pet.ui.insights.InsightsActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HealthActivity extends AppCompatActivity {

    private ActivityHealthBinding binding;
    private PetRepository petRepository;

    private String currentPetId = "";
    private List<ActivityRecord> allRecords = new ArrayList<>();

    // Formatting for the Calendar logic
    private final Calendar selectedCalendar = Calendar.getInstance();
    private final SimpleDateFormat lookupFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHealthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        petRepository = new PetRepository();

        setupBottomNavigation();
        setupClickListeners();

        // Extract petId if passed via Intent, otherwise fetch default
        if (getIntent() != null && getIntent().hasExtra("petId")) {
            currentPetId = getIntent().getStringExtra("petId");
            binding.tvHeaderPetName.setText(getIntent().getStringExtra("petName"));
            binding.tvHeaderPetDetails.setText(getIntent().getStringExtra("petDetails"));
            loadAllRecords();
        } else {
            fetchDefaultPet();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra("petId")) {
            currentPetId = intent.getStringExtra("petId");
            binding.tvHeaderPetName.setText(intent.getStringExtra("petName"));
            binding.tvHeaderPetDetails.setText(intent.getStringExtra("petDetails"));
            loadAllRecords();
        }
    }

    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRefresh.setOnClickListener(v -> loadAllRecords());

        // Launch Native Calendar Dialog when the "Today" button is clicked
        binding.btnDateSelector.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedCalendar.set(year, month, dayOfMonth);
                    binding.tvDateLabel.setText(displayFormat.format(selectedCalendar.getTime()));
                    analyzeSelectedDate();
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void fetchDefaultPet() {
        petRepository.loadPets(new PetRepository.PetsCallback() {
            @Override
            public void onSuccess(List<Pet> pets) {
                if (pets != null && !pets.isEmpty()) {
                    Pet defaultPet = pets.get(0);
                    currentPetId = defaultPet.getId();
                    binding.tvHeaderPetName.setText(defaultPet.getName());
                    binding.tvHeaderPetDetails.setText(defaultPet.breedAgeLine());
                    loadAllRecords();
                }
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(HealthActivity.this, "Could not load pet data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAllRecords() {
        if (currentPetId == null || currentPetId.isEmpty()) return;

        petRepository.loadActivityRecords(currentPetId, new PetRepository.ActivityRecordsCallback() {
            @Override
            public void onSuccess(List<ActivityRecord> records) {
                allRecords = records;
                analyzeSelectedDate(); // Analyze today's date initially
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(HealthActivity.this, "Failed to load history", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void analyzeSelectedDate() {
        String targetDateStr = lookupFormat.format(selectedCalendar.getTime());
        ActivityRecord targetRecord = null;

        // Find the record matching the selected date
        for (ActivityRecord record : allRecords) {
            if (record.getDate() != null && lookupFormat.format(record.getDate()).equals(targetDateStr)) {
                targetRecord = record;
                break;
            }
        }

        if (targetRecord == null) {
            setNoDataState();
        } else {
            populateDashboard(targetRecord);
        }
    }

    private void populateDashboard(ActivityRecord record) {
        int score = record.getHealthScore();
        boolean hasLowAppetite = "Low".equalsIgnoreCase(record.getNutrition());
        boolean hasLethargy = "Low".equalsIgnoreCase(record.getActivity());

        // Update standard UI text
        binding.tvHealthScoreValue.setText(String.valueOf(score));
        binding.tvActivityValue.setText(record.getActivity());
        binding.tvSleepValue.setText(String.format(Locale.US, "%.1fh", record.getSleep()));
        binding.tvFeedingValue.setText(record.getNutrition());

        // Update Health Score Custom View if it has a setScore method (optional fallback)
        try {
            binding.healthScoreView.setScore(score, "");
        } catch (Exception ignored) {}

        // THE ANOMALY ENGINE ROUTING
        if (score < 50 || hasLowAppetite) {
            // CRITICAL (Red)
            binding.tvHealthScoreStatus.setText("Critical");
            binding.tvHealthScoreStatus.setTextColor(Color.parseColor("#D32F2F"));
            binding.cardHealthInsight.setCardBackgroundColor(Color.parseColor("#FFF0F0"));

            if (hasLowAppetite) {
                binding.tvHealthInsight.setText("Critical: Sudden loss of appetite detected. This is a primary indicator of systemic illness. Monitor closely.");
            } else {
                binding.tvHealthInsight.setText("Critical: Health metrics have dropped significantly below the baseline.");
            }
        } else if (score < 80 || hasLethargy) {
            // WARNING (Yellow)
            binding.tvHealthScoreStatus.setText("Irregular");
            binding.tvHealthScoreStatus.setTextColor(Color.parseColor("#F57F17"));
            binding.cardHealthInsight.setCardBackgroundColor(Color.parseColor("#FFF9E6"));

            if (hasLethargy) {
                binding.tvHealthInsight.setText("Note: Lethargy observed. Ensure the pet rests and check for signs of joint pain.");
            } else {
                binding.tvHealthInsight.setText("Note: Sleep architecture or activity patterns are irregular today.");
            }
        } else {
            // NORMAL (Green)
            binding.tvHealthScoreStatus.setText("Good");
            binding.tvHealthScoreStatus.setTextColor(Color.parseColor("#2E7D32"));
            binding.cardHealthInsight.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
            binding.tvHealthInsight.setText("Baseline Normal: All vital metrics are within healthy biological ranges.");
        }
    }

    private void setNoDataState() {
        binding.tvHealthScoreValue.setText("--");
        binding.tvHealthScoreStatus.setText("No Data");
        binding.tvHealthScoreStatus.setTextColor(Color.GRAY);

        binding.tvActivityValue.setText("--");
        binding.tvSleepValue.setText("--");
        binding.tvFeedingValue.setText("--");

        binding.cardHealthInsight.setCardBackgroundColor(Color.WHITE);
        binding.tvHealthInsight.setText("No health records found for this date. Click the + button in Insights to log daily vitals.");
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_health);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_health) {
                return true;
            } else if (id == R.id.nav_behavior) {
                Intent intent = new Intent(this, BehaviorActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_insights) {
                Intent intent = new Intent(this, InsightsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
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
}