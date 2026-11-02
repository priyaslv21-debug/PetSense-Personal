package com.example.pet;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.pet.databinding.ActivityPetProfileBinding;
import com.example.pet.ui.behavior.BehaviorActivity;
import com.example.pet.ui.health.HealthActivity;
import com.example.pet.ui.insights.InsightsActivity;
import com.example.pet.ui.pets.AddPetActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PetProfileActivity — Pet Profile Screen
 *
 * Changes in this version:
 *  - Picked images are copied to app-private storage and decoded down-sampled
 *    (no full-resolution decode on the UI thread, no expired content:// URIs).
 *  - Every image operation is wrapped so a bad/huge/unavailable image can never crash the app.
 *  - Bottom navigation passes the same pet extras that MainActivity passes.
 *  - Back handling uses OnBackPressedCallback (works with gesture back too).
 */
public class PetProfileActivity extends AppCompatActivity {

    private static final String TAG = "PetProfileActivity";
    private static final int PHOTO_MAX_PX = 1024;

    private ActivityPetProfileBinding binding;

    // Tracks whether the "About" section is expanded
    private boolean isAboutExpanded = false;

    // Image copy/decode runs off the main thread
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> editPetLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    // Update our own intent so when we finish, we send it back to MainActivity
                    if (data.getExtras() != null) {
                        getIntent().putExtras(data.getExtras());
                    }
                    loadPetData();
                }
            });

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::onImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge so the pastel header extends under the status bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityPetProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Light status bar icons (dark icons on pastel background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && getWindow().getInsetsController() != null) {
            getWindow().getInsetsController()
                    .setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        }

        // Apply window insets so the header top accounts for the status bar height
        ViewCompat.setOnApplyWindowInsetsListener(binding.frameHeader, (view, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            view.setPadding(0, statusBarHeight, 0, 0);
            return insets;
        });

        // System back / gesture back -> return updated data to MainActivity
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithResult();
            }
        });

        loadPetData();
        setupClickListeners();
        setupBottomNavigation();
    }

    private void loadPetData() {
        Intent intent = getIntent();
        if (intent == null) return;

        String name = intent.getStringExtra(AddPetActivity.EXTRA_PET_NAME);
        String breed = intent.getStringExtra(AddPetActivity.EXTRA_PET_BREED);
        String age = intent.getStringExtra(AddPetActivity.EXTRA_PET_AGE);
        String weight = intent.getStringExtra(AddPetActivity.EXTRA_PET_WEIGHT);
        String notes = intent.getStringExtra(AddPetActivity.EXTRA_PET_NOTES);
        String photoUrl = intent.getStringExtra(AddPetActivity.EXTRA_PET_PHOTO_URL);

        binding.tvPetName.setText(name != null ? name : "Unknown");
        binding.tvBreed.setText(breed != null ? breed : "");
        binding.tvAgeValue.setText(age != null ? age : "--");
        binding.tvWeightValue.setText(weight != null ? weight : "--");
        binding.tvAboutText.setText(notes != null && !notes.isEmpty() ? notes : "No details provided.");

        if (photoUrl != null && !photoUrl.isEmpty()) {
            showPhotoAsync(Uri.parse(photoUrl));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Pet photo: pick -> copy to private storage -> decode -> show
    // ─────────────────────────────────────────────────────────────

    /** Called with the picked URI, or null if the user cancelled. */
    private void onImagePicked(Uri uri) {
        if (uri == null) return; // cancelled: keep current image

        imageExecutor.execute(() -> {
            try {
                File saved = copyToPrivateStorage(uri);
                Uri savedUri = Uri.fromFile(saved);
                Bitmap bitmap = decodeScaled(savedUri, PHOTO_MAX_PX);
                if (bitmap == null) throw new IOException("Could not decode image");

                runOnUiThread(() -> {
                    if (binding == null || isFinishing()) return;
                    binding.imgPet.setImageBitmap(bitmap);
                    getIntent().putExtra(AddPetActivity.EXTRA_PET_PHOTO_URL, savedUri.toString());
                });
            } catch (Exception | OutOfMemoryError e) {
                Log.e(TAG, "Failed to load picked image", e);
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(this, "Couldn't load that image", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /** Displays an already-saved photo URI; on any failure keeps the placeholder. */
    private void showPhotoAsync(Uri uri) {
        imageExecutor.execute(() -> {
            try {
                Bitmap bitmap = decodeScaled(uri, PHOTO_MAX_PX);
                if (bitmap == null) return;
                runOnUiThread(() -> {
                    if (binding == null || isFinishing()) return;
                    binding.imgPet.setImageBitmap(bitmap);
                });
            } catch (Exception | OutOfMemoryError e) {
                Log.w(TAG, "Could not show saved photo, keeping placeholder", e);
            }
        });
    }

    private File copyToPrivateStorage(Uri source) throws IOException {
        File dir = new File(getFilesDir(), "pet_photos");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create photo directory");
        }
        File out = new File(dir, "pet_" + System.currentTimeMillis() + ".img");

        try (InputStream in = getContentResolver().openInputStream(source);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("Cannot open selected image");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
        return out;
    }

    /** Decodes a down-sampled bitmap (long side not much larger than maxPx) and applies EXIF rotation. */
    private Bitmap decodeScaled(Uri uri, int maxPx) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) {
            sample *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) return null;

        int rotation = exifRotation(uri);
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            bitmap = rotated;
        }
        return bitmap;
    }

    private int exifRotation(Uri uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            int orientation = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Click Listeners
    // ─────────────────────────────────────────────────────────────

    private void setupClickListeners() {

        // Back button — return updated data to MainActivity
        binding.btnBack.setOnClickListener(v -> finishWithResult());

        binding.imgPet.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        binding.imgEditPhoto.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // More / options — placeholder for future popup menu
        binding.btnMore.setOnClickListener(v -> {
            // TODO: show PopupMenu with options (Share profile, Settings, etc.)
        });

        // "Read more" toggle for the About section
        binding.tvReadMore.setOnClickListener(v -> toggleAboutExpanded());

        // Edit Pet Profile — launch AddPetActivity with current data
        binding.btnEditProfile.setOnClickListener(v -> {
            Intent editIntent = new Intent(this, AddPetActivity.class);
            if (getIntent() != null) {
                editIntent.putExtras(getIntent());
            }
            editPetLauncher.launch(editIntent);
        });

        // Manage Health Records — secondary CTA
        binding.btnManageHealth.setOnClickListener(v -> {
            // TODO: navigate to HealthRecordsActivity
        });
    }

    /** Sends the (possibly updated) pet data back to MainActivity and closes this screen. */
    private void finishWithResult() {
        if (getIntent() != null) {
            setResult(RESULT_OK, getIntent());
        }
        finish();
    }

    /**
     * Toggles the "About Buddy" text between collapsed (3 lines) and expanded (all lines).
     */
    private void toggleAboutExpanded() {
        isAboutExpanded = !isAboutExpanded;
        if (isAboutExpanded) {
            binding.tvAboutText.setMaxLines(Integer.MAX_VALUE);
            binding.tvReadMore.setText(getString(R.string.read_more));
            binding.tvReadMore.setVisibility(View.GONE);
        } else {
            binding.tvAboutText.setMaxLines(3);
            binding.tvReadMore.setVisibility(View.VISIBLE);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Bottom Navigation
    // ─────────────────────────────────────────────────────────────

    private void setupBottomNavigation() {
        binding.bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_profile) {
                return true;
            }
            if (itemId == R.id.nav_home) {
                // Embed the currently-viewed pet's ID so MainActivity.onResume()
                // can call selectPetById() after FLAG_ACTIVITY_REORDER_TO_FRONT.
                Intent src = getIntent();
                if (src != null) {
                    String petId = src.getStringExtra("id");
                    if (petId != null) {
                        src.putExtra("selected_pet_id", petId);
                    }
                }
                finishWithResult();
                return true;
            }

            Intent target = buildNavIntent(itemId);
            if (target == null) return false;

            // 🔴 PREVENT DUPLICATE SCREENS IN BACK-STACK
            target.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            finishWithResult();
            startActivity(target);
            return true;
        });
    }

    /** Builds the same intent (and extras) that MainActivity uses for each tab. */
    private Intent buildNavIntent(int itemId) {
        Intent src = getIntent();
        String id = src != null ? src.getStringExtra("id") : null;
        String name = src != null ? src.getStringExtra(AddPetActivity.EXTRA_PET_NAME) : null;
        String breed = src != null ? src.getStringExtra(AddPetActivity.EXTRA_PET_BREED) : null;
        String age = src != null ? src.getStringExtra(AddPetActivity.EXTRA_PET_AGE) : null;

        if (itemId == R.id.nav_health) {
            return new Intent(this, HealthActivity.class);
        }
        if (itemId == R.id.nav_behavior) {
            Intent intent = new Intent(this, BehaviorActivity.class);
            if (id != null) {
                intent.putExtra("PET_ID", id);
                intent.putExtra("PET_NAME", name);
                intent.putExtra("PET_BREED", breed);
                intent.putExtra("PET_AGE", age);
            }
            return intent;
        }
        if (itemId == R.id.nav_insights) {
            Intent intent = new Intent(this, InsightsActivity.class);
            if (id != null) {
                String details = (breed != null ? breed : "")
                        + (breed != null && age != null ? " · " : "")
                        + (age != null ? age : "");
                intent.putExtra("petId", id);
                intent.putExtra("petName", name);
                intent.putExtra("petDetails", details);
            }
            return intent;
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        imageExecutor.shutdown();
        // Release View Binding reference to avoid memory leaks
        binding = null;
    }
}