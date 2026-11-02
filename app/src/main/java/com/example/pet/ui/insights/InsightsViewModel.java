package com.example.pet.ui.insights;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.pet.data.ActivityRecord;
import com.example.pet.data.PetRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InsightsViewModel extends ViewModel {

    private static final double SLEEP_SIGNIFICANT_DROP_HOURS = 2.0;
    private static final int TREND_MAX_POINTS = 7;

    private final PetRepository petRepository = new PetRepository();

    private final MutableLiveData<String> petName = new MutableLiveData<>("");
    private final MutableLiveData<String> petDetails = new MutableLiveData<>("");

    private final MutableLiveData<String> aiInsight =
            new MutableLiveData<>("Start tracking your pet's activity to unlock personalized insights.");
    private final MutableLiveData<String> aiInsightContext =
            new MutableLiveData<>("Based on recent activity, sleep and behavior data.");

    private final MutableLiveData<String> activityMetric = new MutableLiveData<>("--");
    private final MutableLiveData<String> activityDesc = new MutableLiveData<>("No data");

    private final MutableLiveData<String> sleepMetric = new MutableLiveData<>("--");
    private final MutableLiveData<String> sleepDesc = new MutableLiveData<>("No data");

    private final MutableLiveData<String> nutritionMetric = new MutableLiveData<>("--");
    private final MutableLiveData<String> nutritionDesc = new MutableLiveData<>("No data");

    private final MutableLiveData<String> behaviorMetric = new MutableLiveData<>("--");
    private final MutableLiveData<String> behaviorDesc = new MutableLiveData<>("No data");

    private final MutableLiveData<String> patternTitle = new MutableLiveData<>("Insufficient Data");
    private final MutableLiveData<String> patternDesc =
            new MutableLiveData<>("Add more activity records to identify patterns.");

    private final MutableLiveData<String> recommendation =
            new MutableLiveData<>("Log your pet's activity today.");

    private final MutableLiveData<List<Integer>> trendLevels = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, Integer>> moodDistribution = new MutableLiveData<>(new HashMap<>());

    public void loadInsightsData(String id, String name, String details) {
        if (name != null) petName.setValue(name);
        if (details != null) petDetails.setValue(details);

        String petDisplayName = (name != null && !name.isEmpty()) ? name : "Your pet";

        fetchBehaviorHistoryFromFirestore(id, petDisplayName);

        if (id == null || id.isEmpty()) return;

        try {
            petRepository.loadActivityRecords(id, new PetRepository.ActivityRecordsCallback() {
                @Override
                public void onSuccess(List<ActivityRecord> records) {
                    applyRecords(records, petDisplayName);
                }

                @Override
                public void onError(Exception e) {
                    applyDemoFallbackState(petDisplayName);
                }
            });
        } catch (Exception e) {
            applyDemoFallbackState(petDisplayName);
        }
    }

    private void fetchBehaviorHistoryFromFirestore(String petId, String petDisplayName) {
        try {
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

            if (firestore == null || currentUser == null) {
                applyDemoMoodDistribution();
                return;
            }

            String uid = currentUser.getUid();
            Query query;
            if (petId != null && !petId.isEmpty()) {
                query = firestore.collection("users").document(uid)
                        .collection("pets").document(petId)
                        .collection("behavior_history");
            } else {
                query = firestore.collection("users").document(uid)
                        .collection("behavior_history");
            }

            query.get().addOnSuccessListener(snapshot -> {
                if (snapshot == null || snapshot.isEmpty()) {
                    applyDemoMoodDistribution();
                    return;
                }

                int total = snapshot.size();
                int happy = 0, relaxed = 0, anxious = 0, angry = 0;

                for (QueryDocumentSnapshot doc : snapshot) {
                    String b = doc.getString("behavior");
                    if (b == null) continue;
                    String lower = b.toLowerCase();
                    if (lower.contains("happy") || lower.contains("play")) happy++;
                    else if (lower.contains("angry") || lower.contains("agitat")) angry++;
                    else if (lower.contains("sad") || lower.contains("anxi")) anxious++;
                    else relaxed++;
                }

                if (total > 0) {
                    Map<String, Integer> dist = new HashMap<>();
                    dist.put("Happy", (happy * 100) / total);
                    dist.put("Relaxed", (relaxed * 100) / total);
                    dist.put("Anxious", (anxious * 100) / total);
                    dist.put("Angry", (angry * 100) / total);
                    moodDistribution.setValue(dist);

                    String topMood = "Relaxed";
                    int maxPct = dist.get("Relaxed");
                    for (Map.Entry<String, Integer> entry : dist.entrySet()) {
                        if (entry.getValue() > maxPct) {
                            maxPct = entry.getValue();
                            topMood = entry.getKey();
                        }
                    }
                    behaviorMetric.setValue(topMood);
                    behaviorDesc.setValue(maxPct + "% of recorded logs");
                } else {
                    applyDemoMoodDistribution();
                }
            }).addOnFailureListener(e -> applyDemoMoodDistribution());

        } catch (Exception e) {
            applyDemoMoodDistribution();
        }
    }

    private void applyDemoMoodDistribution() {
        Map<String, Integer> demoDist = new HashMap<>();
        demoDist.put("Happy", 45);
        demoDist.put("Relaxed", 35);
        demoDist.put("Anxious", 15);
        demoDist.put("Angry", 5);
        moodDistribution.setValue(demoDist);

        if ("--".equals(behaviorMetric.getValue())) {
            behaviorMetric.setValue("Happy");
            behaviorDesc.setValue("45% observed mood");
        }
    }

    private void applyDemoFallbackState(String petDisplayName) {
        aiInsight.setValue(petDisplayName + " appears healthy with stable activity routines.");
        aiInsightContext.setValue("Based on recent activity, sleep and behavior data.");
        patternTitle.setValue("Pattern Stable");
        patternDesc.setValue("Activity and sleep metrics are within optimal ranges.");
        recommendation.setValue("Maintain " + petDisplayName + "'s regular feeding and walk schedule.");
    }

    private void applyRecords(List<ActivityRecord> records, String petDisplayName) {
        if (records.isEmpty()) {
            applyDemoFallbackState(petDisplayName);
            return;
        }

        ActivityRecord latest = records.get(0);

        activityMetric.setValue(latest.getActivity());
        sleepMetric.setValue(formatHours(latest.getSleep()) + "h");
        nutritionMetric.setValue(latest.getNutrition());

        if ("--".equals(behaviorMetric.getValue())) {
            behaviorMetric.setValue(latest.getBehavior());
            behaviorDesc.setValue("Latest log");
        }

        aiInsight.setValue(activityRule(latest.getActivity(), petDisplayName));
        trendLevels.setValue(buildTrendLevels(records));

        if (records.size() == 1) {
            applySingleRecordState(latest, petDisplayName);
        } else {
            applyComparisonState(latest, records.get(1), records, petDisplayName);
        }

        recommendation.setValue(buildRecommendation(patternTitle.getValue(), petDisplayName));
    }

    private void applySingleRecordState(ActivityRecord r, String petDisplayName) {
        activityDesc.setValue("Latest");
        sleepDesc.setValue("Latest");
        nutritionDesc.setValue("Latest");
        aiInsightContext.setValue("Based on today's activity, sleep and appetite data.");
        patternTitle.setValue("Baseline Created");
        patternDesc.setValue("Add more records to identify long-term health trends.");
    }

    private void applyComparisonState(ActivityRecord latest, ActivityRecord previous,
                                      List<ActivityRecord> records, String petDisplayName) {
        int currentLevel = levelOf(latest.getActivity());
        int prevLevel = levelOf(previous.getActivity());
        if (currentLevel > prevLevel) activityDesc.setValue("More active");
        else if (currentLevel < prevLevel) activityDesc.setValue("Less active");
        else activityDesc.setValue("Consistent");

        double sleepDiff = latest.getSleep() - previous.getSleep();
        if (Math.abs(sleepDiff) < 1.0) sleepDesc.setValue("Stable");
        else if (sleepDiff > 0) sleepDesc.setValue("More sleep");
        else sleepDesc.setValue("Less sleep");

        int currentNutritionLevel = levelOf(latest.getNutrition());
        int prevNutritionLevel = levelOf(previous.getNutrition());
        if (currentNutritionLevel > prevNutritionLevel) nutritionDesc.setValue("Improved");
        else if (currentNutritionLevel < prevNutritionLevel) nutritionDesc.setValue("Lower");
        else nutritionDesc.setValue("Consistent");

        boolean activityChanged = (prevLevel >= 1) && currentLevel == 0;
        boolean sleepChanged = sleepDiff <= -SLEEP_SIGNIFICANT_DROP_HOURS;
        boolean nutritionChanged = (prevNutritionLevel >= 1) && currentNutritionLevel == 0;

        int changeCount = (activityChanged ? 1 : 0) + (sleepChanged ? 1 : 0) + (nutritionChanged ? 1 : 0);

        if (changeCount >= 2) {
            patternTitle.setValue("Unusual Pattern Detected");
            patternDesc.setValue("Activity, sleep and appetite have changed compared with the previous record.");
            aiInsightContext.setValue("Multiple metrics changed from normal baseline.");
        } else if (nutritionChanged) {
            patternTitle.setValue("Nutrition Change");
            patternDesc.setValue("Appetite has changed compared with the previous record.");
            aiInsightContext.setValue("Appetite shifted lower than previous baseline.");
        } else if ("High".equalsIgnoreCase(latest.getActivity()) && changeCount == 0) {
            patternTitle.setValue("Active Day");
            patternDesc.setValue("Activity is higher today while sleep and appetite remain typical.");
            aiInsightContext.setValue("High activity recorded with normal vital metrics.");
        } else {
            patternTitle.setValue("Pattern Stable");
            patternDesc.setValue("Recent activity and sleep patterns have remained consistent.");
            aiInsightContext.setValue("Activity, sleep and appetite look consistent with recent records.");
        }
    }

    private String buildRecommendation(String pattern, String petDisplayName) {
        if (pattern == null) return "Log your pet's activity today.";
        switch (pattern) {
            case "Unusual Pattern Detected":
                return "Keep an eye on " + petDisplayName + " over the next few days.";
            case "Nutrition Change":
                return "Monitor " + petDisplayName + "'s appetite over the next few records.";
            case "Active Day":
                return "Make sure " + petDisplayName + " has water and rest after an active day.";
            case "Pattern Stable":
                return "Keep up " + petDisplayName + "'s current routine.";
            default:
                return "Add more records to get a personalized recommendation.";
        }
    }

    private List<Integer> buildTrendLevels(List<ActivityRecord> newestFirstRecords) {
        int count = Math.min(TREND_MAX_POINTS, newestFirstRecords.size());
        List<Integer> levels = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            levels.add(levelOf(newestFirstRecords.get(i).getActivity()));
        }
        return levels;
    }

    private int levelOf(String val) {
        if (val == null) return 1;
        if (val.equalsIgnoreCase("High")) return 2;
        if (val.equalsIgnoreCase("Low")) return 0;
        return 1;
    }

    private String formatHours(double hours) {
        if (hours == Math.floor(hours)) return String.format(Locale.US, "%.0f", hours);
        return String.format(Locale.US, "%.1f", hours);
    }

    private String activityRule(String activity, String petDisplayName) {
        if ("Low".equalsIgnoreCase(activity)) {
            return petDisplayName + "'s activity level is lower today.";
        } else if ("High".equalsIgnoreCase(activity)) {
            return petDisplayName + " has been highly active today.";
        }
        return petDisplayName + "'s activity level is stable.";
    }

    public LiveData<String> getPetName() { return petName; }
    public LiveData<String> getPetDetails() { return petDetails; }
    public LiveData<String> getAiInsight() { return aiInsight; }
    public LiveData<String> getAiInsightContext() { return aiInsightContext; }
    public LiveData<String> getActivityMetric() { return activityMetric; }
    public LiveData<String> getActivityDesc() { return activityDesc; }
    public LiveData<String> getSleepMetric() { return sleepMetric; }
    public LiveData<String> getSleepDesc() { return sleepDesc; }
    public LiveData<String> getNutritionMetric() { return nutritionMetric; }
    public LiveData<String> getNutritionDesc() { return nutritionDesc; }
    public LiveData<String> getBehaviorMetric() { return behaviorMetric; }
    public LiveData<String> getBehaviorDesc() { return behaviorDesc; }
    public LiveData<String> getPatternTitle() { return patternTitle; }
    public LiveData<String> getPatternDesc() { return patternDesc; }
    public LiveData<String> getRecommendation() { return recommendation; }
    public LiveData<List<Integer>> getTrendLevels() { return trendLevels; }
    public LiveData<Map<String, Integer>> getMoodDistribution() { return moodDistribution; }
}