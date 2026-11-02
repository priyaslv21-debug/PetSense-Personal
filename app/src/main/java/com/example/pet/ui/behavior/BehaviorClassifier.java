package com.example.pet.ui.behavior;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BehaviorClassifier {
    private Interpreter interpreter;
    private List<String> labels;
    private final int MODEL_INPUT_SIZE = 224;

    public BehaviorClassifier(Context context) throws IOException {
        try {
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, "model.tflite");
            interpreter = new Interpreter(tfliteModel, new Interpreter.Options());

            // 🔴 FIX 1: Read labels and delete any hidden blank lines
            List<String> rawLabels = FileUtil.loadLabels(context, "labels.txt");
            labels = new ArrayList<>();
            for (String label : rawLabels) {
                if (!label.trim().isEmpty()) {
                    labels.add(label);
                }
            }
            Log.d("TFLiteDebug", "Model loaded. Clean labels count: " + labels.size());
        } catch (Exception e) {
            Log.e("TFLiteDebug", "Error initializing TFLite Classifier", e);
            throw e;
        }
    }

    public String classifyFrame(Bitmap bitmap) {
        if (interpreter == null || labels == null || labels.isEmpty()) return "Unknown";

        try {
            // 🔴 FIX 2: Dynamically ask the model what data type it requires (FLOAT32 or UINT8)
            DataType inputDataType = interpreter.getInputTensor(0).dataType();
            DataType outputDataType = interpreter.getOutputTensor(0).dataType();

            TensorImage tensorImage = new TensorImage(inputDataType);
            tensorImage.load(bitmap);

            int minEdge = Math.min(bitmap.getWidth(), bitmap.getHeight());
            ImageProcessor.Builder processorBuilder = new ImageProcessor.Builder()
                    .add(new ResizeWithCropOrPadOp(minEdge, minEdge))
                    .add(new ResizeOp(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR));

            // Only apply normalization math if the model expects Floating Point decimals
            if (inputDataType == DataType.FLOAT32) {
                processorBuilder.add(new NormalizeOp(127.5f, 127.5f));
            }

            tensorImage = processorBuilder.build().process(tensorImage);

            // Create output buffer matching the exact model requirements
            TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, labels.size()}, outputDataType);

            // Run inference
            interpreter.run(tensorImage.getBuffer(), outputBuffer.getBuffer());

            // Get results
            float[] confidences = outputBuffer.getFloatArray();
            int maxPos = 0;
            float maxConfidence = -1.0f;

            for (int i = 0; i < confidences.length; i++) {
                Log.d("TFLiteDebug", "Label [" + labels.get(i) + "]: " + confidences[i]);
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }

            return labels.get(maxPos);

        } catch (Exception e) {
            Log.e("TFLiteDebug", "Crash during inference! Triggering Demo Fallback.", e);
            return "Unknown";
        }
    }

    public void close() {
        if (interpreter != null) interpreter.close();
    }
}