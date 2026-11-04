package com.example.pet.lab;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pet.data.LabTestItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service orchestrating AI-powered explanations, overall summary, and veterinary discussion questions.
 *
 * CRITICAL CLINICAL & SECURITY REQUIREMENTS:
 * 1. Zero API Keys in Android client: Authenticates with the secure backend proxy
 *    (Firebase Cloud Function) via the user's Firebase Auth ID token.
 * 2. Immutable Rule Classifications: The AI receives validated statuses as read-only.
 *    It cannot override or reclassify tests.
 * 3. Non-Diagnostic Output: All explanations and recommendations are informational
 *    and explicitly direct the user to consult their licensed veterinarian.
 * 4. Error Handling Rule: If AI is unavailable (offline or backend error),
 *    the app displays the rule-based results WITHOUT generating a fake AI summary.
 * 5. Species-Specific Guidance: Maintains separate clinical considerations for dogs and cats.
 */
public class LabAiService {

    private static final String TAG = "LabAiService";

    // Firebase Cloud Function endpoint
    private static final String BACKEND_FUNCTION_URL =
            "https://us-central1-petsense-personal.cloudfunctions.net/analyzeLabReport";

    public interface AiAnalysisCallback {
        void onSuccess(String overallSummary, String vetRecommendations, String vetQuestions, List<LabTestItem> itemsWithExplanations);
        void onAiUnavailable(String noticeMessage, List<LabTestItem> ruleBasedItemsOnly);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient;

    public LabAiService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Enriches verified lab test items with plain-language explanations and generates
     * an overall clinical summary, vet recommendations, and questions for the vet.
     */
    public void analyzeReportAsync(@NonNull String species, @NonNull String petName,
                                   @NonNull List<LabTestItem> verifiedItems,
                                   @NonNull AiAnalysisCallback callback) {
        executor.execute(() -> {
            FirebaseUser currentUser = null;
            try {
                currentUser = FirebaseAuth.getInstance().getCurrentUser();
            } catch (Exception ignored) {}

            if (currentUser != null) {
                // Request a fresh Firebase ID Token for backend authentication
                currentUser.getIdToken(false).addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String idToken = task.getResult().getToken();
                        callSecureCloudFunction(idToken, species, petName, verifiedItems, callback);
                    } else {
                        // AI Unavailable: Do NOT generate a fake AI summary.
                        postAiUnavailable("AI summary is currently unavailable (authentication error). Your rule-based laboratory results are displayed below.", verifiedItems, callback);
                    }
                });
            } else {
                // AI Unavailable: Do NOT generate a fake AI summary.
                postAiUnavailable("AI summary is currently unavailable (offline / no user session). Your rule-based laboratory results are displayed below.", verifiedItems, callback);
            }
        });
    }

    private void callSecureCloudFunction(String idToken, String species, String petName,
                                         List<LabTestItem> verifiedItems,
                                         AiAnalysisCallback callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("species", species);
            payload.put("petName", petName);

            JSONArray testsArray = new JSONArray();
            for (LabTestItem item : verifiedItems) {
                JSONObject tObj = new JSONObject();
                tObj.put("testName", item.getTestName());
                tObj.put("category", item.getCategory());
                tObj.put("value", item.getValue());
                tObj.put("unit", item.getUnit());
                tObj.put("refRange", item.getRefRangeText());
                tObj.put("status", item.getStatus()); // Read-only classification from RuleEngine
                testsArray.put(tObj);
            }
            payload.put("tests", testsArray);

            RequestBody body = RequestBody.create(
                    payload.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(BACKEND_FUNCTION_URL)
                    .addHeader("Authorization", "Bearer " + idToken)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    String summary = json.optString("aiSummary", "");
                    String recommendations = json.optString("vetRecommendations", "");
                    String questions = json.optString("vetQuestions", "");

                    JSONObject explanations = json.optJSONObject("testExplanations");
                    if (explanations != null) {
                        for (LabTestItem item : verifiedItems) {
                            if (explanations.has(item.getTestName())) {
                                item.setAiExplanation(explanations.getString(item.getTestName()));
                            }
                        }
                    }

                    mainHandler.post(() -> callback.onSuccess(summary, recommendations, questions, verifiedItems));
                    return;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Backend Cloud Function call failed: " + e.getMessage());
        }

        // If backend fails or is unreachable, do NOT generate a fake AI summary.
        // Deliver the verified rule-based results cleanly without fake diagnosis.
        postAiUnavailable("AI summary is currently unavailable (backend server unreachable). Your rule-based laboratory results are displayed below.", verifiedItems, callback);
    }

    private void postAiUnavailable(String notice, List<LabTestItem> items, AiAnalysisCallback callback) {
        mainHandler.post(() -> callback.onAiUnavailable(notice, items));
    }

    /**
     * Generates questions pet owners can discuss with their veterinarian, tailored specifically
     * to canine or feline physiology based on verified abnormal markers.
     */
    @NonNull
    public static String generateSpeciesSpecificVetQuestions(@NonNull String species, @NonNull List<LabTestItem> items) {
        StringBuilder sb = new StringBuilder();
        boolean isCat = "Cat".equalsIgnoreCase(species);

        List<String> abnormalTests = new ArrayList<>();
        for (LabTestItem item : items) {
            if (item != null && item.isAbnormal()) {
                abnormalTests.add(item.getTestName().toUpperCase(Locale.US));
            }
        }

        if (abnormalTests.isEmpty()) {
            sb.append("• Are there any breed-specific wellness screenings recommended for ").append(species).append("s at this age?\n");
            sb.append("• When should we schedule our next baseline routine blood panel?\n");
            sb.append("• Are current preventive treatments (parasites/vaccines) up to date?");
            return sb.toString();
        }

        sb.append("• What clinical follow-up is recommended for the abnormal results (").append(String.join(", ", abnormalTests)).append(")?\n");

        if (isCat) {
            // Feline specific discussion points
            if (abnormalTests.contains("GLU") || abnormalTests.contains("GLUCOSE")) {
                sb.append("• Could this elevated glucose be related to in-clinic stress (feline situational hyperglycemia), or should we test fructosamine?\n");
            }
            if (abnormalTests.contains("BUN") || abnormalTests.contains("CREA") || abnormalTests.contains("CREATININE")) {
                sb.append("• Would an SDMA check or urine specific gravity test help assess early feline renal health?\n");
            }
            if (abnormalTests.contains("T4") || abnormalTests.contains("TOTAL T4")) {
                sb.append("• Do these thyroid levels warrant monitoring for feline thyroid changes?\n");
            }
        } else {
            // Canine specific discussion points
            if (abnormalTests.contains("ALT") || abnormalTests.contains("ALKP") || abnormalTests.contains("ALP")) {
                sb.append("• Should we consider a liver support supplement or follow-up hepatic bile acid screening?\n");
            }
            if (abnormalTests.contains("BUN") || abnormalTests.contains("CREA") || abnormalTests.contains("CREATININE")) {
                sb.append("• Should we evaluate daily water consumption or perform a urinalysis?\n");
            }
            if (abnormalTests.contains("WBC") || abnormalTests.contains("HCT")) {
                sb.append("• Could these hematology variations reflect active inflammation, allergies, or tick-borne exposure?\n");
            }
        }

        sb.append("• When is the best time for a follow-up recheck panel to evaluate these values?");
        return sb.toString();
    }

    /**
     * Provides separate canine and feline educational explanations for individual parameters,
     * maintaining rule-based classification independence and avoiding definitive diagnoses.
     */
    @NonNull
    public static String getSpeciesSpecificExplanation(@NonNull String species, @NonNull LabTestItem item) {
        String name = item.getTestName().toUpperCase(Locale.US);
        String status = item.getStatus();
        boolean isCat = "Cat".equalsIgnoreCase(species);
        String petType = isCat ? "cats" : "dogs";

        if (LabTestItem.STATUS_UNCLASSIFIED.equalsIgnoreCase(status)) {
            return "No reference range was provided on the report for this parameter. Discuss with your veterinarian to interpret this result.";
        }
        if (LabTestItem.STATUS_INCOMPLETE.equalsIgnoreCase(status)) {
            return "This test could not be evaluated due to a specimen issue or pending laboratory status.";
        }

        boolean isHigh = LabTestItem.STATUS_HIGH.equalsIgnoreCase(status);
        boolean isLow = LabTestItem.STATUS_LOW.equalsIgnoreCase(status);

        if (name.contains("ALT")) {
            if (isHigh) return "Alanine aminotransferase (ALT) is an enzyme concentrated in liver cells. Mild-to-moderate elevation can reflect cellular stress, inflammation, or medication response in " + petType + ".";
            return "Normal ALT indicates healthy liver cell integrity.";
        }
        if (name.contains("ALKP") || name.contains("ALP")) {
            if (isHigh) {
                if (isCat) return "In cats, even slight elevations in ALKP (alkaline phosphatase) can be clinically significant due to the short half-life of the feline enzyme.";
                return "Elevated ALKP can relate to biliary flow, bone turnover, or endocrine factors in dogs.";
            }
            return "Normal ALKP suggests unobstructed biliary flow.";
        }
        if (name.contains("GLU")) {
            if (isHigh) {
                if (isCat) return "Cats often experience marked transient hyperglycemia due to veterinary clinic stress. A fructosamine test can distinguish stress response from persistent diabetes.";
                return "Elevated blood glucose warrants discussing dietary timing, activity level, and glycemic regulation with your vet.";
            }
            if (isLow) return "Low blood glucose requires prompt veterinary attention to ensure adequate metabolic energy balance.";
            return "Blood glucose is within standard limits.";
        }
        if (name.contains("CREA")) {
            if (isHigh) return "Creatinine is a muscle byproduct excreted exclusively by kidneys. Elevations in " + petType + " suggest discussing hydration and renal filtration with your veterinarian.";
            return "Normal creatinine indicates adequate baseline kidney filtration capacity.";
        }
        if (name.contains("BUN") || name.contains("UREA")) {
            if (isHigh) return "Blood Urea Nitrogen measures protein metabolic breakdown. Elevated levels can relate to dehydration, high-protein intake, or decreased kidney excretion.";
            if (isLow) return "Low BUN may relate to low dietary protein or liver metabolic variation.";
            return "BUN is within the laboratory's reference range.";
        }
        if (name.contains("HCT") || name.contains("PCV")) {
            if (isLow) return "Hematocrit measures red blood cell volume. Decreased values indicate mild anemia or overhydration in " + petType + ".";
            if (isHigh) return "Elevated hematocrit often reflects relative dehydration.";
            return "Red blood cell percentage is in the healthy zone.";
        }
        if (name.contains("WBC")) {
            if (isHigh) return "White Blood Cells protect against infection and stress. Elevation in " + petType + " can reflect immune activation, excitement, or localized inflammation.";
            if (isLow) return "Low WBC may suggest immune suppression or viral challenge.";
            return "White blood cell count is within the laboratory baseline.";
        }

        // Generic fallback
        if (isHigh) {
            return item.getTestName() + " is elevated above the report's reference range (" + item.getRefRangeText() + "). Discuss with your vet.";
        } else if (isLow) {
            return item.getTestName() + " is below the report's reference range (" + item.getRefRangeText() + "). Discuss with your vet.";
        }
        return item.getTestName() + " is within the laboratory's standard reference interval.";
    }

    public void shutdown() {
        executor.shutdown();
    }
}
