package com.example.pet.ui.dashboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.pet.data.ActivityRecord;
import com.example.pet.data.Pet;
import com.example.pet.data.PetRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Holds dashboard state and supports switching between pets.
 *
 * Existing public API is unchanged. Added LiveData for the redesigned dashboard:
 * sleep/activity/feeding subtitles, water status/percent, health-score delta, last-updated time and the
 * behavior banner (time + text). All of the new values derived from Firestore come from the latest
 * ActivityRecord; defaults for values that have no data source yet (water, care reminder, user name,
 * demo pet) are the ones that were already in this class.
 */
public class DashboardViewModel extends ViewModel {

    private static final String NO_BEHAVIOR_TEXT = "No behavior logged yet";
    private static final String BEHAVIOR_ERROR_TEXT = "Couldn't load behavior";

    // Status pills for the Health tiles
    private final MutableLiveData<String> activityStatus = new MutableLiveData<>("Active");
    private final MutableLiveData<String> sleepStatus = new MutableLiveData<>("Good");
    private final MutableLiveData<String> feedingStatus = new MutableLiveData<>("On Track");

    // Water has no data source yet: defaults match the existing 1.8 L value against a 2.0 L goal.
    private final MutableLiveData<String> waterStatus = new MutableLiveData<>("Normal");
    private final MutableLiveData<Integer> waterPercent = new MutableLiveData<>(90);

    // Tile sub-labels (defaults = the reference text; replaced by values derived from the latest record)
    private final MutableLiveData<String> sleepSubtitle = new MutableLiveData<>("Optimal rest cycle");
    private final MutableLiveData<String> activitySubtitle = new MutableLiveData<>("Light indoor walk");
    private final MutableLiveData<String> feedingSubtitle = new MutableLiveData<>("Evening snack left");

    // Health-score card extras (null = nothing to show)
    private final MutableLiveData<Integer> healthScoreDelta = new MutableLiveData<>();
    private final MutableLiveData<Long> lastUpdatedMillis = new MutableLiveData<>();

    // Behavior banner
    private final MutableLiveData<String> behaviorBannerTime = new MutableLiveData<>("");
    private final MutableLiveData<String> behaviorBannerText = new MutableLiveData<>(NO_BEHAVIOR_TEXT);

    public LiveData<String> getActivityStatus() { return activityStatus; }
    public LiveData<String> getSleepStatus() { return sleepStatus; }
    public LiveData<String> getFeedingStatus() { return feedingStatus; }
    public LiveData<String> getWaterStatus() { return waterStatus; }
    public LiveData<Integer> getWaterPercent() { return waterPercent; }
    public LiveData<String> getSleepSubtitle() { return sleepSubtitle; }
    public LiveData<String> getActivitySubtitle() { return activitySubtitle; }
    public LiveData<String> getFeedingSubtitle() { return feedingSubtitle; }
    public LiveData<Integer> getHealthScoreDelta() { return healthScoreDelta; }
    public LiveData<Long> getLastUpdatedMillis() { return lastUpdatedMillis; }
    public LiveData<String> getBehaviorBannerTime() { return behaviorBannerTime; }
    public LiveData<String> getBehaviorBannerText() { return behaviorBannerText; }

    private final PetRepository petRepository = new PetRepository();

    private final MutableLiveData<String> userName = new MutableLiveData<>("Sarah");

    // Multi-pet state
    private final MutableLiveData<List<Pet>> pets =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Pet> selectedPet = new MutableLiveData<>();

    // Existing dashboard pet fields, updated when the selected pet changes
    private final MutableLiveData<String> petName = new MutableLiveData<>("Max");
    private final MutableLiveData<String> petBreedAge =
            new MutableLiveData<>("Golden Retriever · 2 Years");
    private final MutableLiveData<Boolean> petIsMale = new MutableLiveData<>(true);

    private final MutableLiveData<Integer> healthScore = new MutableLiveData<>(86);
    private final MutableLiveData<String> healthStatus = new MutableLiveData<>("Good");

    private final MutableLiveData<String> sleepValue = new MutableLiveData<>("8h 20m");
    private final MutableLiveData<String> activityValue = new MutableLiveData<>("2h 45m");
    private final MutableLiveData<String> feedingValue = new MutableLiveData<>("2 / 2 meals");
    private final MutableLiveData<String> waterValue = new MutableLiveData<>("1.8 L");

    private final MutableLiveData<String> careReminderDetail =
            new MutableLiveData<>("Next preventive task · in 3 weeks");

    // Kept from your original ViewModel
    private final MutableLiveData<String> healthInsight =
            new MutableLiveData<>(
                    "Max is maintaining a healthy activity and sleep pattern today."
            );

    public DashboardViewModel() {
        // Demo pet for the dashboard before/without Firestore data
        Pet demoPet = new Pet(
                "Max", "Dog", "Golden Retriever", "2 Years", null, true, "24 kg", null
        );

        List<Pet> initialPets = new ArrayList<>();
        initialPets.add(demoPet);
        pets.setValue(initialPets);
        selectPet(demoPet);

        refreshPetsFromRepository();
    }

    private void refreshPetsFromRepository() {
        try {
            petRepository.loadPets(new PetRepository.PetsCallback() {
                @Override
                public void onSuccess(List<Pet> loadedPets) {
                    if (loadedPets == null || loadedPets.isEmpty()) {
                        return;
                    }

                    pets.setValue(loadedPets);
                    selectPet(loadedPets.get(0));
                }

                @Override
                public void onError(Exception e) {
                    // Keep the demo pet visible if loading fails.
                }
            });
        } catch (IllegalStateException notSignedIn) {
            // Keep the demo pet visible if no user is signed in.
        }
    }

    // Getters
    public LiveData<String> getUserName() { return userName; }

    public LiveData<List<Pet>> getPets() { return pets; }
    public LiveData<Pet> getSelectedPet() { return selectedPet; }

    public LiveData<String> getPetName() { return petName; }
    public LiveData<String> getPetBreedAge() { return petBreedAge; }
    public LiveData<Boolean> getPetIsMale() { return petIsMale; }

    public LiveData<Integer> getHealthScore() { return healthScore; }
    public LiveData<String> getHealthStatus() { return healthStatus; }

    public LiveData<String> getSleepValue() { return sleepValue; }
    public LiveData<String> getActivityValue() { return activityValue; }
    public LiveData<String> getFeedingValue() { return feedingValue; }
    public LiveData<String> getWaterValue() { return waterValue; }

    public LiveData<String> getCareReminderDetail() {
        return careReminderDetail;
    }

    public LiveData<String> getHealthInsight() {
        return healthInsight;
    }

    // Actions

    /** Switches the active pet. */
    public void selectPet(@NonNull Pet pet) {
        selectedPet.setValue(pet);
        petName.setValue(pet.getName());
        petBreedAge.setValue(pet.breedAgeLine());
        petIsMale.setValue(pet.isMale());
        healthInsight.setValue(defaultInsight(pet.getName()));

        // Fetch the latest activity data for this specific pet
        if (pet.getId() != null) {
            fetchLatestActivityRecord(pet.getId());
        }
    }

    /**
     * Finds the pet with the given ID in the current list and selects it.
     * Called from MainActivity.onResume() after returning from PetProfileActivity
     * via FLAG_ACTIVITY_REORDER_TO_FRONT (so onCreate doesn't re-run).
     */
    public void selectPetById(@NonNull String petId) {
        List<Pet> current = pets.getValue();
        if (current == null) return;
        for (Pet p : current) {
            if (petId.equals(p.getId())) {
                selectPet(p);
                return;
            }
        }
        // ID not in cached list yet — reload from repository then select
        petRepository.loadPets(new PetRepository.PetsCallback() {
            @Override
            public void onSuccess(List<Pet> loadedPets) {
                if (loadedPets == null) return;
                pets.setValue(loadedPets);
                for (Pet p : loadedPets) {
                    if (petId.equals(p.getId())) {
                        selectPet(p);
                        return;
                    }
                }
            }

            @Override
            public void onError(Exception e) { /* keep current selection */ }
        });
    }

    private void fetchLatestActivityRecord(String petId) {
        petRepository.loadActivityRecords(petId, new PetRepository.ActivityRecordsCallback() {
            @Override
            public void onSuccess(List<ActivityRecord> records) {
                if (records != null && !records.isEmpty()) {
                    // The repository orders by date DESCENDING, so index 0 is the newest
                    ActivityRecord latest = records.get(0);

                    activityValue.setValue(latest.getActivity());
                    sleepValue.setValue(formatSleep(latest.getSleep()));
                    feedingValue.setValue(latest.getNutrition());

                    // Score, status text, tile pills, tile sub-labels and the insight sentence
                    calculateAndSetHealthScore(latest);

                    // Card extras
                    healthScoreDelta.setValue(deltaFromYesterday(latest, records));
                    lastUpdatedMillis.setValue(
                            latest.getDate() != null ? Long.valueOf(latest.getDate().getTime()) : null);
                    updateBehaviorBanner(latest);
                } else {
                    activityValue.setValue("No data");
                    sleepValue.setValue("No data");
                    feedingValue.setValue("No data");

                    // Clear pills for empty state
                    activityStatus.setValue("No data");
                    sleepStatus.setValue("No data");
                    feedingStatus.setValue("No data");

                    sleepSubtitle.setValue("No data yet");
                    activitySubtitle.setValue("No data yet");
                    feedingSubtitle.setValue("No data yet");

                    healthScore.setValue(0);
                    healthStatus.setValue("No data");
                    healthScoreDelta.setValue(null);
                    lastUpdatedMillis.setValue(null);
                    behaviorBannerTime.setValue("");
                    behaviorBannerText.setValue(NO_BEHAVIOR_TEXT);
                    healthInsight.setValue("No activity recorded yet for " + currentPetName()
                            + ". Add a record to see today's insights.");
                }
            }

            @Override
            public void onError(Exception e) {
                activityValue.setValue("Error");
                sleepValue.setValue("Error");
                feedingValue.setValue("Error");

                // Clear pills for error state
                activityStatus.setValue("Error");
                sleepStatus.setValue("Error");
                feedingStatus.setValue("Error");

                sleepSubtitle.setValue("Couldn't load");
                activitySubtitle.setValue("Couldn't load");
                feedingSubtitle.setValue("Couldn't load");

                healthScore.setValue(0);
                healthStatus.setValue("Error");
                healthScoreDelta.setValue(null);
                lastUpdatedMillis.setValue(null);
                behaviorBannerTime.setValue("");
                behaviorBannerText.setValue(BEHAVIOR_ERROR_TEXT);
                healthInsight.setValue("Couldn't load " + currentPetName() + "'s activity data.");
            }
        });
    }

    /** Saves a new pet, adds it to the list, and selects it. */
    public void addPet(@NonNull Pet pet, @NonNull Runnable onDone) {
        try {
            petRepository.addPet(pet, new PetRepository.SavePetCallback() {
                @Override
                public void onSuccess(Pet savedPet) {
                    List<Pet> current = pets.getValue() != null
                            ? pets.getValue()
                            : new ArrayList<>();

                    List<Pet> updated = new ArrayList<>(current);
                    updated.add(savedPet);
                    pets.setValue(updated);

                    selectPet(savedPet);
                    onDone.run();
                }

                @Override
                public void onError(Exception e) {
                    // Local fallback if Firestore saving fails.
                    addPetLocallyOnly(pet);
                    onDone.run();
                }
            });
        } catch (IllegalStateException notSignedIn) {
            addPetLocallyOnly(pet);
            onDone.run();
        }
    }

    private void addPetLocallyOnly(@NonNull Pet pet) {
        List<Pet> current = pets.getValue() != null
                ? pets.getValue()
                : new ArrayList<>();

        List<Pet> updated = new ArrayList<>(current);
        updated.add(pet);
        pets.setValue(updated);

        selectPet(pet);
    }

    public void updatePet(@NonNull Pet pet, @NonNull Runnable onDone) {
        try {
            petRepository.updatePet(pet, new PetRepository.SavePetCallback() {
                @Override
                public void onSuccess(Pet savedPet) {
                    updatePetLocallyOnly(savedPet);
                    onDone.run();
                }

                @Override
                public void onError(Exception e) {
                    updatePetLocallyOnly(pet);
                    onDone.run();
                }
            });
        } catch (IllegalStateException notSignedIn) {
            updatePetLocallyOnly(pet);
            onDone.run();
        }
    }

    public void updatePetLocallyOnly(@NonNull Pet pet) {
        List<Pet> current = pets.getValue() != null ? pets.getValue() : new ArrayList<>();
        List<Pet> updated = new ArrayList<>();
        for (Pet p : current) {
            if (p.getId() != null && p.getId().equals(pet.getId())) {
                updated.add(pet);
            } else if (p.getName().equals(pet.getName())) { // Fallback if no ID
                updated.add(pet);
            } else {
                updated.add(p);
            }
        }
        pets.setValue(updated);
        selectPet(pet);
    }

    public void updateHealthScore(int score, @NonNull String status) {
        healthScore.setValue(score);
        healthStatus.setValue(status);
    }

    /** Forces a refresh of the currently selected pet's activity data */
    public void refreshCurrentPetActivity() {
        Pet currentPet = selectedPet.getValue();
        if (currentPet != null && currentPet.getId() != null) {
            fetchLatestActivityRecord(currentPet.getId());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Derived values
    // ------------------------------------------------------------------------------------------

    /**
     * Same scoring rules as before (activity 33/15, nutrition 33/15, sleep 34/20/10), now evaluated on the
     * real sleep value. The old code stripped every non-digit from the double, so 4.5 h became "45" and 0.5 h
     * became "05"; any sleep >= 0.8 h could therefore score full sleep points.
     */
    private int computeScore(ActivityRecord record) {
        int score = 0;

        String act = record.getActivity();
        if ("High".equalsIgnoreCase(act) || "Normal".equalsIgnoreCase(act)) {
            score += 33;
        } else if ("Low".equalsIgnoreCase(act)) {
            score += 15;
        }

        String nut = record.getNutrition();
        if ("High".equalsIgnoreCase(nut) || "Normal".equalsIgnoreCase(nut)) {
            score += 33;
        } else if ("Low".equalsIgnoreCase(nut)) {
            score += 15;
        }

        double sleepHours = record.getSleep();
        if (sleepHours >= 8) {
            score += 34;
        } else if (sleepHours >= 4) {
            score += 20;
        } else if (sleepHours > 0) {
            score += 10;
        }

        return score;
    }

    private void calculateAndSetHealthScore(ActivityRecord latest) {
        if (latest == null) return;

        int score = computeScore(latest);
        String act = latest.getActivity();
        String nut = latest.getNutrition();
        double sleepHours = latest.getSleep();

        // Determine status
        String status = "Attention";
        if (score >= 80) {
            status = "Good";
        } else if (score >= 50) {
            status = "Fair";
        }

        healthScore.setValue(score);
        healthStatus.setValue(status);

        // --- Status pill logic ---
        if ("Low".equalsIgnoreCase(act)) {
            activityStatus.setValue("Low Activity");
        } else {
            activityStatus.setValue("Active");
        }

        if ("Low".equalsIgnoreCase(nut)) {
            feedingStatus.setValue("Warning");
        } else {
            feedingStatus.setValue("On Track");
        }

        if (sleepHours >= 8) {
            sleepStatus.setValue("Good");
            sleepSubtitle.setValue("Optimal rest cycle");
        } else if (sleepHours >= 4) {
            sleepStatus.setValue("Fair");
            sleepSubtitle.setValue("Slightly short rest");
        } else {
            sleepStatus.setValue("Warning");
            sleepSubtitle.setValue("Not enough rest");
        }

        // --- Tile sub-labels derived from the record ---
        if ("Low".equalsIgnoreCase(act)) {
            activitySubtitle.setValue("Light activity today");
        } else if ("High".equalsIgnoreCase(act)) {
            activitySubtitle.setValue("Very active day");
        } else if ("Normal".equalsIgnoreCase(act)) {
            activitySubtitle.setValue("Moderate activity");
        } else {
            activitySubtitle.setValue("");
        }

        if ("Low".equalsIgnoreCase(nut)) {
            feedingSubtitle.setValue("Appetite is low");
        } else if ("High".equalsIgnoreCase(nut)) {
            feedingSubtitle.setValue("Healthy appetite");
        } else if ("Normal".equalsIgnoreCase(nut)) {
            feedingSubtitle.setValue("Appetite normal");
        } else {
            feedingSubtitle.setValue("");
        }

        // --- Insight sentence follows the real score ---
        String name = currentPetName();
        if ("Good".equals(status)) {
            healthInsight.setValue(defaultInsight(name));
        } else if ("Fair".equals(status)) {
            healthInsight.setValue(name + " had a mixed day. Check sleep, activity and appetite.");
        } else {
            healthInsight.setValue(name + " may need attention today. Review the latest activity record.");
        }
    }

    /** Score change versus the newest record from the previous calendar day, or null if there isn't one. */
    @Nullable
    private Integer deltaFromYesterday(ActivityRecord latest, List<ActivityRecord> records) {
        if (latest.getDate() == null) return null;
        for (ActivityRecord candidate : records) {
            if (candidate != latest && candidate.getDate() != null
                    && isDayBefore(candidate.getDate(), latest.getDate())) {
                return computeScore(latest) - computeScore(candidate);
            }
        }
        return null;
    }

    private void updateBehaviorBanner(ActivityRecord latest) {
        String behavior = latest.getBehavior();
        if (behavior == null || behavior.trim().isEmpty()) {
            behaviorBannerTime.setValue("");
            behaviorBannerText.setValue(NO_BEHAVIOR_TEXT);
            return;
        }
        behaviorBannerTime.setValue(latest.getDate() != null ? formatBannerTime(latest.getDate()) : "");
        behaviorBannerText.setValue(behavior.trim() + " behavior logged");
    }

    private String currentPetName() {
        String name = petName.getValue();
        return (name == null || name.trim().isEmpty()) ? "Your pet" : name;
    }

    private static String defaultInsight(@Nullable String name) {
        String n = (name == null || name.trim().isEmpty()) ? "Your pet" : name;
        return n + " is maintaining a healthy activity and sleep pattern today.";
    }

    /** 8.33 -> "8h 20m", 8.0 -> "8h", 0.5 -> "30m". */
    static String formatSleep(double hours) {
        if (hours <= 0) return "0h";
        int totalMinutes = (int) Math.round(hours * 60);
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        if (m == 0) return h + "h";
        if (h == 0) return m + "m";
        return h + "h " + m + "m";
    }

    private static String formatBannerTime(Date date) {
        String pattern = isSameDay(date, new Date()) ? "h:mm a" : "MMM d";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
    }

    private static boolean isSameDay(Date a, Date b) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(a);
        Calendar cb = Calendar.getInstance();
        cb.setTime(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    /** True when {@code earlier} falls on the calendar day immediately before {@code later}. */
    private static boolean isDayBefore(Date earlier, Date later) {
        Calendar expected = Calendar.getInstance();
        expected.setTime(later);
        expected.add(Calendar.DAY_OF_YEAR, -1);
        Calendar actual = Calendar.getInstance();
        actual.setTime(earlier);
        return expected.get(Calendar.YEAR) == actual.get(Calendar.YEAR)
                && expected.get(Calendar.DAY_OF_YEAR) == actual.get(Calendar.DAY_OF_YEAR);
    }
}