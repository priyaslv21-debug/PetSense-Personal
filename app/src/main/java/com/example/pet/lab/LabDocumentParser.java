package com.example.pet.lab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles text extraction from veterinary lab documents across all supported formats:
 * PDF (including multi-page documents), JPG, JPEG, and PNG.
 *
 * Utilizes:
 * - Native Android PdfRenderer to rasterize PDF pages.
 * - Google ML Kit On-Device Text Recognition for fast, private, offline OCR.
 * - LabRuleEngine for deterministic parsing and report-range-only classification.
 */
public class LabDocumentParser {

    private static final String TAG = "LabDocumentParser";

    public interface ParserCallback {
        void onSuccess(List<LabTestItem> parsedItems, String rawText, int pageCount);
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
     * Parses a laboratory report from a local content Uri or file path.
     * Automatically differentiates between PDF documents and standard image formats.
     * Gracefully rejects unsupported formats and flags unreadable documents.
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

                List<LabTestItem> items = LabRuleEngine.parseReportText(extractedText);
                final int finalPageCount = pageCount;
                mainHandler.post(() -> callback.onSuccess(items, extractedText, finalPageCount));

            } catch (Exception e) {
                Log.e(TAG, "Error parsing lab document", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Iterates through all pages in a multi-page PDF document, rendering each page
     * at 2x resolution and performing OCR.
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
                    // Render at 2x scale for crisp OCR recognition of small decimal prints
                    int width = page.getWidth() * 2;
                    int height = page.getHeight() * 2;
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
                    Text visionText = Tasks.await(textRecognizer.process(inputImage));

                    if (visionText != null && !visionText.getText().isEmpty()) {
                        outText.append(visionText.getText()).append("\n");
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
            // Fallback: copy to temporary file and decode scaled bitmap
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
        if (visionText != null && !visionText.getText().isEmpty()) {
            outText.append(visionText.getText()).append("\n");
        }
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
