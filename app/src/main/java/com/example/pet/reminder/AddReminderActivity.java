package com.example.pet.reminder;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pet.R;
import com.example.pet.data.Pet;
import com.example.pet.data.PetRepository;
import com.example.pet.data.Reminder;
import com.example.pet.data.ReminderRepository;
import com.example.pet.databinding.ActivityAddReminderBinding;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity for creating or editing a Pet Care Reminder (Vaccination, Check-up, Deworming, Medication).
 */
public class AddReminderActivity extends AppCompatActivity {

    private static final String TAG = "AddReminderActivity";

    public static final String EXTRA_REMINDER_ID = "extra_reminder_id";
    public static final String EXTRA_PET_ID = "extra_pet_id";
    public static final String EXTRA_PET_NAME = "extra_pet_name";

    private ActivityAddReminderBinding binding;
    private final PetRepository petRepository = new PetRepository();
    private final ReminderRepository reminderRepository = new ReminderRepository();

    private final List<Pet> userPets = new ArrayList<>();
    private Pet selectedPet = null;

    private final Calendar scheduledCalendar = Calendar.getInstance();
    private boolean dateSelected = false;
    private boolean timeSelected = false;

    private boolean isEditMode = false;
    private String editingReminderId = null;
    private Reminder existingReminder = null;

    private final List<String> medicationAdminTimes = new ArrayList<>();

    private static final String[] CATEGORIES = {
            Reminder.TYPE_VACCINATION,
            Reminder.TYPE_CHECKUP,
            Reminder.TYPE_DEWORMING,
            Reminder.TYPE_MEDICATION
    };

    private static final String[] REPEAT_OPTIONS = {
            Reminder.FREQ_NONE,
            Reminder.FREQ_DAILY,
            Reminder.FREQ_WEEKLY,
            Reminder.FREQ_MONTHLY,
            Reminder.FREQ_YEARLY
    };

    private static final String[] ADVANCE_OPTIONS = {
            Reminder.ADVANCE_EXACT,
            Reminder.ADVANCE_1_HOUR,
            Reminder.ADVANCE_1_DAY,
            Reminder.ADVANCE_2_DAYS,
            Reminder.ADVANCE_1_WEEK
    };

    private static final String[] MED_FREQUENCIES = {
            "Once daily",
            "Twice daily",
            "Three times daily",
            "Every 8 hours",
            "Every 12 hours",
            "As needed"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddReminderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        editingReminderId = getIntent().getStringExtra(EXTRA_REMINDER_ID);
        isEditMode = (editingReminderId != null && !editingReminderId.trim().isEmpty());

        setupDropdowns();
        setupClickListeners();
        loadPets();

        if (isEditMode) {
            binding.tvHeaderTitle.setText("Edit Pet Care Reminder");
            binding.btnSaveReminder.setText("Save Changes");
        } else {
            // Default time to next hour
            scheduledCalendar.add(Calendar.HOUR_OF_DAY, 1);
            scheduledCalendar.set(Calendar.MINUTE, 0);
            scheduledCalendar.set(Calendar.SECOND, 0);
        }
    }

    private void setupDropdowns() {
        // Categories
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, CATEGORIES);
        binding.dropdownCategory.setAdapter(categoryAdapter);
        binding.dropdownCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = CATEGORIES[position];
            onCategorySelected(selected);
        });

        // Repeat
        ArrayAdapter<String> repeatAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, REPEAT_OPTIONS);
        binding.dropdownRepeat.setAdapter(repeatAdapter);
        binding.dropdownRepeat.setText(Reminder.FREQ_NONE, false);

        // Advance Notice
        ArrayAdapter<String> advanceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, ADVANCE_OPTIONS);
        binding.dropdownAdvance.setAdapter(advanceAdapter);
        binding.dropdownAdvance.setText(Reminder.ADVANCE_EXACT, false);

        // Medication frequency
        ArrayAdapter<String> medFreqAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, MED_FREQUENCIES);
        binding.dropdownMedFrequency.setAdapter(medFreqAdapter);
        binding.dropdownMedFrequency.setText("Once daily", false);
    }

    private void setupClickListeners() {
        binding.btnAddReminderBack.setOnClickListener(v -> finish());

        // Date picker
        binding.etReminderDate.setOnClickListener(v -> showDatePicker());

        // Time picker
        binding.etReminderTime.setOnClickListener(v -> showTimePicker());

        // Med Start & End Date pickers
        binding.etMedStartDate.setOnClickListener(v -> showDatePickerForView(binding.etMedStartDate));
        binding.etMedEndDate.setOnClickListener(v -> showDatePickerForView(binding.etMedEndDate));

        // Add Administration Time button
        binding.btnAddAdminTime.setOnClickListener(v -> showTimePickerForAdmin());

        // Save Reminder Button
        binding.btnSaveReminder.setOnClickListener(v -> onSaveClicked());
    }

    private void onCategorySelected(String category) {
        boolean isMed = Reminder.TYPE_MEDICATION.equalsIgnoreCase(category);
        binding.cardMedicationSection.setVisibility(isMed ? View.VISIBLE : View.GONE);

        // Populate quick suggestions
        binding.chipGroupSuggestions.removeAllViews();
        String[] suggestions;

        if (Reminder.TYPE_VACCINATION.equalsIgnoreCase(category)) {
            suggestions = new String[]{"Rabies Booster", "DHPP Vaccine", "Bordetella", "FVRCP Core"};
        } else if (Reminder.TYPE_CHECKUP.equalsIgnoreCase(category)) {
            suggestions = new String[]{"Annual Wellness Exam", "Dental Cleaning", "Bloodwork Panel", "Weight Check"};
        } else if (Reminder.TYPE_DEWORMING.equalsIgnoreCase(category)) {
            suggestions = new String[]{"Broad-Spectrum Dewormer", "Heartworm Prevention", "Flea & Tick Treatment"};
        } else {
            suggestions = new String[]{"Antibiotics Course", "Ear Drops", "Pain Relief", "Anti-inflammatory"};
        }

        binding.tvSuggestionsLabel.setVisibility(suggestions.length > 0 ? View.VISIBLE : View.GONE);
        for (String suggestion : suggestions) {
            Chip chip = new Chip(this);
            chip.setText(suggestion);
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(R.color.pd_lavender);
            chip.setTextColor(getColor(R.color.ps_primary));
            chip.setOnClickListener(v -> binding.etReminderTitle.setText(suggestion));
            binding.chipGroupSuggestions.addView(chip);
        }
    }

    private void loadPets() {
        petRepository.loadPets(new PetRepository.PetsCallback() {
            @Override
            public void onSuccess(List<Pet> pets) {
                userPets.clear();
                if (pets != null) {
                    userPets.addAll(pets);
                }

                if (userPets.isEmpty()) {
                    Toast.makeText(AddReminderActivity.this,
                            "No pets registered yet. Please add a pet profile first.",
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                List<String> petNames = new ArrayList<>();
                for (Pet p : userPets) {
                    petNames.add(p.getName() + " (" + p.getSpecies() + ")");
                }

                ArrayAdapter<String> petAdapter = new ArrayAdapter<>(
                        AddReminderActivity.this, android.R.layout.simple_list_item_1, petNames);
                binding.dropdownPet.setAdapter(petAdapter);
                binding.dropdownPet.setOnItemClickListener((parent, view, position, id) -> {
                    selectedPet = userPets.get(position);
                });

                // Check if a specific petId was passed via intent
                String requestedPetId = getIntent().getStringExtra(EXTRA_PET_ID);
                int selectedIdx = 0;
                if (requestedPetId != null) {
                    for (int i = 0; i < userPets.size(); i++) {
                        if (requestedPetId.equals(userPets.get(i).getId())) {
                            selectedIdx = i;
                            break;
                        }
                    }
                }

                selectedPet = userPets.get(selectedIdx);
                binding.dropdownPet.setText(petNames.get(selectedIdx), false);

                // If in Edit mode, load the reminder now that pets are loaded
                if (isEditMode) {
                    loadReminderForEditing();
                } else {
                    // Default to first category
                    binding.dropdownCategory.setText(CATEGORIES[0], false);
                    onCategorySelected(CATEGORIES[0]);
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AddReminderActivity.this, "Failed to load pets: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadReminderForEditing() {
        if (selectedPet == null || editingReminderId == null) return;

        reminderRepository.loadRemindersForPet(selectedPet.getId(), new ReminderRepository.RemindersCallback() {
            @Override
            public void onSuccess(List<Reminder> reminders) {
                for (Reminder r : reminders) {
                    if (editingReminderId.equals(r.getReminderId())) {
                        existingReminder = r;
                        populateFieldsForEdit(r);
                        break;
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(AddReminderActivity.this, "Could not load reminder details.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateFieldsForEdit(Reminder r) {
        binding.etReminderTitle.setText(r.getTitle());
        binding.dropdownCategory.setText(r.getReminderType(), false);
        onCategorySelected(r.getReminderType());

        if (r.getDescription() != null) {
            binding.etReminderDescription.setText(r.getDescription());
        }

        if (r.getScheduledDate() != null) {
            binding.etReminderDate.setText(r.getScheduledDate());
            dateSelected = true;
        }

        if (r.getScheduledTime() != null) {
            binding.etReminderTime.setText(r.getScheduledTime());
            timeSelected = true;
        }

        if (r.getScheduledTimestamp() > 0) {
            scheduledCalendar.setTimeInMillis(r.getScheduledTimestamp());
        }

        if (r.getRepeatFrequency() != null) {
            binding.dropdownRepeat.setText(r.getRepeatFrequency(), false);
        }

        if (r.getAdvanceNotification() != null) {
            binding.dropdownAdvance.setText(r.getAdvanceNotification(), false);
        }

        // Medication fields
        if (Reminder.TYPE_MEDICATION.equalsIgnoreCase(r.getReminderType())) {
            if (r.getMedicationName() != null) binding.etMedName.setText(r.getMedicationName());
            if (r.getStartDate() != null) binding.etMedStartDate.setText(r.getStartDate());
            if (r.getEndDate() != null) binding.etMedEndDate.setText(r.getEndDate());
            if (r.getDosageFrequency() != null) binding.dropdownMedFrequency.setText(r.getDosageFrequency(), false);

            if (r.getAdministrationTimes() != null) {
                medicationAdminTimes.clear();
                medicationAdminTimes.addAll(r.getAdministrationTimes());
                renderAdminTimesChips();
            }
        }
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        if (dateSelected) cal.setTime(scheduledCalendar.getTime());

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    scheduledCalendar.set(Calendar.YEAR, year);
                    scheduledCalendar.set(Calendar.MONTH, month);
                    scheduledCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    dateSelected = true;

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    binding.etReminderDate.setText(sdf.format(scheduledCalendar.getTime()));
                    binding.tilDate.setError(null);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        if (timeSelected) cal.setTime(scheduledCalendar.getTime());

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    scheduledCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    scheduledCalendar.set(Calendar.MINUTE, minute);
                    scheduledCalendar.set(Calendar.SECOND, 0);
                    timeSelected = true;

                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                    binding.etReminderTime.setText(sdf.format(scheduledCalendar.getTime()));
                    binding.tilTime.setError(null);
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
        );
        dialog.show();
    }

    private void showDatePickerForView(com.google.android.material.textfield.TextInputEditText targetView) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar c = Calendar.getInstance();
                    c.set(year, month, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    targetView.setText(sdf.format(c.getTime()));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showTimePickerForAdmin() {
        Calendar cal = Calendar.getInstance();
        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    Calendar c = Calendar.getInstance();
                    c.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    c.set(Calendar.MINUTE, minute);
                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                    String timeStr = sdf.format(c.getTime());

                    if (!medicationAdminTimes.contains(timeStr)) {
                        medicationAdminTimes.add(timeStr);
                        renderAdminTimesChips();
                    }
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
        ).show();
    }

    private void renderAdminTimesChips() {
        binding.chipGroupAdminTimes.removeAllViews();
        for (String time : medicationAdminTimes) {
            Chip chip = new Chip(this);
            chip.setText(time);
            chip.setCloseIconVisible(true);
            chip.setChipBackgroundColorResource(R.color.pd_lavender);
            chip.setTextColor(getColor(R.color.ps_primary));
            chip.setOnCloseIconClickListener(v -> {
                medicationAdminTimes.remove(time);
                renderAdminTimesChips();
            });
            binding.chipGroupAdminTimes.addView(chip);
        }
    }

    private void onSaveClicked() {
        if (selectedPet == null) {
            binding.tilPet.setError("Please select a pet");
            return;
        } else {
            binding.tilPet.setError(null);
        }

        String category = binding.dropdownCategory.getText().toString().trim();
        if (category.isEmpty()) {
            binding.tilCategory.setError("Please select a category");
            return;
        } else {
            binding.tilCategory.setError(null);
        }

        String title = textOf(binding.etReminderTitle);
        if (title.isEmpty()) {
            binding.tilTitle.setError("Reminder title is required");
            return;
        } else {
            binding.tilTitle.setError(null);
        }

        String dateStr = textOf(binding.etReminderDate);
        if (dateStr.isEmpty()) {
            binding.tilDate.setError("Date is required");
            return;
        } else {
            binding.tilDate.setError(null);
        }

        String timeStr = textOf(binding.etReminderTime);
        if (timeStr.isEmpty()) {
            binding.tilTime.setError("Time is required");
            return;
        } else {
            binding.tilTime.setError(null);
        }

        boolean isMed = Reminder.TYPE_MEDICATION.equalsIgnoreCase(category);
        String medName = textOf(binding.etMedName);
        if (isMed && medName.isEmpty()) {
            binding.tilMedName.setError("Medication name is required");
            return;
        } else {
            binding.tilMedName.setError(null);
        }

        String desc = textOf(binding.etReminderDescription);
        String repeat = binding.dropdownRepeat.getText().toString().trim();
        String advance = binding.dropdownAdvance.getText().toString().trim();

        Reminder reminder = (isEditMode && existingReminder != null) ? existingReminder : new Reminder();
        reminder.setPetId(selectedPet.getId());
        reminder.setPetName(selectedPet.getName());
        reminder.setReminderType(category);
        reminder.setTitle(title);
        reminder.setDescription(desc);
        reminder.setScheduledDate(dateStr);
        reminder.setScheduledTime(timeStr);
        reminder.setScheduledTimestamp(scheduledCalendar.getTimeInMillis());
        reminder.setRepeatFrequency(!repeat.isEmpty() ? repeat : Reminder.FREQ_NONE);
        reminder.setAdvanceNotification(!advance.isEmpty() ? advance : Reminder.ADVANCE_EXACT);
        reminder.setStatus(reminder.calculateCurrentStatus());
        reminder.setEnabled(true);

        if (isMed) {
            reminder.setMedicationName(medName);
            reminder.setStartDate(textOf(binding.etMedStartDate));
            reminder.setEndDate(textOf(binding.etMedEndDate));
            reminder.setDosageFrequency(binding.dropdownMedFrequency.getText().toString().trim());
            reminder.setAdministrationTimes(new ArrayList<>(medicationAdminTimes));
        }

        setLoading(true);

        if (isEditMode) {
            reminderRepository.updateReminder(reminder, new ReminderRepository.SaveReminderCallback() {
                @Override
                public void onSuccess(Reminder saved) {
                    handleSuccess(saved, "Reminder updated successfully!");
                }

                @Override
                public void onError(Exception e) {
                    handleError(e);
                }
            });
        } else {
            reminderRepository.addReminder(reminder, new ReminderRepository.SaveReminderCallback() {
                @Override
                public void onSuccess(Reminder saved) {
                    handleSuccess(saved, "Reminder scheduled successfully!");
                }

                @Override
                public void onError(Exception e) {
                    handleError(e);
                }
            });
        }
    }

    private void handleSuccess(Reminder saved, String msg) {
        setLoading(false);
        Log.i(TAG, "Reminder persisted: " + saved.getReminderId());

        // Cancel previous alarm if updating and schedule new alarm
        ReminderNotificationHelper.cancelAlarm(this, saved.getReminderId());
        ReminderNotificationHelper.scheduleAlarm(this, saved);

        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

        Intent result = new Intent();
        result.putExtra(EXTRA_REMINDER_ID, saved.getReminderId());
        result.putExtra(EXTRA_PET_ID, saved.getPetId());
        setResult(RESULT_OK, result);
        finish();
    }

    private void handleError(Exception e) {
        setLoading(false);
        Log.e(TAG, "Failed saving reminder", e);
        Toast.makeText(this, "Failed to save reminder: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSaveReminder.setEnabled(!loading);
    }

    private String textOf(com.google.android.material.textfield.TextInputEditText et) {
        return (et.getText() != null) ? et.getText().toString().trim() : "";
    }
}
