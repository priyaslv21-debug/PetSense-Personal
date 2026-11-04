package com.example.pet.lab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pet.data.LabTestItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic rule-based parsing and classification engine for veterinary laboratory reports.
 *
 * CRITICAL CLINICAL RULES IMPLEMENTED:
 * 1. Strictly prioritizes reference ranges extracted directly from the uploaded report.
 * 2. NEVER substitutes generic canine/feline ranges if the report range is missing.
 *    Any test without a report-printed reference range is classified as UNCLASSIFIED.
 * 3. Independent of AI: evaluates measurements purely against the report's reference boundaries.
 * 4. Gracefully handles qualitative results ("Negative", "Positive"), missing values
 *    ("QNS", "Hemolyzed"), decimal/comma differences, and unit variations.
 */
public class LabRuleEngine {

    // Common qualitative indicators
    private static final Set<String> NORMAL_QUALITATIVE = new HashSet<>();
    private static final Set<String> ABNORMAL_QUALITATIVE = new HashSet<>();
    private static final Set<String> INCOMPLETE_INDICATORS = new HashSet<>();

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

        // Incomplete / Error / Specimen issue indicators
        INCOMPLETE_INDICATORS.add("qns");
        INCOMPLETE_INDICATORS.add("hemolyzed");
        INCOMPLETE_INDICATORS.add("lipemic");
        INCOMPLETE_INDICATORS.add("icteric");
        INCOMPLETE_INDICATORS.add("pending");
        INCOMPLETE_INDICATORS.add("clotted");
        INCOMPLETE_INDICATORS.add("cancelled");
        INCOMPLETE_INDICATORS.add("error");
    }

    /**
     * Regex patterns to detect tabular rows containing reference ranges:
     * e.g. "10 - 100", "0.5 - 1.5", "10,0 - 100,0", "< 5.0", "> 20"
     */
    private static final Pattern RANGE_INTERVAL_PATTERN =
            Pattern.compile("([\\d.,]+)\\s*(?:-|–|to)\\s*([\\d.,]+)");

    private static final Pattern RANGE_LESS_THAN_PATTERN =
            Pattern.compile("(?:<|<=|less than)\\s*([\\d.,]+)");

    private static final Pattern RANGE_GREATER_THAN_PATTERN =
            Pattern.compile("(?:>|>=|greater than)\\s*([\\d.,]+)");

    /**
     * Parses raw OCR text extracted from a lab report into a list of structured LabTestItem objects.
     */
    @NonNull
    public static List<LabTestItem> parseReportText(@Nullable String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LabTestItem> results = new ArrayList<>();
        String[] lines = rawText.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isHeaderOrFooterLine(trimmed)) {
                continue;
            }

            LabTestItem item = parseLine(trimmed);
            if (item != null) {
                // Classify based strictly on report range
                classifyTestItem(item);
                results.add(item);
            }
        }

        return results;
    }

    /**
     * Parses a flexible numeric representation with dots, commas, or thousands separators.
     */
    @Nullable
    public static Double parseFlexibleDouble(@Nullable String str) {
        if (str == null) return null;
        String trimmed = str.trim().replaceAll("[*:\\[\\]]", "");
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
     * If no reference range was found on the report, it is marked UNCLASSIFIED.
     */
    public static void classifyTestItem(@NonNull LabTestItem item) {
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

        // 3. Check for numeric value
        Double numVal = item.getNumericValue();
        if (numVal == null) {
            numVal = parseFlexibleDouble(valStr);
            if (numVal != null) {
                item.setNumericValue(numVal);
            }
        }

        // 4. RULE: If reference range is missing from report, NEVER guess or substitute generic range.
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
                // Pattern "< high"
                if (numVal > high) {
                    item.setStatus(LabTestItem.STATUS_HIGH);
                } else {
                    item.setStatus(LabTestItem.STATUS_NORMAL);
                }
            } else if (low != null) {
                // Pattern "> low"
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
     */
    @Nullable
    private static LabTestItem parseLine(String line) {
        // Look for reference range first
        String refRangeText = null;
        Double low = null;
        Double high = null;

        Matcher intervalMatcher = RANGE_INTERVAL_PATTERN.matcher(line);
        if (intervalMatcher.find()) {
            refRangeText = intervalMatcher.group(0);
            low = parseFlexibleDouble(intervalMatcher.group(1));
            high = parseFlexibleDouble(intervalMatcher.group(2));
        } else {
            Matcher lessThanMatcher = RANGE_LESS_THAN_PATTERN.matcher(line);
            if (lessThanMatcher.find()) {
                refRangeText = lessThanMatcher.group(0);
                high = parseFlexibleDouble(lessThanMatcher.group(1));
            } else {
                Matcher greaterThanMatcher = RANGE_GREATER_THAN_PATTERN.matcher(line);
                if (greaterThanMatcher.find()) {
                    refRangeText = greaterThanMatcher.group(0);
                    low = parseFlexibleDouble(greaterThanMatcher.group(1));
                }
            }
        }

        // Isolate remaining portion of the line before the reference range
        String beforeRange = line;
        if (refRangeText != null) {
            int rangeIdx = line.indexOf(refRangeText);
            if (rangeIdx > 0) {
                beforeRange = line.substring(0, rangeIdx).trim();
            }
        }

        // Tokenize remaining text into: [Test Name] [Value] [Optional Unit]
        String[] tokens = beforeRange.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }

        // Find the index of the measured value token (numeric or qualitative keyword)
        int valueIdx = -1;
        for (int i = tokens.length - 1; i >= 1; i--) {
            String token = tokens[i].replaceAll("[:*\\[\\]]", "").replaceAll("^[,]+|[,]+$", "");
            if (isNumericOrQualitative(token)) {
                valueIdx = i;
                break;
            }
        }

        if (valueIdx == -1) {
            return null;
        }

        // Construct Test Name from tokens before valueIdx
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < valueIdx; i++) {
            if (i > 0) nameBuilder.append(" ");
            nameBuilder.append(tokens[i].replaceAll("[:*]", ""));
        }
        String testName = nameBuilder.toString().trim();
        if (testName.length() < 2 || isGenericWord(testName)) {
            return null;
        }

        // Value - keep original formatting (including decimal comma if present)
        String value = tokens[valueIdx].replaceAll("[:*\\[\\]]", "").replaceAll("^[,]+|[,]+$", "");
        Double numVal = parseFlexibleDouble(value);

        // Unit (tokens between valueIdx and range, if any)
        StringBuilder unitBuilder = new StringBuilder();
        for (int i = valueIdx + 1; i < tokens.length; i++) {
            String t = tokens[i].replaceAll("[:,\\[\\]]", "");
            if (!t.isEmpty() && !t.equalsIgnoreCase("high") && !t.equalsIgnoreCase("low")) {
                if (unitBuilder.length() > 0) unitBuilder.append(" ");
                unitBuilder.append(t);
            }
        }
        String unit = unitBuilder.toString().trim();

        // Infer medical category
        String category = inferCategory(testName);

        LabTestItem item = new LabTestItem(
                testName,
                category,
                value,
                numVal,
                unit,
                low,
                high,
                refRangeText != null ? refRangeText : "",
                LabTestItem.STATUS_UNCLASSIFIED,
                ""
        );

        return item;
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

    private static boolean isHeaderOrFooterLine(String line) {
        String lower = line.toLowerCase(Locale.US);
        return lower.contains("patient") || lower.contains("doctor")
                || lower.contains("veterinarian") || lower.contains("clinic")
                || lower.contains("sample id") || lower.contains("page ")
                || lower.contains("phone:") || lower.contains("address:")
                || lower.contains("reference interval") || lower.contains("test name")
                || lower.startsWith("idexx") || lower.startsWith("antech")
                || lower.contains("lab report") || lower.contains("laboratory");
    }

    private static boolean isGenericWord(String str) {
        String lower = str.toLowerCase(Locale.US);
        return lower.equals("result") || lower.equals("test") || lower.equals("value")
                || lower.equals("date") || lower.equals("notes") || lower.equals("flag");
    }

    /**
     * Categorizes common veterinary tests into clinical diagnostic panels.
     */
    public static String inferCategory(String testName) {
        String t = testName.toUpperCase(Locale.US);
        if (t.contains("ALT") || t.contains("AST") || t.contains("ALP") || t.contains("ALKP")
                || t.contains("GGT") || t.contains("BILIRUBIN") || t.contains("TBIL")) {
            return "Liver Panel";
        } else if (t.contains("BUN") || t.contains("CREA") || t.contains("UREA")
                || t.contains("SDMA") || t.contains("PHOS")) {
            return "Kidney Panel";
        } else if (t.contains("WBC") || t.contains("RBC") || t.contains("HCT")
                || t.contains("HGB") || t.contains("PLATELET") || t.contains("PLT")
                || t.contains("NEUTROPHIL") || t.contains("LYMPHOCYTE") || t.contains("EOS")) {
            return "Hematology (CBC)";
        } else if (t.contains("NA") || t.contains("SODIUM") || t.contains("POTASSIUM")
                || t.contains("CHLORIDE") || t.contains("CALCIUM") || t.contains("ELECTROLYTE")) {
            return "Electrolytes";
        } else if (t.contains("GLUCOSE") || t.contains("GLU") || t.contains("FRUCTOSAMINE")) {
            return "Endocrine / Metabolic";
        } else if (t.contains("T4") || t.contains("THYROID") || t.contains("TSH") || t.contains("CORTISOL")) {
            return "Endocrinology";
        } else if (t.contains("ALBUMIN") || t.contains("GLOBULIN") || t.contains("PROTEIN") || t.contains("TP")) {
            return "Protein Profile";
        } else if (t.contains("URINE") || t.contains("USG") || t.contains("SEDIMENT")) {
            return "Urinalysis";
        }
        return "General Vitals";
    }
}
