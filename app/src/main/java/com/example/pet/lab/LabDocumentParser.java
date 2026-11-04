package com.example.pet.lab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pet.data.LabTestItem;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles text extraction from veterinary lab documents across all supported formats:
 * PDF (including multi-page documents), JPG, JPEG, and PNG.
 *
 * Employs:
 * 1. Native Android PdfRenderer to rasterize PDF pages at high DPI (3x).
 * 2. Google ML Kit On-Device Text Recognition.
 * 3. Spatial Line Reconstruction algorithm to group multi-column tabular data into horizontal rows.
 * 4. Extraction of document metadata (Lab name, test date, pet demographics, sample quality warnings).
 * 5. Deterministic LabRuleEngine validation and classification.
 */
public class LabDocumentParser {

    private static final String TAG = "LabDocumentParser";

    public interface ParserCallback {
        void onSuccess(List<LabTestItem> parsedItems, String rawText, int pageCount, LabRuleEngine.ReportMetadata metadata);
        void onError(Exception e);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TextRecognizer textRecognizer;

    public LabDocumentParser(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    /**
     * Parses a laboratory report from a local content Uri or file path asynchronously.
     */
    public void parseDocumentAsync(@NonNull Uri uri, @NonNull String mimeType, @NonNull ParserCallback callback) {
        executor.execute(() -> {
            try {
                String resolvedMime = mimeType;
                if (resolvedMime == null || resolvedMime.trim().isEmpty() || resolvedMime.equals("*/*")) {
                    try {
                        resolvedMime = context.getContentResolver().getType(uri);
                    } catch (Exception ignored) {}
                }
                String uriLower = uri.toString().toLowerCase();

                boolean isPdf = (resolvedMime != null && resolvedMime.equalsIgnoreCase("application/pdf"))
                        || uriLower.endsWith(".pdf");

                boolean isImage = (resolvedMime != null && resolvedMime.startsWith("image/"))
                        || uriLower.endsWith(".jpg") || uriLower.endsWith(".jpeg")
                        || uriLower.endsWith(".png");

                if (!isPdf && !isImage) {
                    throw new IllegalArgumentException("Unsupported file format. Please upload a PDF, JPG, JPEG, or PNG laboratory report.");
                }

                StringBuilder fullText = new StringBuilder();
                int pageCount = 1;

                if (isPdf) {
                    pageCount = processPdf(uri, fullText);
                } else {
                    processImage(uri, fullText);
                }

                String extractedText = fullText.toString().trim();
                if (extractedText.isEmpty() || extractedText.split("\\r?\\n").length < 2) {
                    throw new Exception("Document could not be clearly read. Please ensure proper lighting and focus, or use manual entry.");
                }

                LabRuleEngine.ReportMetadata metadata = LabRuleEngine.extractReportMetadata(extractedText);
                List<LabTestItem> items = LabRuleEngine.parseReportText(extractedText);
                final int finalPageCount = pageCount;

                mainHandler.post(() -> callback.onSuccess(items, extractedText, finalPageCount, metadata));

            } catch (Exception e) {
                Log.e(TAG, "Error parsing lab document", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Iterates through all pages in a multi-page PDF document, rendering each page
     * at 3x resolution and performing OCR with spatial row reconstruction.
     */
    private int processPdf(Uri uri, StringBuilder outText) throws Exception {
        File tempPdf = copyUriToTempFile(uri, "temp_report.pdf");
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;

        try {
            pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            int count = renderer.getPageCount();

            for (int i = 0; i < count; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                Bitmap bitmap = null;
                try {
                    // Render at 3x scale for crisp OCR recognition of small decimal points and units
                    int width = Math.min(3000, page.getWidth() * 3);
                    int height = Math.min(4000, page.getHeight() * 3);
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
                    Text visionText = Tasks.await(textRecognizer.process(inputImage));

                    if (visionText != null) {
                        String pageText = extractSpatiallyReconstructedText(visionText);
                        if (!pageText.isEmpty()) {
                            outText.append(pageText).append("\n");
                        }
                    }
                } finally {
                    page.close();
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
            }

            return count;

        } finally {
            if (renderer != null) renderer.close();
            if (pfd != null) pfd.close();
            if (tempPdf.exists()) tempPdf.delete();
        }
    }

    /**
     * Processes a single image file (JPG, JPEG, PNG).
     */
    private void processImage(Uri uri, StringBuilder outText) throws Exception {
        InputImage inputImage;
        try {
            inputImage = InputImage.fromFilePath(context, uri);
        } catch (Exception e) {
            File tempImage = copyUriToTempFile(uri, "temp_image.img");
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(tempImage.getAbsolutePath());
                if (bitmap == null) throw new Exception("Could not decode image from file.");
                inputImage = InputImage.fromBitmap(bitmap, 0);
            } finally {
                if (tempImage.exists()) tempImage.delete();
            }
        }

        Text visionText = Tasks.await(textRecognizer.process(inputImage));
        if (visionText != null) {
            String pageText = extractSpatiallyReconstructedText(visionText);
            if (!pageText.isEmpty()) {
                outText.append(pageText).append("\n");
            }
        }
    }

    /**
     * Reconstructs lines of tabular data by grouping OCR lines and elements according to their
     * vertical coordinates (Y position), then sorting horizontally (X position).
     * This prevents tabular columns from being split or scrambled across different OCR blocks.
     */
    @NonNull
    public static String extractSpatiallyReconstructedText(@NonNull Text visionText) {
        List<Text.Line> allLines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            allLines.addAll(block.getLines());
        }

        if (allLines.isEmpty()) {
            return visionText.getText();
        }

        // Sort lines primarily by top Y position
        Collections.sort(allLines, Comparator.comparingInt(l -> {
            Rect box = l.getBoundingBox();
            return box != null ? box.top : 0;
        }));

        // Cluster lines that lie on approximately the same horizontal band
        List<List<Text.Line>> rowClusters = new ArrayList<>();
        for (Text.Line line : allLines) {
            Rect lineBox = line.getBoundingBox();
            if (lineBox == null) continue;

            int lineCenterY = (lineBox.top + lineBox.bottom) / 2;
            int lineHeight = Math.max(12, lineBox.height());
            int threshold = (int) (lineHeight * 0.65);

            boolean addedToCluster = false;
            for (List<Text.Line> cluster : rowClusters) {
                Rect firstBox = cluster.get(0).getBoundingBox();
                if (firstBox != null) {
                    int clusterCenterY = (firstBox.top + firstBox.bottom) / 2;
                    if (Math.abs(lineCenterY - clusterCenterY) <= threshold) {
                        cluster.add(line);
                        addedToCluster = true;
                        break;
                    }
                }
            }

            if (!addedToCluster) {
                List<Text.Line> newCluster = new ArrayList<>();
                newCluster.add(line);
                rowClusters.add(newCluster);
            }
        }

        // Within each row cluster, sort left-to-right by X coordinate
        StringBuilder sb = new StringBuilder();
        for (List<Text.Line> cluster : rowClusters) {
            Collections.sort(cluster, Comparator.comparingInt(l -> {
                Rect box = l.getBoundingBox();
                return box != null ? box.left : 0;
            }));

            StringBuilder rowBuilder = new StringBuilder();
            for (Text.Line line : cluster) {
                String text = line.getText().trim();
                if (!text.isEmpty()) {
                    if (rowBuilder.length() > 0) rowBuilder.append("    ");
                    rowBuilder.append(text);
                }
            }
            if (rowBuilder.length() > 0) {
                sb.append(rowBuilder.toString()).append("\n");
            }
        }

        String reconstructed = sb.toString().trim();
        return !reconstructed.isEmpty() ? reconstructed : visionText.getText();
    }

    private File copyUriToTempFile(Uri uri, String tempFileName) throws Exception {
        File cacheDir = new File(context.getCacheDir(), "lab_ocr_temp");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File tempFile = new File(cacheDir, System.currentTimeMillis() + "_" + tempFileName);

        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tempFile)) {
            if (in == null) throw new Exception("Unable to open input stream for " + uri);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return tempFile;
    }

    public void close() {
        textRecognizer.close();
        executor.shutdown();
    }
}
