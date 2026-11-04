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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service orchestrating AI-powered explanations, overall clinical summary,
 * tailored precautions, and veterinary discussion questions.
 *
 * CRITICAL CLINICAL & SECURITY REQUIREMENTS:
 * 1. Zero API Keys in Android client: Uses secure Firebase Cloud Function proxy
 *    authenticated via Firebase Auth ID token.
 * 2. Immutable Rule Classifications: The AI receives validated statuses as read-only.
 * 3. Non-Diagnostic Output: All explanations are informational and direct users to their veterinarian.
 * 4. Resilient Fallback: If AI is unavailable or offline, executes a rule-based veterinary
 *    knowledge engine without producing synthetic diagnoses or crashing.
 * 5. Species-Specific Guidance: Maintains distinct clinical logic for dogs and cats.
 */
public class LabAiService {

    private static final String TAG = "LabAiService";

    // Firebase Cloud Function endpoint
    private static final String BACKEND_FUNCTION_URL =
            "https://us-central1-petsense-personal.cloudfunctions.net/analyzeLabReport";

    public interface AiAnalysisCallback {
        void onSuccess(
                String overallSummary,
                String vetRecommendations,
                String vetQuestions,
                List<String> precautions,
                String veterinaryAttention,
                String attentionDetails,
                List<LabTestItem> itemsWithExplanations
        );

        void onAiUnavailable(
                String noticeMessage,
                String overallSummary,
                String vetRecommendations,
                String vetQuestions,
                List<String> precautions,
                String veterinaryAttention,
                String attentionDetails,
                List<LabTestItem> ruleBasedItemsOnly
        );
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
     * Enriches verified lab test items with explanations, precautions, attention flags,
     * and discussion points tailored to the pet profile.
     */
    public void analyzeReportAsync(@NonNull String species, @NonNull String petName,
                                   @Nullable String breed, @Nullable String age,
                                   @Nullable String gender, @Nullable String weight,
                                   @NonNull List<LabTestItem> verifiedItems,
                                   @NonNull AiAnalysisCallback callback) {
        executor.execute(() -> {
            FirebaseUser currentUser = null;
            try {
                currentUser = FirebaseAuth.getInstance().getCurrentUser();
            } catch (Exception ignored) {}

            if (currentUser != null) {
                currentUser.getIdToken(false).addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String idToken = task.getResult().getToken();
                        callSecureCloudFunction(idToken, species, petName, breed, age, gender, weight, verifiedItems, callback);
                    } else {
                        executeClinicalFallback(species, petName, breed, age, verifiedItems,
                                "AI cloud service unavailable (session expired). Local clinical evaluation displayed below.", callback);
                    }
                });
            } else {
                executeClinicalFallback(species, petName, breed, age, verifiedItems,
                        "AI cloud service offline. Local clinical evaluation displayed below.", callback);
            }
        });
    }

    /** Overload for backwards compatibility. */
    public void analyzeReportAsync(@NonNull String species, @NonNull String petName,
                                   @NonNull List<LabTestItem> verifiedItems,
                                   @NonNull AiAnalysisCallback callback) {
        analyzeReportAsync(species, petName, null, null, null, null, verifiedItems, callback);
    }

    private void callSecureCloudFunction(String idToken, String species, String petName,
                                         String breed, String age, String gender, String weight,
                                         List<LabTestItem> verifiedItems,
                                         AiAnalysisCallback callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("species", species);
            payload.put("petName", petName);
            if (breed != null) payload.put("breed", breed);
            if (age != null) payload.put("age", age);
            if (gender != null) payload.put("gender", gender);
            if (weight != null) payload.put("weight", weight);

            JSONArray testsArray = new JSONArray();
            for (LabTestItem item : verifiedItems) {
                JSONObject tObj = new JSONObject();
                tObj.put("testName", item.getTestName());
                tObj.put("category", item.getCategory());
                tObj.put("value", item.getValue());
                tObj.put("unit", item.getUnit());
                tObj.put("refRange", item.getRefRangeText());
                tObj.put("status", item.getStatus());
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
                    String attention = json.optString("veterinaryAttention", "Routine Follow-up");
                    String attentionDetails = json.optString("veterinaryAttentionDetails", "");

                    List<String> precautions = new ArrayList<>();
                    JSONArray precArray = json.optJSONArray("precautions");
                    if (precArray != null) {
                        for (int i = 0; i < precArray.length(); i++) {
                            precautions.add(precArray.getString(i));
                        }
                    }

                    JSONObject explanations = json.optJSONObject("testExplanations");
                    JSONObject significances = json.optJSONObject("clinicalSignificance");
                    JSONObject followUps = json.optJSONObject("recommendedFollowUp");

                    for (LabTestItem item : verifiedItems) {
                        String name = item.getTestName();
                        if (explanations != null && explanations.has(name)) {
                            item.setAiExplanation(explanations.getString(name));
                        }
                        if (significances != null && significances.has(name)) {
                            item.setClinicalSignificance(significances.getString(name));
                        }
                        if (followUps != null && followUps.has(name)) {
                            item.setRecommendedFollowUp(followUps.getString(name));
                        }
                    }

                    mainHandler.post(() -> callback.onSuccess(
                            summary, recommendations, questions, precautions, attention, attentionDetails, verifiedItems
                    ));
                    return;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Backend Cloud Function call failed: " + e.getMessage());
        }

        executeClinicalFallback(species, petName, breed, age, verifiedItems,
                "AI cloud service unreachable. Deterministic clinical evaluation displayed below.", callback);
    }

    /**
     * High-grade deterministic veterinary fallback engine.
     * Generates plain-language medical explanations, tailored precautions, and veterinary attention advice
     * based purely on verified values and pet demographics without ever fabricating data or diagnosis.
     */
    private void executeClinicalFallback(String species, String petName, String breed, String age,
                                        List<LabTestItem> verifiedItems, String notice,
                                        AiAnalysisCallback callback) {
        boolean isCat = "Cat".equalsIgnoreCase(species);
        String petNoun = isCat ? "cat" : "dog";

        List<LabTestItem> abnormalItems = new ArrayList<>();
        List<LabTestItem> normalItems = new ArrayList<>();

        for (LabTestItem item : verifiedItems) {
            if (item.isAbnormal()) {
                abnormalItems.add(item);
            } else if (LabTestItem.STATUS_NORMAL.equalsIgnoreCase(item.getStatus())) {
                normalItems.add(item);
            }
            // Populate individual parameter clinical explanations
            populateItemClinicalContext(species, item);
        }

        // Assess Veterinary Attention Level & Urgency
        boolean hasCritical = false;
        StringBuilder criticalReasons = new StringBuilder();

        for (LabTestItem item : abnormalItems) {
            Double val = item.getNumericValue();
            String name = item.getTestName().toUpperCase(Locale.US);

            // Severe hypoglycemia
            if (name.contains("GLU") && val != null && val < 50.0) {
                hasCritical = true;
                criticalReasons.append("Severely low blood glucose (").append(val).append(" ").append(item.getUnit()).append(") requires immediate veterinary evaluation. ");
            }
            // Severe azotemia
            if (name.contains("CREA") && val != null && val > 5.0) {
                hasCritical = true;
                criticalReasons.append("Marked creatinine elevation (").append(val).append(" ").append(item.getUnit()).append(") indicates acute renal strain. ");
            }
            // Severe anemia
            if ((name.contains("HCT") || name.contains("PCV")) && val != null && val < 20.0) {
                hasCritical = true;
                criticalReasons.append("Severe hematocrit drop (").append(val).append("%) reflects significant red blood cell loss. ");
            }
            // Severe thrombocytopenia
            if (name.contains("PLT") && val != null && val < 50.0) {
                hasCritical = true;
                criticalReasons.append("Markedly low platelet count (<50 K/uL) presents bleeding risk. ");
            }
        }

        final String attentionLevel;
        final String attentionDetails;
        if (hasCritical) {
            attentionLevel = "Urgent Veterinary Attention Required";
            attentionDetails = criticalReasons.toString().trim();
        } else if (!abnormalItems.isEmpty()) {
            attentionLevel = "Prompt Veterinary Consultation Recommended";
            attentionDetails = abnormalItems.size() + " parameter(s) deviate from the laboratory's printed reference ranges. Schedule a clinical review with your veterinarian.";
        } else {
            attentionLevel = "Routine Wellness Review";
            attentionDetails = "All tested parameters fall within reported reference ranges. Note: Normal results confirm baseline stability for this specific panel, but do not replace an in-person veterinary physical examination.";
        }

        // Generate Overall Summary
        String overallSummary = generateClinicalSummary(species, petName, abnormalItems, normalItems);

        // Generate Precautions
        List<String> precautions = generateTailoredPrecautions(species, abnormalItems);

        // Generate Vet Consultation Recommendations & Questions
        String vetRecommendations = generateVetRecommendations(species, abnormalItems);
        String vetQuestions = generateSpeciesSpecificVetQuestions(species, verifiedItems);

        mainHandler.post(() -> callback.onAiUnavailable(
                notice, overallSummary, vetRecommendations, vetQuestions,
                precautions, attentionLevel, attentionDetails, verifiedItems
        ));
    }

    private static String generateClinicalSummary(String species, String petName,
                                                  List<LabTestItem> abnormal, List<LabTestItem> normal) {
        StringBuilder sb = new StringBuilder();
        if (abnormal.isEmpty()) {
            sb.append("All ").append(normal.size()).append(" analyzed laboratory parameters for ")
              .append(petName).append(" are within the laboratory's reported reference ranges. ")
              .append("Baseline organ and hematologic functions show healthy physiological limits on this panel.");
        } else {
            sb.append(petName).append("'s report reveals ").append(abnormal.size())
              .append(" parameter(s) outside printed reference intervals: ");
            for (int i = 0; i < abnormal.size(); i++) {
                LabTestItem item = abnormal.get(i);
                if (i > 0) sb.append(", ");
                sb.append(item.getTestName()).append(" (").append(item.getStatus()).append(")");
            }
            sb.append(". The remaining ").append(normal.size()).append(" parameters are within expected limits.");

            // Highlight known clinical patterns
            String pattern = detectClinicalPattern(species, abnormal);
            if (pattern != null) {
                sb.append("\n\nPattern Consideration: ").append(pattern);
            }
        }
        return sb.toString();
    }

    @Nullable
    private static String detectClinicalPattern(String species, List<LabTestItem> abnormal) {
        Map<String, String> statuses = new HashMap<>();
        for (LabTestItem item : abnormal) {
            statuses.put(item.getTestName().toUpperCase(Locale.US), item.getStatus());
        }

        // Renal pattern: elevated BUN and Creatinine
        boolean highBun = isHigh(statuses, "BUN") || isHigh(statuses, "UREA");
        boolean highCrea = isHigh(statuses, "CREA") || isHigh(statuses, "CREATININE");
        if (highBun && highCrea) {
            return "Concomitant elevation in BUN and Creatinine (azotemia) suggests reduced kidney filtration or prerenal dehydration. Hydration assessment and urinalysis (USG) are strongly recommended.";
        }

        // Hepatic pattern: elevated ALT and ALKP/ALP
        boolean highAlt = isHigh(statuses, "ALT") || isHigh(statuses, "SGPT");
        boolean highAlp = isHigh(statuses, "ALKP") || isHigh(statuses, "ALP");
        if (highAlt && highAlp) {
            return "Dual elevation in hepatocellular (ALT) and biliary/cholestatic (ALKP) enzymes suggests combined liver cellular stress. Biliary ultrasound or liver support review is indicated.";
        }

        // Anemia pattern: low RBC, HCT, or HGB
        boolean lowHct = isLow(statuses, "HCT") || isLow(statuses, "PCV");
        boolean lowRbc = isLow(statuses, "RBC");
        if (lowHct || lowRbc) {
            return "Decreased red blood cell mass indicates mild-to-moderate anemia. A reticulocyte count will help your vet determine if bone marrow regeneration is active.";
        }

        // Feline stress vs persistent hyperglycemia
        if ("Cat".equalsIgnoreCase(species) && isHigh(statuses, "GLU") || isHigh(statuses, "GLUCOSE")) {
            return "Cats frequently experience transient in-clinic situational hyperglycemia due to adrenaline surge. A fructosamine test can distinguish clinic stress from true glycemic regulation issues.";
        }

        return null;
    }

    private static boolean isHigh(Map<String, String> map, String key) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().contains(key) && LabTestItem.STATUS_HIGH.equalsIgnoreCase(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLow(Map<String, String> map, String key) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().contains(key) && LabTestItem.STATUS_LOW.equalsIgnoreCase(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    public static String assessVeterinaryAttention(@NonNull List<LabTestItem> abnormalItems) {
        for (LabTestItem item : abnormalItems) {
            Double val = item.getNumericValue();
            String name = item.getTestName().toUpperCase(Locale.US);

            if (name.contains("GLU") && val != null && val < 50.0) {
                return "Urgent Veterinary Attention Required";
            }
            if (name.contains("CREA") && val != null && val > 5.0) {
                return "Urgent Veterinary Attention Required";
            }
            if ((name.contains("HCT") || name.contains("PCV")) && val != null && val < 20.0) {
                return "Urgent Veterinary Attention Required";
            }
            if (name.contains("PLT") && val != null && val < 50.0) {
                return "Urgent Veterinary Attention Required";
            }
        }
        if (!abnormalItems.isEmpty()) {
            return "Prompt Veterinary Consultation Recommended";
        }
        return "Routine Wellness Review";
    }

    public static List<String> generateTailoredPrecautions(@NonNull String species, @NonNull List<LabTestItem> abnormal) {
        List<String> precautions = new ArrayList<>();
        if (abnormal.isEmpty()) {
            precautions.add("Maintain regular feeding schedules, fresh water access, and routine exercise.");
            precautions.add("Continue routine annual or semi-annual veterinary wellness exams.");
            precautions.add("Observe daily activity, litter box/elimination habits, and appetite as baseline indicators.");
            return precautions;
        }

        boolean kidneyInvolved = false;
        boolean liverInvolved = false;
        boolean anemiaInvolved = false;
        boolean glycemicInvolved = false;
        boolean immuneInvolved = false;

        for (LabTestItem item : abnormal) {
            String name = item.getTestName().toUpperCase(Locale.US);
            if (name.contains("BUN") || name.contains("CREA") || name.contains("SDMA") || name.contains("PHOS")) {
                kidneyInvolved = true;
            }
            if (name.contains("ALT") || name.contains("AST") || name.contains("ALKP") || name.contains("ALP") || name.contains("BILIRUBIN")) {
                liverInvolved = true;
            }
            if (name.contains("HCT") || name.contains("PCV") || name.contains("RBC") || name.contains("HGB") || name.contains("PLT")) {
                anemiaInvolved = true;
            }
            if (name.contains("GLU") || name.contains("FRUCTOSAMINE")) {
                glycemicInvolved = true;
            }
            if (name.contains("WBC") || name.contains("NEUTROPHIL") || name.contains("LYMPHOCYTE")) {
                immuneInvolved = true;
            }
        }

        if (kidneyInvolved) {
            precautions.add("Ensure 24/7 access to clean, fresh water to support renal perfusion and prevent dehydration.");
            precautions.add("Monitor daily water consumption and urination volume/frequency; note any sudden increases.");
            precautions.add("Avoid giving human foods, high-sodium treats, or unprescribed pain medications (NSAIDs).");
        }

        if (liverInvolved) {
            precautions.add("Monitor appetite closely; report any bouts of nausea, lip-smacking, vomiting, or yellowing (icterus) of gums or eyes.");
            precautions.add("Avoid introducing new supplements, high-fat table scraps, or over-the-counter medications without veterinary approval.");
        }

        if (anemiaInvolved) {
            precautions.add("Observe gum color (should be healthy bubblegum pink, not pale or white); report sudden lethargy or exercise intolerance.");
            precautions.add("Inspect stools and urine for signs of dark, tarry blood or red discoloration.");
        }

        if (glycemicInvolved) {
            precautions.add("Maintain strict meal timing and avoid sugary treats; observe for excessive thirst (polydipsia) or increased urination.");
        }

        if (immuneInvolved) {
            precautions.add("Check for localized swelling, coughing, nasal discharge, or warmth indicating localized inflammation or infection.");
        }

        precautions.add("Consult your veterinarian promptly for a comprehensive physical evaluation and interpretation.");
        precautions.add("Do not initiate, adjust, or discontinue medications, supplements, or diets without direct veterinary guidance.");

        return precautions;
    }

    private static String generateVetRecommendations(String species, List<LabTestItem> abnormal) {
        if (abnormal.isEmpty()) {
            return "• All tested markers are within baseline reference intervals.\n• Schedule your next routine wellness checkup according to your veterinarian's recommended interval for " + species.toLowerCase() + "s.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("• Review ").append(abnormal.size()).append(" abnormal finding(s) with your veterinarian.\n");
        sb.append("• Inquire whether follow-up testing (e.g. repeat blood panel in 2-4 weeks, urinalysis, or imaging) is warranted.\n");
        sb.append("• Provide the clinic with a complete list of current foods, treats, and any medications.");
        return sb.toString();
    }

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
            if (abnormalTests.contains("GLU") || abnormalTests.contains("GLUCOSE")) {
                sb.append("• Could this elevated glucose be related to in-clinic stress (feline situational hyperglycemia), or should we test fructosamine?\n");
            }
            if (abnormalTests.contains("BUN") || abnormalTests.contains("CREA") || abnormalTests.contains("CREATININE") || abnormalTests.contains("SDMA")) {
                sb.append("• Would an SDMA check or urine specific gravity test help assess early feline renal health?\n");
            }
            if (abnormalTests.contains("T4") || abnormalTests.contains("TOTAL T4")) {
                sb.append("• Do these thyroid levels warrant monitoring for feline hyperthyroidism?\n");
            }
        } else {
            if (abnormalTests.contains("ALT") || abnormalTests.contains("ALKP") || abnormalTests.contains("ALP")) {
                sb.append("• Should we consider hepatic support or a follow-up bile acid screening?\n");
            }
            if (abnormalTests.contains("BUN") || abnormalTests.contains("CREA") || abnormalTests.contains("CREATININE")) {
                sb.append("• Should we evaluate daily water consumption or perform a urinalysis?\n");
            }
            if (abnormalTests.contains("WBC") || abnormalTests.contains("HCT") || abnormalTests.contains("PLT")) {
                sb.append("• Could these hematology variations reflect active inflammation, allergies, or tick-borne exposure?\n");
            }
        }

        sb.append("• When is the best time for a follow-up recheck panel to evaluate these values?");
        return sb.toString();
    }

    public static String getSpeciesSpecificExplanation(@NonNull String species, @NonNull LabTestItem item) {
        populateItemClinicalContext(species, item);
        return item.getAiExplanation();
    }

    private static void populateItemClinicalContext(String species, LabTestItem item) {
        String name = item.getTestName().toUpperCase(Locale.US);
        String status = item.getStatus();
        boolean isCat = "Cat".equalsIgnoreCase(species);
        String petNoun = isCat ? "cats" : "dogs";

        if (LabTestItem.STATUS_UNCLASSIFIED.equalsIgnoreCase(status)) {
            item.setAiExplanation("No printed reference range was detected on the report for this test. Consult your veterinarian for clinical interpretation.");
            item.setClinicalSignificance("Interpretation requires the laboratory's specific instrument baseline.");
            item.setRecommendedFollowUp("Request reference range from the testing laboratory or clinic.");
            return;
        }

        if (LabTestItem.STATUS_INCOMPLETE.equalsIgnoreCase(status)) {
            item.setAiExplanation("This test could not be evaluated due to a specimen issue or pending laboratory status.");
            item.setClinicalSignificance("Inconclusive result.");
            item.setRecommendedFollowUp("Discuss with vet whether a redrawn sample is required.");
            return;
        }

        boolean isHigh = LabTestItem.STATUS_HIGH.equalsIgnoreCase(status);
        boolean isLow = LabTestItem.STATUS_LOW.equalsIgnoreCase(status);

        if (name.contains("ALT") || name.contains("SGPT")) {
            if (isHigh) {
                item.setAiExplanation("Alanine aminotransferase (ALT) is an enzyme concentrated inside liver cells. Leakage into the bloodstream indicates hepatic cellular stress, inflammation, or medication exposure in " + petNoun + "s.");
                item.setClinicalSignificance("Possible hepatocellular stress, toxic response, or secondary hepatic inflammation.");
                item.setRecommendedFollowUp("Re-evaluate liver enzymes in 2-4 weeks; assess current medications and supplements.");
            } else {
                item.setAiExplanation("Normal ALT indicates intact liver cell membrane integrity.");
            }
        } else if (name.contains("ALP") || name.contains("ALKP")) {
            if (isHigh) {
                if (isCat) {
                    item.setAiExplanation("In cats, even slight elevations in ALKP are clinically notable because feline ALKP has a very short half-life (6 hours).");
                    item.setClinicalSignificance("Possible feline hepatic lipidosis, cholangiohepatitis, or hyperthyroidism.");
                    item.setRecommendedFollowUp("Prompt veterinary evaluation for biliary or thyroid markers.");
                } else {
                    item.setAiExplanation("Alkaline phosphatase (ALKP) reflects biliary flow, bone remodeling, or endocrine/steroid response in dogs.");
                    item.setClinicalSignificance("Potential biliary sluggishness, steroid exposure, or Cushingoid changes.");
                    item.setRecommendedFollowUp("Discuss biliary function and endocrine screening if other enzymes are elevated.");
                }
            } else {
                item.setAiExplanation("Normal ALKP suggests uncompromised biliary drainage.");
            }
        } else if (name.contains("CREA")) {
            if (isHigh) {
                item.setAiExplanation("Creatinine is a muscle metabolic byproduct filtered exclusively by kidneys. Elevations indicate reduced glomerular filtration rate in " + petNoun + "s.");
                item.setClinicalSignificance("Potential renal filtration compromise or acute prerenal dehydration.");
                item.setRecommendedFollowUp("Urinalysis (USG) and hydration evaluation; review renal blood pressure.");
            } else {
                item.setAiExplanation("Normal creatinine indicates adequate baseline kidney excretion capacity.");
            }
        } else if (name.contains("BUN") || name.contains("UREA")) {
            if (isHigh) {
                item.setAiExplanation("Blood Urea Nitrogen measures protein breakdown excretion. Elevated levels can reflect dehydration, high protein intake, or reduced renal excretion.");
                item.setClinicalSignificance("Prerenal hemoconcentration or renal functional decline.");
                item.setRecommendedFollowUp("Assess hydration status; compare with concurrent creatinine and urine concentration.");
            } else if (isLow) {
                item.setAiExplanation("Low BUN can relate to low dietary protein intake, excessive hydration, or reduced liver urea cycle production.");
                item.setClinicalSignificance("Low protein intake or hepatic functional variation.");
                item.setRecommendedFollowUp("Review dietary composition with veterinarian.");
            } else {
                item.setAiExplanation("BUN is within normal physiologic limits.");
            }
        } else if (name.contains("GLU")) {
            if (isHigh) {
                if (isCat) {
                    item.setAiExplanation("Cats frequently experience marked temporary hyperglycemia due to clinic visit excitement and adrenaline release.");
                    item.setClinicalSignificance("Situational stress hyperglycemia vs persistent diabetes mellitus.");
                    item.setRecommendedFollowUp("Check fructosamine level to measure 2-week average blood glucose independently of stress.");
                } else {
                    item.setAiExplanation("Elevated blood glucose warrants reviewing meal timing, activity, and metabolic glycemic regulation.");
                    item.setClinicalSignificance("Possible glycemic regulation impairment or postprandial spike.");
                    item.setRecommendedFollowUp("Perform fasting glucose recheck and urine glucose assessment.");
                }
            } else if (isLow) {
                item.setAiExplanation("Low blood glucose requires prompt attention to ensure adequate energy substrate for brain and body tissues.");
                item.setClinicalSignificance("Hypoglycemia: potential insulinoma, sepsis, or pediatric depletion.");
                item.setRecommendedFollowUp("Immediate veterinary review and blood sugar stabilization.");
            } else {
                item.setAiExplanation("Blood glucose is balanced.");
            }
        } else if (name.contains("HCT") || name.contains("PCV")) {
            if (isLow) {
                item.setAiExplanation("Hematocrit measures the percentage of blood volume composed of red blood cells. Low values indicate anemia in " + petNoun + "s.");
                item.setClinicalSignificance("Anemia: potential blood loss, red cell destruction, or reduced production.");
                item.setRecommendedFollowUp("Complete reticulocyte count and check stool for occult blood.");
            } else if (isHigh) {
                item.setAiExplanation("Elevated hematocrit often indicates relative hemoconcentration from reduced fluid intake.");
                item.setClinicalSignificance("Dehydration or primary polycythemia.");
                item.setRecommendedFollowUp("Check hydration status and water intake.");
            } else {
                item.setAiExplanation("Red blood cell volume is healthy.");
            }
        } else if (name.contains("WBC")) {
            if (isHigh) {
                item.setAiExplanation("White Blood Cells defend against pathogens and tissue injury. Elevation in " + petNoun + "s indicates immune response, stress, or inflammation.");
                item.setClinicalSignificance("Active inflammation, immune reaction, or infectious challenge.");
                item.setRecommendedFollowUp("Examine differential (neutrophils vs lymphocytes) and physical exam.");
            } else if (isLow) {
                item.setAiExplanation("Low WBC can reflect bone marrow challenge, severe acute consumption, or viral infection.");
                item.setClinicalSignificance("Leukopenia: compromised immune defense.");
                item.setRecommendedFollowUp("Prompt veterinary evaluation for infection or bone marrow status.");
            } else {
                item.setAiExplanation("White blood cell count is within laboratory baseline.");
            }
        } else if (name.contains("PLT") || name.contains("PLATELET")) {
            if (isLow) {
                item.setAiExplanation("Platelets are responsible for clotting. Decreased counts can increase bleeding risk or reflect platelet clumping in the tube.");
                item.setClinicalSignificance("Thrombocytopenia or in-vitro sample clumping.");
                item.setRecommendedFollowUp("Verify with blood smear slide review to rule out clumping artifact.");
            } else {
                item.setAiExplanation("Platelet count is adequate.");
            }
        } else {
            // General parameter fallback
            if (isHigh) {
                item.setAiExplanation(item.getTestName() + " is elevated above the report's reference range (" + item.getRefRangeText() + ").");
                item.setClinicalSignificance("Result is higher than baseline.");
                item.setRecommendedFollowUp("Discuss significance with your veterinarian.");
            } else if (isLow) {
                item.setAiExplanation(item.getTestName() + " is below the report's reference range (" + item.getRefRangeText() + ").");
                item.setClinicalSignificance("Result is lower than baseline.");
                item.setRecommendedFollowUp("Discuss significance with your veterinarian.");
            } else {
                item.setAiExplanation(item.getTestName() + " is within the laboratory's reported reference interval.");
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
