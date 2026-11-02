package com.example.pet.ui.behavior;

import androidx.recyclerview.widget.LinearLayoutManager;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.pet.MainActivity;
import com.example.pet.R;
import com.example.pet.databinding.BehaviorBinding;
import com.example.pet.ui.health.HealthActivity;
import com.example.pet.ui.insights.InsightsActivity;
import com.google.common.util.concurrent.ListenableFuture;

public class BehaviorActivity extends AppCompatActivity {

    private BehaviorBinding binding;
    private BehaviorViewModel viewModel;
    private BehaviorHistoryAdapter adapter;
    private ProgressDialog progressDialog;

    private static final int CAMERA_PERMISSION_CODE = 100;
    private boolean isCameraActive = false;

    // Launcher for selecting either Image or Video from gallery
    private ActivityResultLauncher<Intent> mediaPickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = BehaviorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(BehaviorViewModel.class);

        // Initialize TFLite Classifier
        viewModel.initClassifier(this);

        // ProgressDialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Analyzing pet behavior and mood...");
        progressDialog.setCancelable(false);

        // Catch intent pet context data
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("PET_NAME")) {
            String pId = intent.getStringExtra("PET_ID");
            String pName = intent.getStringExtra("PET_NAME");
            String pBreed = intent.getStringExtra("PET_BREED");
            String pAge = intent.getStringExtra("PET_AGE");
            viewModel.initPetData(pId, pName, pBreed, pAge);
        }

        setupMediaPicker();
        setupViews();
        setupBottomNavigation();
        observeViewModel();

        viewModel.loadInitialData();
    }

    private void setupMediaPicker() {
        mediaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedUri = result.getData().getData();
                        if (selectedUri != null) {
                            processSelectedMedia(selectedUri);
                        }
                    }
                }
        );
    }

    private void processSelectedMedia(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null && mimeType.startsWith("video")) {
            extractFrameAndAnalyze(uri);
        } else {
            // It is an image photo from gallery
            try {
                Bitmap bitmap;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri));
                } else {
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                }

                if (bitmap != null) {
                    if (isCameraActive) resetCameraUI();
                    // Convert to software bitmap if needed
                    Bitmap copyBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    viewModel.startRealAnalysis(copyBitmap);
                }
            } catch (Exception e) {
                Log.e("MediaPicker", "Failed to decode image", e);
                Toast.makeText(this, "Could not load selected photo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnMore.setOnClickListener(v ->
                Toast.makeText(this, "More options", Toast.LENGTH_SHORT).show());

        // CAMERA BUTTON LOGIC
        binding.btnStartCamera.setOnClickListener(v -> {
            if (!isCameraActive) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                }
            } else {
                Bitmap frame = binding.viewFinder.getBitmap();
                if (frame != null) {
                    viewModel.startRealAnalysis(frame);
                } else {
                    Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // IMAGE AND VIDEO UPLOAD BUTTON LOGIC
        binding.btnUploadVideo.setText("Upload Photo / Video");
        binding.btnUploadVideo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            String[] mimeTypes = {"image/*", "video/*"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            mediaPickerLauncher.launch(intent);
        });

        binding.btnResetAnalysis.setOnClickListener(v -> {
            viewModel.resetAnalysis();
            resetCameraUI();
        });

        adapter = new BehaviorHistoryAdapter();
        binding.rvRecentAnalyses.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRecentAnalyses.setAdapter(adapter);
    }

    private void extractFrameAndAnalyze(Uri videoUri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, videoUri);
            Bitmap frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            retriever.release();

            if (frame != null) {
                if (isCameraActive) resetCameraUI();
                viewModel.startRealAnalysis(frame);
            } else {
                Toast.makeText(this, "Could not extract frame from video", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("VideoAnalysis", "Error reading video", e);
            Toast.makeText(this, "Error reading video file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);

                binding.cameraPlaceholder.setVisibility(View.GONE);
                binding.viewFinder.setVisibility(View.VISIBLE);
                binding.btnStartCamera.setText("Capture & Analyze");
                isCameraActive = true;
            } catch (Exception e) {
                Log.e("CameraX", "Binding failed", e);
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void resetCameraUI() {
        isCameraActive = false;
        binding.viewFinder.setVisibility(View.GONE);
        binding.cameraPlaceholder.setVisibility(View.VISIBLE);
        binding.btnStartCamera.setText("Start Camera Analysis");
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_behavior);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            } else if (id == R.id.nav_health) {
                startActivity(new Intent(this, HealthActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_behavior) {
                return true;
            } else if (id == R.id.nav_insights) {
                startActivity(new Intent(this, InsightsActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void observeViewModel() {
        viewModel.getPetName().observe(this, name -> {
            binding.tvPetContextName.setText(name);
            binding.tvBehaviorMainTitle.setText("Understand " + name + " better");
            binding.tvProgressTitle.setText("Analyzing " + name + "...");
            binding.tvActionSuggestionTitle.setText("Suggested for " + name);
        });

        viewModel.getPetBreedAge().observe(this, binding.tvPetContextDetails::setText);

        viewModel.getCurrentBehavior().observe(this, binding.tvResultBehavior::setText);
        viewModel.getCurrentEmoji().observe(this, binding.tvResultEmoji::setText);
        viewModel.getCurrentConfidence().observe(this, binding.tvResultConfidence::setText);
        viewModel.getMotion().observe(this, binding.tvIndicatorMotion::setText);
        viewModel.getPosture().observe(this, binding.tvIndicatorPosture::setText);
        viewModel.getEnergy().observe(this, binding.tvIndicatorEnergy::setText);
        viewModel.getAiInsight().observe(this, text -> {
            binding.tvAiInsight.setText(text);
            binding.tvResultExplanation.setText(text);
        });
        viewModel.getActionSuggestion().observe(this, binding.tvActionSuggestionDesc::setText);

        viewModel.getState().observe(this, state -> {
            if (state == BehaviorViewModel.AnalysisState.ANALYZING) {
                progressDialog.show();
            } else {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }

            binding.layoutInputState.setVisibility(state == BehaviorViewModel.AnalysisState.IDLE ? View.VISIBLE : View.GONE);
            binding.layoutProgressState.setVisibility(state == BehaviorViewModel.AnalysisState.ANALYZING ? View.VISIBLE : View.GONE);
            binding.layoutResultState.setVisibility(state == BehaviorViewModel.AnalysisState.RESULT ? View.VISIBLE : View.GONE);

            binding.behaviorScroll.smoothScrollTo(0, 0);
        });

        viewModel.getHistory().observe(this, history -> {
            if (history == null || history.isEmpty()) {
                binding.layoutHistoryEmpty.setVisibility(View.VISIBLE);
                binding.rvRecentAnalyses.setVisibility(View.GONE);
            } else {
                binding.layoutHistoryEmpty.setVisibility(View.GONE);
                binding.rvRecentAnalyses.setVisibility(View.VISIBLE);
                adapter.setItems(history);
            }
        });
    }
}