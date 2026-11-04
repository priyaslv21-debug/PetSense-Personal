package com.example.pet.ui.pets;

import android.app.Activity;
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
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pet.LoginActivity;
import com.example.pet.MainActivity;
import com.example.pet.R;
import com.example.pet.AuthRepository;
import com.example.pet.data.Pet;
import com.example.pet.data.PetRepository;
import com.example.pet.databinding.ActivityAddPetBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for registering or editing a pet profile.
 *
 * Supports two distinct modes:
 * 1. Onboarding Mode (EXTRA_IS_ONBOARDING = true):
 *    Mandatory initial step after user registration.
 *    Persists the pet directly to Cloud Firestore under users/{uid}/pets/{petId}.
 *    Navigates to MainActivity only after successful persistence.
 *
 * 2. Standard Mode (EXTRA_IS_ONBOARDING = false):
 *    Invoked from Dashboard or PetProfileActivity to add or update an existing pet.
 *    Persists the pet to Firestore and returns the result to the caller.
 */
public class AddPetActivity extends AppCompatActivity {

    private static final String TAG = "AddPetActivity";
    private static final int PHOTO_MAX_PX = 1024;

    public static final String EXTRA_PET_NAME = "extra_pet_name";
    public static final String EXTRA_PET_SPECIES = "extra_pet_species";
    public static final String EXTRA_PET_BREED = "extra_pet_breed";
    public static final String EXTRA_PET_AGE = "extra_pet_age";
    public static final String EXTRA_PET_GENDER = "extra_pet_gender";
    public static final String EXTRA_PET_NOTES = "extra_pet_notes";
    public static final String EXTRA_PET_WEIGHT = "extra_pet_weight";
    public static final String EXTRA_PET_PHOTO_URL = "extra_pet_photo_url";
    public static final String EXTRA_IS_ONBOARDING = "extra_is_onboarding";
    public static final String EXTRA_PET_ID = "id";

    private ActivityAddPetBinding binding;
    private final PetRepository petRepository = new PetRepository();
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    private boolean isOnboarding = false;
    private String existingPetId = null;
    private String selectedPhotoUriString = null;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::onImagePicked
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddPetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isOnboarding = getIntent().getBooleanExtra(EXTRA_IS_ONBOARDING, false);

        setUpDropdowns();
        configureModeView();
        setupClickListeners();
        preFillData();
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdown();
        super.onDestroy();
    }

    private void configureModeView() {
        if (isOnboarding) {
            binding.tvHeaderTitle.setText(R.string.ps_onboarding_pet_title);
            binding.tvHeaderSubtitle.setText(R.string.ps_onboarding_pet_subtitle);
            binding.tvHeaderSubtitle.setVisibility(View.VISIBLE);
            binding.btnSavePet.setText(R.string.ps_create_pet_profile);
            binding.btnAddPetBack.setVisibility(View.GONE);

            // Handle back press during onboarding by offering Logout
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    new MaterialAlertDialogBuilder(AddPetActivity.this)
                            .setTitle("Exit Pet Registration?")
                            .setMessage("A pet profile is required to set up your dashboard. Do you want to log out and exit?")
                            .setPositiveButton("Log Out", (dialog, which) -> {
                                new AuthRepository().logout();
                                Intent loginIntent = new Intent(AddPetActivity.this, LoginActivity.class);
                                loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(loginIntent);
                                finish();
                            })
                            .setNegativeButton("Continue Setup", null)
                            .show();
                }
            });
        } else {
            binding.tvHeaderTitle.setText(R.string.ps_add_pet_title);
            binding.tvHeaderSubtitle.setVisibility(View.GONE);
            binding.btnSavePet.setText(R.string.ps_save_pet);
            binding.btnAddPetBack.setVisibility(View.VISIBLE);
        }
    }

    private void setUpDropdowns() {
        // Species options
        String[] speciesOptions = getResources().getStringArray(R.array.ps_species_options);
        ArrayAdapter<String> speciesAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, speciesOptions);
        binding.inputSpecies.setAdapter(speciesAdapter);

        // Gender options
        String[] genderOptions = getResources().getStringArray(R.array.ps_gender_options);
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, genderOptions);
        binding.inputGender.setAdapter(genderAdapter);
    }

    private void setupClickListeners() {
        binding.btnAddPetBack.setOnClickListener(v -> finish());
        binding.btnSavePet.setOnClickListener(v -> onSaveClicked());

        View.OnClickListener photoPickListener = v -> imagePickerLauncher.launch("image/*");
        binding.cardPhotoPicker.setOnClickListener(photoPickListener);
        binding.imgPetPhoto.setOnClickListener(photoPickListener);
        binding.btnChangePhoto.setOnClickListener(photoPickListener);
    }

    private void preFillData() {
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_PET_ID)) {
                existingPetId = intent.getStringExtra(EXTRA_PET_ID);
                if (!isOnboarding) {
                    binding.tvHeaderTitle.setText("Edit Pet Profile");
                }
            }

            if (intent.hasExtra(EXTRA_PET_NAME)) {
                binding.inputName.setText(intent.getStringExtra(EXTRA_PET_NAME));
            }
            if (intent.hasExtra(EXTRA_PET_SPECIES)) {
                binding.inputSpecies.setText(intent.getStringExtra(EXTRA_PET_SPECIES), false);
            }
            if (intent.hasExtra(EXTRA_PET_BREED)) {
                binding.inputBreed.setText(intent.getStringExtra(EXTRA_PET_BREED));
            }
            if (intent.hasExtra(EXTRA_PET_AGE)) {
                binding.inputAge.setText(intent.getStringExtra(EXTRA_PET_AGE));
            }
            if (intent.hasExtra(EXTRA_PET_GENDER)) {
                binding.inputGender.setText(intent.getStringExtra(EXTRA_PET_GENDER), false);
            }
            if (intent.hasExtra(EXTRA_PET_WEIGHT)) {
                binding.inputWeight.setText(intent.getStringExtra(EXTRA_PET_WEIGHT));
            }
            if (intent.hasExtra(EXTRA_PET_NOTES)) {
                binding.inputNotes.setText(intent.getStringExtra(EXTRA_PET_NOTES));
            }
            if (intent.hasExtra(EXTRA_PET_PHOTO_URL)) {
                selectedPhotoUriString = intent.getStringExtra(EXTRA_PET_PHOTO_URL);
                if (selectedPhotoUriString != null && !selectedPhotoUriString.isEmpty()) {
                    showPhotoAsync(Uri.parse(selectedPhotoUriString));
                    binding.btnChangePhoto.setText(R.string.ps_change_photo);
                }
            }
        }
    }

    private void onImagePicked(@Nullable Uri uri) {
        if (uri == null) return;

        imageExecutor.execute(() -> {
            try {
                File saved = copyToPrivateStorage(uri);
                Uri savedUri = Uri.fromFile(saved);
                Bitmap bitmap = decodeScaled(savedUri, PHOTO_MAX_PX);
                if (bitmap == null) throw new IOException("Could not decode picked image");

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    binding.imgPetPhoto.setImageBitmap(bitmap);
                    binding.btnChangePhoto.setText(R.string.ps_change_photo);
                    selectedPhotoUriString = savedUri.toString();
                });
            } catch (Exception | OutOfMemoryError e) {
                Log.e(TAG, "Failed to load picked image", e);
                runOnUiThread(() -> Toast.makeText(this, "Could not load selected photo.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private File copyToPrivateStorage(@NonNull Uri source) throws IOException {
        File dir = new File(getFilesDir(), "pets/photos");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create private photos directory");
        }
        File out = new File(dir, "pet_avatar_" + System.currentTimeMillis() + ".jpg");

        try (InputStream in = getContentResolver().openInputStream(source);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("Cannot open selected image stream");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }
        return out;
    }

    private Bitmap decodeScaled(@NonNull Uri uri, int maxPx) throws IOException {
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

    private int exifRotation(@NonNull Uri uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException ignored) {
            return 0;
        }
    }

    private void showPhotoAsync(@NonNull Uri uri) {
        imageExecutor.execute(() -> {
            try {
                Bitmap bitmap = decodeScaled(uri, PHOTO_MAX_PX);
                if (bitmap != null) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            binding.imgPetPhoto.setImageBitmap(bitmap);
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void onSaveClicked() {
        String name = textOf(binding.inputName);
        String species = textOf(binding.inputSpecies);
        String breed = textOf(binding.inputBreed);
        String gender = textOf(binding.inputGender);
        String age = textOf(binding.inputAge);
        String weight = textOf(binding.inputWeight);
        String notes = textOf(binding.inputNotes);

        boolean valid = true;

        if (name.isEmpty()) {
            binding.inputNameLayout.setError(getString(R.string.ps_error_required));
            valid = false;
        } else {
            binding.inputNameLayout.setError(null);
        }

        if (species.isEmpty()) {
            binding.inputSpeciesLayout.setError("Select pet species");
            valid = false;
        } else {
            binding.inputSpeciesLayout.setError(null);
        }

        if (breed.isEmpty()) {
            binding.inputBreedLayout.setError("Enter pet breed");
            valid = false;
        } else {
            binding.inputBreedLayout.setError(null);
        }

        if (gender.isEmpty()) {
            binding.inputGenderLayout.setError("Select pet gender");
            valid = false;
        } else {
            binding.inputGenderLayout.setError(null);
        }

        if (age.isEmpty()) {
            binding.inputAgeLayout.setError(getString(R.string.ps_error_required));
            valid = false;
        } else {
            binding.inputAgeLayout.setError(null);
        }

        if (weight.isEmpty()) {
            binding.inputWeightLayout.setError(getString(R.string.ps_error_required));
            valid = false;
        } else {
            binding.inputWeightLayout.setError(null);
        }

        if (!valid) {
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Cannot save pet profile: No signed-in Firebase user detected.");
            Toast.makeText(this, "Authentication required. Please sign in again.", Toast.LENGTH_LONG).show();
            Intent loginIntent = new Intent(this, LoginActivity.class);
            loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(loginIntent);
            finish();
            return;
        }

        String authUid = currentUser.getUid();
        Log.i(TAG, "Authenticated Firebase UID: " + authUid + " (" + currentUser.getEmail() + ") - proceeding with pet save");

        boolean isMale = !"Female".equalsIgnoreCase(gender);
        Pet pet = new Pet(name, species, breed, age, notes, isMale, weight, selectedPhotoUriString);
        pet.setGender(gender);

        if (existingPetId != null) {
            pet.setId(existingPetId);
        }

        setLoadingState(true);

        if (existingPetId != null && !existingPetId.trim().isEmpty()) {
            petRepository.updatePet(pet, new PetRepository.SavePetCallback() {
                @Override
                public void onSuccess(Pet savedPet) {
                    handleSaveSuccess(savedPet);
                }

                @Override
                public void onError(Exception e) {
                    handleSaveError(e);
                }
            });
        } else {
            petRepository.addPet(pet, new PetRepository.SavePetCallback() {
                @Override
                public void onSuccess(Pet savedPet) {
                    handleSaveSuccess(savedPet);
                }

                @Override
                public void onError(Exception e) {
                    handleSaveError(e);
                }
            });
        }
    }

    private void handleSaveSuccess(Pet savedPet) {
        setLoadingState(false);
        Toast.makeText(this, savedPet.getName() + " profile saved successfully!", Toast.LENGTH_SHORT).show();

        if (isOnboarding) {
            Intent mainIntent = new Intent(AddPetActivity.this, MainActivity.class);
            mainIntent.putExtra("selected_pet_id", savedPet.getId());
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(mainIntent);
            finish();
        } else {
            Intent result = new Intent();
            result.putExtra(EXTRA_PET_ID, savedPet.getId());
            result.putExtra(EXTRA_PET_NAME, savedPet.getName());
            result.putExtra(EXTRA_PET_SPECIES, savedPet.getSpecies());
            result.putExtra(EXTRA_PET_BREED, savedPet.getBreed());
            result.putExtra(EXTRA_PET_AGE, savedPet.getAge());
            result.putExtra(EXTRA_PET_GENDER, savedPet.getGender());
            result.putExtra(EXTRA_PET_NOTES, savedPet.getNotes());
            result.putExtra(EXTRA_PET_WEIGHT, savedPet.getWeight());
            result.putExtra(EXTRA_PET_PHOTO_URL, savedPet.getPhotoUrl());
            setResult(Activity.RESULT_OK, result);
            finish();
        }
    }

    private void handleSaveError(Exception e) {
        setLoadingState(false);
        Log.e(TAG, "Failed to save pet profile in Firestore", e);
        Toast.makeText(this,
                "Failed to save pet profile: " + e.getMessage() + ". Please check your connection and tap again to retry.",
                Toast.LENGTH_LONG).show();
    }

    private void setLoadingState(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSavePet.setEnabled(!isLoading);
        if (isLoading) {
            binding.btnSavePet.setText(isOnboarding ? "Creating Profile..." : "Saving Pet...");
        } else {
            binding.btnSavePet.setText(isOnboarding ? R.string.ps_create_pet_profile : R.string.ps_save_pet);
        }
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }
}
