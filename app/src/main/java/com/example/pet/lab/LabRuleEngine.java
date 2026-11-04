package com.example.pet.lab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pet.data.LabTestItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic rule-based parsing, metadata extraction, and classification engine
 * for veterinary laboratory reports (Canine and Feline).
 *
 * CRITICAL CLINICAL RULES:
 * 1. Strictly prioritizes reference ranges extracted directly from the uploaded report.
 * 2. Missing reference range MUST remain UNCLASSIFIED (never guessing or substituting ranges).
 * 3. Does NOT interpret random text, addresses, postal codes, or phone numbers as lab tests.
 * 4. Sample quality observations (Hemolysis, Lipemia, Icterus) are separated as sample-quality warnings,
 *    never as ordinary disease indicators.
 * 5. Flags uncertain extractions for user verification.
 * 6. Deterministic and independent of AI.
 */
public class LabRuleEngine {

    /**
     * Container holding extracted report-level metadata.
     */
    public static class ReportMetadata {
        public String labName;
        public String reportDateStr;
        public String petName;
        public String species;
        public String breed;
        public String age;
        public String gender;
        public String weight;
        public List<String> sampleQualityWarnings = new ArrayList<>();
    }

    // Common qualitative indicators
    private static final Set<String> NORMAL_QUALITATIVE = new HashSet<>();
    private static final Set<String> ABNORMAL_QUALITATIVE = new HashSet<>();
    private static final Set<String> INCOMPLETE_INDICATORS = new HashSet<>();
    private static final Set<String> SAMPLE_QUALITY_TERMS = new HashSet<>();

    static {
        // Normal qualitative terms
        NORMAL_QUALITATIVE.add("negative");
        NORMAL_QUALITATIVE.add("neg");
        NORMAL_QUALITATIVE.add("normal");
        NORMAL_QUALITATIVE.add("clear");
        NORMAL_QUALITATIVE.add("none");
        NORMAL_QUALITATIVE.add("none seen");
        NORMAL_QUALITATIVE.add("non-reactive");
        NORMAL_QUALITATIVE.add("nonreactive");
        NORMAL_QUALITATIVE.add("nil");

        // Abnormal qualitative terms
        ABNORMAL_QUALITATIVE.add("positive");
        ABNORMAL_QUALITATIVE.add("pos");
        ABNORMAL_QUALITATIVE.add("reactive");
        ABNORMAL_QUALITATIVE.add("elevated");
        ABNORMAL_QUALITATIVE.add("trace");
        ABNORMAL_QUALITATIVE.add("1+");
        ABNORMAL_QUALITATIVE.add("2+");
        ABNORMAL_QUALITATIVE.add("3+");
        ABNORMAL_QUALITATIVE.add("4+");
        ABNORMAL_QUALITATIVE.add("moderate");
        ABNORMAL_QUALITATIVE.add("marked");

        // Incomplete indicators
        INCOMPLETE_INDICATORS.add("qns");
        INCOMPLETE_INDICATORS.add("pending");
        INCOMPLETE_INDICATORS.add("cancelled");
        INCOMPLETE_INDICATORS.add("canceled");
        INCOMPLETE_INDICATORS.add("error");
        INCOMPLETE_INDICATORS.add("not tested");
        INCOMPLETE_INDICATORS.add("hemolyzed");
        INCOMPLETE_INDICATORS.add("hemolysed");
        INCOMPLETE_INDICATORS.add("clotted");
        INCOMPLETE_INDICATORS.add("lipemic");
        INCOMPLETE_INDICATORS.add("icteric");

        // Sample quality terms
        SAMPLE_QUALITY_TERMS.add("hemolysis");
        SAMPLE_QUALITY_TERMS.add("haemolysis");
        SAMPLE_QUALITY_TERMS.add("lipemia");
        SAMPLE_QUALITY_TERMS.add("lipaemia");
        SAMPLE_QUALITY_TERMS.add("icterus");
        SAMPLE_QUALITY_TERMS.add("jaundice");
        SAMPLE_QUALITY_TERMS.add("clotted");
        SAMPLE_QUALITY_TERMS.add("platelet clumping");
    }

    // Known veterinary test parameters mapping standard names & abbreviations
    private static final Map<String, String> KNOWN_VET_PARAMETERS = new LinkedHashMap<>();

    static {
        // Liver Panel
        registerParam("ALT", "ALT (Alanine Aminotransferase)");
        registerParam("SGPT", "ALT (SGPT)");
        registerParam("AST", "AST (Aspartate Aminotransferase)");
        registerParam("SGOT", "AST (SGOT)");
        registerParam("ALP", "ALKP (Alkaline Phosphatase)");
        registerParam("ALKP", "ALKP (Alkaline Phosphatase)");
        registerParam("GGT", "GGT (Gamma-Glutamyl Transferase)");
        registerParam("TOTAL BILIRUBIN", "Total Bilirubin");
        registerParam("TBIL", "Total Bilirubin");
        registerParam("DIRECT BILIRUBIN", "Direct Bilirubin");
        registerParam("DBIL", "Direct Bilirubin");
        registerParam("BILE ACIDS", "Bile Acids");

        // Kidney Panel
        registerParam("BUN", "BUN (Blood Urea Nitrogen)");
        registerParam("UREA", "Urea");
        registerParam("CREATININE", "Creatinine");
        registerParam("CREA", "Creatinine");
        registerParam("CRE", "Creatinine");
        registerParam("SDMA", "SDMA (Symmetric Dimethylarginine)");
        registerParam("PHOSPHORUS", "Phosphorus");
        registerParam("PHOS", "Phosphorus");
        registerParam("BUN/CREATININE RATIO", "BUN/Creatinine Ratio");
        registerParam("B/C RATIO", "BUN/Creatinine Ratio");

        // Hematology (CBC)
        registerParam("WBC", "WBC (White Blood Cells)");
        registerParam("WHITE BLOOD CELL", "WBC (White Blood Cells)");
        registerParam("LEUKOCYTES", "WBC (Leukocytes)");
        registerParam("RBC", "RBC (Red Blood Cells)");
        registerParam("RED BLOOD CELL", "RBC (Red Blood Cells)");
        registerParam("ERYTHROCYTES", "RBC (Erythrocytes)");
        registerParam("HGB", "Hemoglobin (HGB)");
        registerParam("HEMOGLOBIN", "Hemoglobin (HGB)");
        registerParam("HB", "Hemoglobin (Hb)");
        registerParam("HCT", "Hematocrit (HCT)");
        registerParam("HEMATOCRIT", "Hematocrit (HCT)");
        registerParam("PCV", "Packed Cell Volume (PCV)");
        registerParam("MCV", "MCV");
        registerParam("MCH", "MCH");
        registerParam("MCHC", "MCHC");
        registerParam("RDW", "RDW");
        registerParam("PLATELETS", "Platelets (PLT)");
        registerParam("PLATELET", "Platelets (PLT)");
        registerParam("PLT", "Platelets (PLT)");
        registerParam("MPV", "MPV (Mean Platelet Volume)");
        registerParam("NEUTROPHILS", "Neutrophils");
        registerParam("NEUTS", "Neutrophils");
        registerParam("SEGS", "Segmented Neutrophils");
        registerParam("BANDS", "Band Neutrophils");
        registerParam("LYMPHOCYTES", "Lymphocytes");
        registerParam("LYMPHS", "Lymphocytes");
        registerParam("MONOCYTES", "Monocytes");
        registerParam("MONOS", "Monocytes");
        registerParam("EOSINOPHILS", "Eosinophils");
        registerParam("EOS", "Eosinophils");
        registerParam("BASOPHILS", "Basophils");
        registerParam("BASOS", "Basophils");
        registerParam("RETICULOCYTES", "Reticulocytes");
        registerParam("RETICS", "Reticulocytes");

        // Electrolytes
        registerParam("SODIUM", "Sodium (Na)");
        registerParam("NA", "Sodium (Na)");
        registerParam("POTASSIUM", "Potassium (K)");
        registerParam("K", "Potassium (K)");
        registerParam("NA/K RATIO", "Na/K Ratio");
        registerParam("CHLORIDE", "Chloride (Cl)");
        registerParam("CL", "Chloride (Cl)");
        registerParam("CALCIUM", "Calcium (Ca)");
        registerParam("CA", "Calcium (Ca)");
        registerParam("BICARBONATE", "Bicarbonate (HCO3)");
        registerParam("TCO2", "Total CO2");

        // Endocrine & Metabolic
        registerParam("GLUCOSE", "Glucose");
        registerParam("GLU", "Glucose");
        registerParam("FRUCTOSAMINE", "Fructosamine");
        registerParam("TOTAL T4", "Total T4 (Thyroxine)");
        registerParam("T4", "Total T4 (Thyroxine)");
        registerParam("FREE T4", "Free T4");
        registerParam("TSH", "TSH");
        registerParam("CORTISOL", "Cortisol");

        // Proteins
        registerParam("TOTAL PROTEIN", "Total Protein (TP)");
        registerParam("TP", "Total Protein (TP)");
        registerParam("ALBUMIN", "Albumin");
        registerParam("ALB", "Albumin");
        registerParam("GLOBULIN", "Globulin");
        registerParam("GLOB", "Globulin");
        registerParam("A/G RATIO", "A/G Ratio");
        registerParam("ALB/GLOB", "A/G Ratio");

        // Pancreatic & Lipids
        registerParam("AMYLASE", "Amylase");
        registerParam("LIPASE", "Lipase");
        registerParam("SPEC CPL", "Spec cPL (Canine Pancreatic Lipase)");
        registerParam("SPEC FPL", "Spec fPL (Feline Pancreatic Lipase)");
        registerParam("CHOLESTEROL", "Cholesterol (CHOL)");
        registerParam("CHOL", "Cholesterol (CHOL)");
        registerParam("TRIGLYCERIDES", "Triglycerides");
        registerParam("TRIG", "Triglycerides");
        registerParam("CPK", "CPK (Creatine Kinase)");
        registerParam("CK", "CK (Creatine Kinase)");

        // Urinalysis
        registerParam("USG", "Urine Specific Gravity (USG)");
        registerParam("URINE SPECIFIC GRAVITY", "Urine Specific Gravity (USG)");
        registerParam("URINE PROTEIN", "Urine Protein");
        registerParam("URINE GLUCOSE", "Urine Glucose");
        registerParam("URINE KETONES", "Urine Ketones");
        registerParam("URINE PH", "Urine pH");
    }

    private static void registerParam(String key, String standardName) {
        KNOWN_VET_PARAMETERS.put(key.toUpperCase(Locale.US), standardName);
    }

    // Common veterinary units
    private static final Set<String> COMMON_VET_UNITS = new HashSet<>(Arrays.asList(
            "u/l", "iu/l", "mg/dl", "g/dl", "mmol/l", "umol/l", "µmol/l",
            "k/ul", "k/µl", "m/ul", "m/µl", "/ul", "/µl", "%",
            "x10^9/l", "10^9/l", "x10^12/l", "10^12/l", "x10^3/ul", "10^3/ul",
            "fl", "pg", "meq/l", "ug/dl", "µg/dl", "ng/ml", "ng/dl", "pmol/l",
            "ratio", "sec", "seconds", "cells/hpf"
    ));

    // Regex for reference ranges
    // Matches: "10 - 100", "0.5 - 1.5", "-4 to +4", "10 .. 100", "10,0 - 100,0", "(10 - 100)"
    private static final Pattern RANGE_INTERVAL_PATTERN = Pattern.compile(
            "[\\[\\(]?\\s*([+-]?[\\d]+(?:[.,][\\d]+)?)\\s*(?:-|–|—|to|\\.\\.)\\s*([+-]?[\\d]+(?:[.,][\\d]+)?)\\s*[\\]\\)]?",
            Pattern.CASE_INSENSITIVE
    );

    // Matches: "< 5.0", "<= 5.0", "<5.0", "less than 5.0"
    private static final Pattern RANGE_LESS_THAN_PATTERN = Pattern.compile(
            "(?:<|<=|less than|under)\\s*([+-]?[\\d]+(?:[.,][\\d]+)?)",
            Pattern.CASE_INSENSITIVE
    );

    // Matches: "> 20", ">= 20", ">20", "greater than 20"
    private static final Pattern RANGE_GREATER_THAN_PATTERN = Pattern.compile(
            "(?:>|>=|greater than|over)\\s*([+-]?[\\d]+(?:[.,][\\d]+)?)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts report-level metadata (Lab name, Date, Pet details, Sample Quality warnings).
     */
    @NonNull
    public static ReportMetadata extractReportMetadata(@Nullable String rawText) {
        ReportMetadata meta = new ReportMetadata();
        if (rawText == null || rawText.trim().isEmpty()) {
            return meta;
        }

        String[] lines = rawText.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase(Locale.US);

            // Lab Name detection
            if (meta.labName == null || meta.labName.isEmpty()) {
                if (lower.contains("idexx")) {
                    meta.labName = "IDEXX Reference Laboratories";
                } else if (lower.contains("antech")) {
                    meta.labName = "Antech Diagnostics";
                } else if (lower.contains("heska")) {
                    meta.labName = "Heska Veterinary Diagnostics";
                } else if (lower.contains("zoetis")) {
                    meta.labName = "Zoetis Reference Laboratories";
                } else if (lower.contains("marshfield")) {
                    meta.labName = "Marshfield Veterinary Laboratories";
                } else if (lower.contains("laboratory") || lower.contains("diagnostics") || lower.contains("vet clinic")) {
                    if (trimmed.length() < 50 && !lower.contains("patient") && !lower.contains("sample")) {
                        meta.labName = trimmed;
                    }
                }
            }

            // Report Date detection (e.g. "Date: 11/02/2026", "Collected: 2026-10-15")
            if (meta.reportDateStr == null) {
                Matcher dateMatcher = Pattern.compile(
                        "(?:date|collected|reported|received|time)[\\s:]+([0-9]{1,4}[-/\\.][0-9]{1,2}[-/\\.][0-9]{1,4}|[A-Za-z]{3,9}\\s+[0-9]{1,2},?\\s+[0-9]{4})",
                        Pattern.CASE_INSENSITIVE
                ).matcher(trimmed);
                if (dateMatcher.find()) {
                    meta.reportDateStr = dateMatcher.group(1).trim();
                }
            }

            // Pet Name detection (e.g. "Patient: Luna", "Animal: Max")
            if (meta.petName == null) {
                Matcher petMatcher = Pattern.compile(
                        "(?:patient|animal|pet name)[\\s:]+([A-Za-z0-9_-]+)",
                        Pattern.CASE_INSENSITIVE
                ).matcher(trimmed);
                if (petMatcher.find()) {
                    meta.petName = petMatcher.group(1).trim();
                }
            }

            // Species detection (Canine / Feline / Dog / Cat)
            if (meta.species == null) {
                if (lower.contains("canine") || lower.contains("dog")) {
                    meta.species = "Dog";
                } else if (lower.contains("feline") || lower.contains("cat")) {
                    meta.species = "Cat";
                }
            }

            // Breed detection
            if (meta.breed == null) {
                Matcher breedMatcher = Pattern.compile("(?:breed)[\\s:]+([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE).matcher(trimmed);
                if (breedMatcher.find()) {
                    meta.breed = breedMatcher.group(1).trim();
                }
            }

            // Age detection
            if (meta.age == null) {
                Matcher ageMatcher = Pattern.compile("(?:age)[\\s:]+([0-9]+(?:\\.[0-9]+)?\\s*(?:years?|yrs?|months?|m|y)?)", Pattern.CASE_INSENSITIVE).matcher(trimmed);
                if (ageMatcher.find()) {
                    meta.age = ageMatcher.group(1).trim();
                }
            }

            // Gender / Sex detection
            if (meta.gender == null) {
                Matcher genderMatcher = Pattern.compile("(?:sex|gender)[\\s:]+([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE).matcher(trimmed);
                if (genderMatcher.find()) {
                    meta.gender = genderMatcher.group(1).trim();
                }
            }

            // Weight detection
            if (meta.weight == null) {
                Matcher weightMatcher = Pattern.compile("(?:weight|wt)[\\s:]+([0-9]+(?:\\.[0-9]+)?\\s*(?:kg|lbs?|pounds?|g)?)", Pattern.CASE_INSENSITIVE).matcher(trimmed);
                if (weightMatcher.find()) {
                    meta.weight = weightMatcher.group(1).trim();
                }
            }

            // Sample Quality detection (Hemolysis, Lipemia, Icterus)
            for (String term : SAMPLE_QUALITY_TERMS) {
                if (lower.contains(term)) {
                    String warning = cleanSampleQualityWarning(trimmed, term);
                    if (!meta.sampleQualityWarnings.contains(warning)) {
                        meta.sampleQualityWarnings.add(warning);
                    }
                }
            }
        }

        return meta;
    }

    private static String cleanSampleQualityWarning(String rawLine, String term) {
        String clean = rawLine.replaceAll("[*:\\[\\]]", " ").trim();
        return "Sample Quality Notice (" + term.toUpperCase(Locale.US) + "): " + clean;
    }

    /**
     * Parses raw OCR text extracted from a lab report into a list of structured LabTestItem objects.
     */
    @NonNull
    public static List<LabTestItem> parseReportText(@Nullable String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LabTestItem> results = new ArrayList<>();
        Set<String> seenParameterKeys = new HashSet<>();

        // Normalise decimal spacing produced by OCR: "1 . 2" -> "1.2"
        String normalized = rawText.replaceAll("(\\d+)\\s*\\.\\s*(\\d+)", "$1.$2")
                .replaceAll("(\\d+)\\s*,\\s*(\\d+)", "$1,$2");

        String[] lines = normalized.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isDiscardableNonLabLine(trimmed)) {
                continue;
            }

            // Check if this line is purely a sample-quality observation
            if (isSampleQualityOnlyLine(trimmed)) {
                continue;
            }

            LabTestItem item = parseLine(trimmed);
            if (item != null) {
                String dedupKey = item.getTestName().toUpperCase(Locale.US) + "_" + item.getValue();
                if (!seenParameterKeys.contains(dedupKey)) {
                    seenParameterKeys.add(dedupKey);
                    classifyTestItem(item);
                    results.add(item);
                }
            }
        }

        return results;
    }

    /**
     * Parses a flexible numeric representation with dots, commas, or negative signs.
     */
    @Nullable
    public static Double parseFlexibleDouble(@Nullable String str) {
        if (str == null) return null;
        String trimmed = str.trim().replaceAll("[*:\\[\\]^~]", "");
        if (trimmed.isEmpty()) return null;

        // Comma as decimal separator e.g. "45,5" or "10,0"
        if (trimmed.matches("^[+-]?\\d+,\\d+$")) {
            trimmed = trimmed.replace(',', '.');
        } else if (trimmed.matches("^[+-]?\\d{1,3}(,\\d{3})+(\\.\\d+)?$")) {
            // Thousands separator e.g. "1,200" or "1,200.50"
            trimmed = trimmed.replace(",", "");
        }

        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts low and high range boundaries from refRangeText if not already populated.
     */
    public static void extractRangeBoundaries(@NonNull LabTestItem item) {
        String rangeText = item.getRefRangeText();
        if (rangeText == null || rangeText.trim().isEmpty()) {
            return;
        }

        if (item.getRefRangeLow() != null || item.getRefRangeHigh() != null) {
            return;
        }

        Matcher intervalMatcher = RANGE_INTERVAL_PATTERN.matcher(rangeText);
        if (intervalMatcher.find()) {
            item.setRefRangeLow(parseFlexibleDouble(intervalMatcher.group(1)));
            item.setRefRangeHigh(parseFlexibleDouble(intervalMatcher.group(2)));
            return;
        }

        Matcher lessThanMatcher = RANGE_LESS_THAN_PATTERN.matcher(rangeText);
        if (lessThanMatcher.find()) {
            item.setRefRangeHigh(parseFlexibleDouble(lessThanMatcher.group(1)));
            return;
        }

        Matcher greaterThanMatcher = RANGE_GREATER_THAN_PATTERN.matcher(rangeText);
        if (greaterThanMatcher.find()) {
            item.setRefRangeLow(parseFlexibleDouble(greaterThanMatcher.group(1)));
        }
    }

    /**
     * Classifies a single test item strictly against its extracted reference range.
     * Missing reference range on the report MUST remain UNCLASSIFIED.
     */
    public static void classifyTestItem(@NonNull LabTestItem item) {
        // If flagged as a sample quality warning, do not classify as standard disease
        if (item.isSampleQualityWarning()) {
            item.setStatus(LabTestItem.STATUS_UNCLASSIFIED);
            return;
        }

        String valStr = item.getValue() != null ? item.getValue().trim() : "";
        if (valStr.isEmpty()) {
            item.setStatus(LabTestItem.STATUS_INCOMPLETE);
            return;
        }

        String lowerVal = valStr.toLowerCase(Locale.US);

        // 1. Check for specimen/incomplete flags
        if (INCOMPLETE_INDICATORS.contains(lowerVal)) {
            item.setStatus(LabTestItem.STATUS_INCOMPLETE);
            return;
        }

        // 2. Check for qualitative results (Negative, Positive, etc.)
        if (NORMAL_QUALITATIVE.contains(lowerVal)) {
            item.setStatus(LabTestItem.STATUS_NORMAL);
            return;
        }
        if (ABNORMAL_QUALITATIVE.contains(lowerVal)) {
            item.setStatus(LabTestItem.STATUS_HIGH);
            return;
        }

        // Ensure range boundaries are extracted if refRangeText is present
        extractRangeBoundaries(item);

        // 3. Numeric value resolution
        Double numVal = item.getNumericValue();
        if (numVal == null) {
            numVal = parseFlexibleDouble(valStr);
            if (numVal != null) {
                item.setNumericValue(numVal);
            }
        }

        // 4. Strict rule: If reference range is missing from report, NEVER substitute or assume.
        String refRange = item.getRefRangeText();
        Double low = item.getRefRangeLow();
        Double high = item.getRefRangeHigh();

        if ((refRange == null || refRange.trim().isEmpty()) && low == null && high == null) {
            item.setStatus(LabTestItem.STATUS_UNCLASSIFIED);
            return;
        }

        // 5. Evaluate against parsed reference interval
        if (numVal != null) {
            if (low != null && high != null) {
                if (numVal < low) {
                    item.setStatus(LabTestItem.STATUS_LOW);
                } else if (numVal > high) {
                    item.setStatus(LabTestItem.STATUS_HIGH);
                } else {
                    item.setStatus(LabTestItem.STATUS_NORMAL);
                }
            } else if (high != null) {
                if (numVal > high) {
                    item.setStatus(LabTestItem.STATUS_HIGH);
                } else {
                    item.setStatus(LabTestItem.STATUS_NORMAL);
                }
            } else if (low != null) {
                if (numVal < low) {
                    item.setStatus(LabTestItem.STATUS_LOW);
                } else {
                    item.setStatus(LabTestItem.STATUS_NORMAL);
                }
            } else {
                item.setStatus(LabTestItem.STATUS_UNCLASSIFIED);
            }
        } else {
            item.setStatus(LabTestItem.STATUS_UNCLASSIFIED);
        }
    }

    /**
     * Parses a single text line into a LabTestItem by extracting the test name, value, unit, and range.
     * Discards noise, addresses, postal codes, and non-laboratory lines.
     */
    @Nullable
    public static LabTestItem parseLine(String line) {
        String cleanLine = line.replaceAll("[\t]+", " ").trim();

        // 1. Reference range extraction
        String refRangeText = null;
        Double low = null;
        Double high = null;

        Matcher intervalMatcher = RANGE_INTERVAL_PATTERN.matcher(cleanLine);
        int rangeStartIdx = -1;
        if (intervalMatcher.find()) {
            refRangeText = intervalMatcher.group(0).replaceAll("[\\[\\]\\(\\)]", "").trim();
            low = parseFlexibleDouble(intervalMatcher.group(1));
            high = parseFlexibleDouble(intervalMatcher.group(2));
            rangeStartIdx = intervalMatcher.start();
        } else {
            Matcher lessThanMatcher = RANGE_LESS_THAN_PATTERN.matcher(cleanLine);
            if (lessThanMatcher.find()) {
                refRangeText = lessThanMatcher.group(0).trim();
                high = parseFlexibleDouble(lessThanMatcher.group(1));
                rangeStartIdx = lessThanMatcher.start();
            } else {
                Matcher greaterThanMatcher = RANGE_GREATER_THAN_PATTERN.matcher(cleanLine);
                if (greaterThanMatcher.find()) {
                    refRangeText = greaterThanMatcher.group(0).trim();
                    low = parseFlexibleDouble(greaterThanMatcher.group(1));
                    rangeStartIdx = greaterThanMatcher.start();
                }
            }
        }

        // Extract substring before reference range
        String beforeRange = cleanLine;
        String afterRange = "";
        if (rangeStartIdx >= 0) {
            beforeRange = cleanLine.substring(0, rangeStartIdx).trim();
            int rangeEnd = rangeStartIdx + (refRangeText != null ? refRangeText.length() : 0);
            if (rangeEnd < cleanLine.length()) {
                afterRange = cleanLine.substring(rangeEnd).trim();
            }
        }

        // Tokenize beforeRange: [Test Name tokens...] [Value] [Optional Unit]
        String[] tokens = beforeRange.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }

        // Identify value token index (scanning backwards from end of beforeRange)
        int valueIdx = -1;
        for (int i = tokens.length - 1; i >= 1; i--) {
            String token = cleanToken(tokens[i]);
            // Avoid treating unit tokens (like "10^9/L" or "x10^3") as the primary value
            if (isUnitString(token)) {
                continue;
            }
            if (isNumericOrQualitative(token)) {
                valueIdx = i;
                break;
            }
        }

        if (valueIdx == -1) {
            return null;
        }

        // Construct Test Name
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < valueIdx; i++) {
            String t = tokens[i].replaceAll("[:*\\[\\]#]", "");
            if (!t.isEmpty()) {
                if (nameBuilder.length() > 0) nameBuilder.append(" ");
                nameBuilder.append(t);
            }
        }
        String rawTestName = nameBuilder.toString().trim();
        if (rawTestName.length() < 2 || isGenericNonLabWord(rawTestName)) {
            return null;
        }

        // Check if name is known or has standard lab traits
        String canonicalName = resolveCanonicalTestName(rawTestName);
        boolean isRecognized = canonicalName != null;
        boolean hasRange = refRangeText != null && !refRangeText.isEmpty();

        // Clean value
        String rawValue = cleanToken(tokens[valueIdx]);
        Double numVal = parseFlexibleDouble(rawValue);

        // Unit extraction (look between valueIdx and range, or after range)
        StringBuilder unitBuilder = new StringBuilder();
        for (int i = valueIdx + 1; i < tokens.length; i++) {
            String t = cleanToken(tokens[i]);
            if (!isFlagWord(t) && isUnitString(t)) {
                if (unitBuilder.length() > 0) unitBuilder.append(" ");
                unitBuilder.append(t);
            }
        }
        if (unitBuilder.length() == 0 && !afterRange.isEmpty()) {
            for (String part : afterRange.split("\\s+")) {
                String t = cleanToken(part);
                if (!isFlagWord(t) && isUnitString(t)) {
                    if (unitBuilder.length() > 0) unitBuilder.append(" ");
                    unitBuilder.append(t);
                }
            }
        }
        String unit = unitBuilder.toString().trim();
        boolean hasUnit = !unit.isEmpty();
        boolean isQual = isQualitative(rawValue);

        // If not recognized and has no reference range, no unit, and not qualitative, discard as non-lab text/address
        if (!isRecognized && !hasRange && !hasUnit && !isQual) {
            return null;
        }

        // Keep rawTestName as printed on the report
        String finalTestName = rawTestName;

        // Flag uncertain extractions
        boolean isUncertain = false;
        String uncertainReason = null;
        if (!isRecognized) {
            isUncertain = true;
            uncertainReason = "Uncommon parameter name. Please verify spelling.";
        } else if (numVal == null && !isQualitative(rawValue)) {
            isUncertain = true;
            uncertainReason = "Non-numeric result detected. Please verify.";
        } else if (!hasRange) {
            isUncertain = true;
            uncertainReason = "No reference range found on report.";
        }

        String category = inferCategory(finalTestName);

        LabTestItem item = new LabTestItem(
                finalTestName,
                category,
                rawValue,
                numVal,
                unit,
                low,
                high,
                refRangeText != null ? refRangeText : "",
                LabTestItem.STATUS_UNCLASSIFIED,
                ""
        );
        item.setUncertain(isUncertain);
        item.setUncertainReason(uncertainReason);

        return item;
    }

    private static String cleanToken(String token) {
        if (token == null) return "";
        return token.replaceAll("[:*\\[\\]!▲▼]", "").replaceAll("^[,.]+|[,.]+$", "").trim();
    }

    private static boolean isFlagWord(String token) {
        String lower = token.toLowerCase(Locale.US);
        return lower.equals("h") || lower.equals("l") || lower.equals("high")
                || lower.equals("low") || lower.equals("crit") || lower.equals("abn")
                || lower.equals("normal") || lower.equals("norm") || lower.equals("*");
    }

    private static boolean isUnitString(String token) {
        String lower = token.toLowerCase(Locale.US).replaceAll("[\\[\\]\\(\\)]", "").trim();
        return COMMON_VET_UNITS.contains(lower) || lower.contains("/l") || lower.contains("/ul")
                || lower.contains("mg/") || lower.contains("g/") || lower.contains("%")
                || lower.contains("10^") || lower.equals("ratio") || lower.equals("sec");
    }

    private static boolean isNumericOrQualitative(String token) {
        if (token == null || token.isEmpty()) return false;
        if (parseFlexibleDouble(token) != null) {
            return true;
        }
        String lower = token.toLowerCase(Locale.US);
        return NORMAL_QUALITATIVE.contains(lower)
                || ABNORMAL_QUALITATIVE.contains(lower)
                || INCOMPLETE_INDICATORS.contains(lower);
    }

    private static boolean isQualitative(String token) {
        if (token == null || token.isEmpty()) return false;
        String lower = token.toLowerCase(Locale.US);
        return NORMAL_QUALITATIVE.contains(lower)
                || ABNORMAL_QUALITATIVE.contains(lower)
                || INCOMPLETE_INDICATORS.contains(lower);
    }

    @Nullable
    private static String resolveCanonicalTestName(String rawName) {
        String key = rawName.toUpperCase(Locale.US).replaceAll("[^A-Z0-9/]", "");
        if (KNOWN_VET_PARAMETERS.containsKey(key)) {
            return KNOWN_VET_PARAMETERS.get(key);
        }
        for (Map.Entry<String, String> entry : KNOWN_VET_PARAMETERS.entrySet()) {
            if (key.contains(entry.getKey()) || entry.getKey().contains(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Rejects lines that contain address headers, client/doctor names, postal codes, and phone numbers.
     */
    private static boolean isDiscardableNonLabLine(String line) {
        String lower = line.toLowerCase(Locale.US);

        // Address & contact indicators
        if (lower.contains("street") || lower.contains("suite ") || lower.contains("road")
                || lower.contains("boulevard") || lower.contains("blvd") || lower.contains("avenue")
                || lower.contains("ave.") || lower.contains("p.o. box") || lower.contains("zip:")
                || lower.contains("postal") || lower.contains("phone:") || lower.contains("tel:")
                || lower.contains("fax:") || lower.contains("email:") || lower.contains("www.")
                || lower.contains("http:") || lower.contains("https:") || lower.contains("address:")
                || lower.contains(" hospital way") || lower.contains("way\n") || lower.endsWith(" way")
                || lower.contains(" drive") || lower.contains(" lane")) {
            return true;
        }

        // Clinic & administrative metadata
        if (lower.contains("patient id") || lower.contains("sample id") || lower.contains("accession")
                || lower.contains("client:") || lower.contains("owner:") || lower.contains("doctor:")
                || lower.contains("dr.") || lower.contains("veterinarian:") || lower.contains("clinic:")
                || lower.contains("hospital:") || lower.contains("account #") || lower.contains("order #")
                || lower.contains("page ") || lower.contains("test name") || lower.contains("reference interval")
                || lower.contains("flag") || lower.contains("technician") || lower.contains("verified by")) {
            return true;
        }

        // Pet demographics lines (handled separately by metadata extractor)
        if (lower.startsWith("patient:") || lower.startsWith("animal:") || lower.startsWith("species:")
                || lower.startsWith("breed:") || lower.startsWith("age:") || lower.startsWith("sex:")
                || lower.startsWith("weight:") || lower.startsWith("dob:")) {
            return true;
        }

        return false;
    }

    private static boolean isSampleQualityOnlyLine(String line) {
        String lower = line.toLowerCase(Locale.US);
        for (String term : SAMPLE_QUALITY_TERMS) {
            if (lower.startsWith(term) || (lower.contains(term) && (lower.contains("index") || lower.contains("degree") || lower.contains("sample")))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGenericNonLabWord(String str) {
        String lower = str.toLowerCase(Locale.US);
        return lower.equals("result") || lower.equals("test") || lower.equals("value")
                || lower.equals("date") || lower.equals("notes") || lower.equals("flag")
                || lower.equals("status") || lower.equals("units") || lower.equals("unit")
                || lower.equals("range") || lower.equals("comments") || lower.equals("comment")
                || lower.equals("interval") || lower.equals("report") || lower.equals("analysis")
                || lower.equals("page") || lower.equals("suite") || lower.equals("box");
    }

    /**
     * Categorizes common veterinary tests into clinical diagnostic panels.
     */
    public static String inferCategory(String testName) {
        String t = testName.toUpperCase(Locale.US);
        if (t.contains("ALT") || t.contains("AST") || t.contains("ALP") || t.contains("ALKP")
                || t.contains("GGT") || t.contains("BILIRUBIN") || t.contains("TBIL") || t.contains("BILE ACID")) {
            return "Liver Panel";
        } else if (t.contains("BUN") || t.contains("CREA") || t.contains("UREA")
                || t.contains("SDMA") || t.contains("PHOS")) {
            return "Kidney Panel";
        } else if (t.contains("WBC") || t.contains("RBC") || t.contains("HCT") || t.contains("PCV")
                || t.contains("HGB") || t.contains("PLATELET") || t.contains("PLT") || t.contains("MCV")
                || t.contains("MCH") || t.contains("MCHC") || t.contains("RDW")
                || t.contains("NEUTROPHIL") || t.contains("LYMPHOCYTE") || t.contains("EOS")
                || t.contains("MONOCYTE") || t.contains("BASOPHIL") || t.contains("RETICULOCYTE")) {
            return "Hematology (CBC)";
        } else if (t.contains("NA") || t.contains("SODIUM") || t.contains("POTASSIUM")
                || t.contains("CHLORIDE") || t.contains("CALCIUM") || t.contains("ELECTROLYTE")
                || t.contains("BICARBONATE") || t.contains("TCO2")) {
            return "Electrolytes";
        } else if (t.contains("GLUCOSE") || t.contains("GLU") || t.contains("FRUCTOSAMINE")) {
            return "Endocrine / Metabolic";
        } else if (t.contains("T4") || t.contains("THYROID") || t.contains("TSH") || t.contains("CORTISOL")) {
            return "Endocrinology";
        } else if (t.contains("ALBUMIN") || t.contains("GLOBULIN") || t.contains("PROTEIN") || t.contains("TP") || t.contains("A/G")) {
            return "Protein Profile";
        } else if (t.contains("AMYLASE") || t.contains("LIPASE") || t.contains("SPEC CPL") || t.contains("SPEC FPL")) {
            return "Pancreatic & Lipids";
        } else if (t.contains("URINE") || t.contains("USG") || t.contains("SEDIMENT")) {
            return "Urinalysis";
        }
        return "General Vitals";
    }
}
