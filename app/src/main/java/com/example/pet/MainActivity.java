package com.example.pet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.pet.data.Pet;
import com.example.pet.databinding.ActivityMainBinding;
import com.example.pet.ui.behavior.BehaviorActivity;
import com.example.pet.ui.dashboard.DashboardViewModel;
import com.example.pet.ui.dashboard.PetPhotoLoader;
import com.example.pet.ui.dashboard.StatusPill;
import com.example.pet.ui.health.HealthActivity;
import com.example.pet.ui.insights.InsightsActivity;
import com.example.pet.ui.pets.AddPetActivity;
import com.example.pet.ui.pets.PetSelectorBottomSheet;

/**
 * Hosts the PetSense dashboard.
 * Preserves existing navigation and adds multi-pet selector support.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private DashboardViewModel viewModel;
    private final PetPhotoLoader photoLoader = new PetPhotoLoader();

    // Behavior banner is rendered from two LiveData values (time + text).
    private String bannerTime = "";
    private String bannerText = "";

    private final ActivityResultLauncher<Intent> addPetLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::onAddPetResult
            );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        setUpBottomNavigation();
        setUpClickListeners();
        observeViewModel();
    }

    /**
     * Single bottom-navigation listener. (The previous version registered two listeners; the second one
     * replaced the first, so this is the behaviour that was actually running, now without the dead copy.)
     */
    private void setUpBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                showDashboard();
                return true;
            }

            else if (id == R.id.nav_health) {
                Intent healthIntent = new Intent(MainActivity.this, HealthActivity.class);
                healthIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // 🔴 PREVENT BACK-STACK DUPLICATION
                startActivity(healthIntent);
                return true;
            }

            else if (id == R.id.nav_behavior) {
                Intent behaviorIntent = new Intent(MainActivity.this, BehaviorActivity.class);
                behaviorIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // 🔴 PREVENT BACK-STACK DUPLICATION

                Pet bPet = viewModel.getSelectedPet().getValue();
                if (bPet != null) {
                    behaviorIntent.putExtra("PET_ID", bPet.getId());
                    behaviorIntent.putExtra("PET_NAME", bPet.getName());
                    behaviorIntent.putExtra("PET_BREED", bPet.getBreed());
                    behaviorIntent.putExtra("PET_AGE", bPet.getAge());
                }
                startActivity(behaviorIntent);
                return true;
            }

            else if (id == R.id.nav_insights) {
                Intent insightsIntent = new Intent(MainActivity.this, InsightsActivity.class);
                insightsIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // 🔴 PREVENT BACK-STACK DUPLICATION

                Pet iPet = viewModel.getSelectedPet().getValue();
                if (iPet != null) {
                    insightsIntent.putExtra("petId", iPet.getId());
                    insightsIntent.putExtra("petName", iPet.getName());
                    insightsIntent.putExtra("petDetails", iPet.breedAgeLine());
                }
                startActivity(insightsIntent);
                return true;
            }

            else if (id == R.id.nav_profile) {
                openPetProfile();
                return true;
            }
            return false;
        });
    }

    private void showDashboard() {
        binding.dashboardScroll.setVisibility(View.VISIBLE);
        binding.placeholderContainer.setVisibility(View.GONE);
    }

    private void showPlaceholder(String title) {
        binding.dashboardScroll.setVisibility(View.GONE);
        binding.placeholderContainer.setVisibility(View.VISIBLE);
        binding.tvPlaceholderTitle.setText(title);
    }

    /**
     * Called when FLAG_ACTIVITY_REORDER_TO_FRONT brings this Activity to the front instead of
     * creating a new instance. Store the new intent so onResume() can act on it.
     */
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // make getIntent() return the latest intent
    }

    @Override
    protected void onResume() {
        super.onResume();
        MenuItem home = binding.bottomNavigation.getMenu().findItem(R.id.nav_home);
        if (home != null) home.setChecked(true);

        if (viewModel == null) return;

        // If Profile screen sent back a specific petId (via FLAG_ACTIVITY_REORDER_TO_FRONT),
        // re-select that pet so the header and avatar stay in sync.
        Intent current = getIntent();
        if (current != null) {
            String petIdFromProfile = current.getStringExtra("selected_pet_id");
            if (petIdFromProfile != null) {
                viewModel.selectPetById(petIdFromProfile);
                // Clear the extra so a future plain onResume doesn't re-trigger unnecessarily.
                current.removeExtra("selected_pet_id");
            }
        }

        // Always refresh live metrics whenever the user returns to the Dashboard.
        viewModel.refreshCurrentPetActivity();

        // Re-bind avatar in case the photo changed while we were away.
        Pet currentPet = viewModel.getSelectedPet().getValue();
        if (currentPet != null) {
            photoLoader.bind(this, binding.ivPetSelectorAvatar, currentPet.getPhotoUrl());
        }
    }

    @Override
    protected void onDestroy() {
        photoLoader.shutdown();
        super.onDestroy();
    }

    private void setUpClickListeners() {
        // Pet name + dropdown caret -> pet selector bottom sheet.
        binding.cardPetSelector.setOnClickListener(v -> openPetSelectorSheet());

        // The avatar keeps the old selector behaviour: open the selected pet's profile.
        binding.ivPetSelectorAvatar.setOnClickListener(v -> openPetProfile());

        // Add-pet button.
        binding.btnAddPet.setOnClickListener(v -> openAddPet());

        // Add-activity shortcut: tap the behavior banner card.
        binding.cardBehaviorBanner.setOnClickListener(v -> {
            Pet selected = viewModel.getSelectedPet().getValue();
            if (selected != null && selected.getId() != null) {
                Intent intent = new Intent(MainActivity.this,
                        com.example.pet.ui.insights.AddActivityRecordActivity.class);
                intent.putExtra("petId", selected.getId());
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Please select a pet first",
                        Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnViewCareSchedule.setOnClickListener(v ->
                Toast.makeText(this, "TODO: open Care Schedule screen",
                        Toast.LENGTH_SHORT).show());

        // Hidden in the redesign (the reference has no menu icon); listener kept so wiring is unchanged.
        binding.btnMenu.setOnClickListener(v ->
                Toast.makeText(this, "TODO: open navigation drawer / menu",
                        Toast.LENGTH_SHORT).show());
    }

    private final ActivityResultLauncher<Intent> petProfileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            // Pet was updated! The simplest way is to extract it and update
                            Intent data = result.getData();
                            Pet updated = new Pet(
                                    data.getStringExtra(AddPetActivity.EXTRA_PET_NAME),
                                    data.getStringExtra(AddPetActivity.EXTRA_PET_SPECIES),
                                    data.getStringExtra(AddPetActivity.EXTRA_PET_BREED),
                                    data.getStringExtra(AddPetActivity.EXTRA_PET_AGE),
                                    data.getStringExtra(AddPetActivity.EXTRA_PET_NOTES),
                                    true,
                                    data.getStringExtra(AddPetActivity.EXTRA_PET_WEIGHT),
                                    data.getStringExtra(AddPetActivity.EXTRA_PET_PHOTO_URL)
                            );
                            updated.setId(data.getStringExtra("id"));
                            viewModel.updatePet(updated, () -> {});
                        }
                    }
            );

    private void openPetProfile() {
        Pet selected = viewModel.getSelectedPet().getValue();
        if (selected == null) return;

        Intent intent = new Intent(this, PetProfileActivity.class);
        intent.putExtra("id", selected.getId());
        intent.putExtra(AddPetActivity.EXTRA_PET_NAME, selected.getName());
        intent.putExtra(AddPetActivity.EXTRA_PET_SPECIES, selected.getSpecies());
        intent.putExtra(AddPetActivity.EXTRA_PET_BREED, selected.getBreed());
        intent.putExtra(AddPetActivity.EXTRA_PET_AGE, selected.getAge());
        intent.putExtra(AddPetActivity.EXTRA_PET_NOTES, selected.getNotes());
        intent.putExtra(AddPetActivity.EXTRA_PET_WEIGHT, selected.getWeight());
        intent.putExtra(AddPetActivity.EXTRA_PET_PHOTO_URL, selected.getPhotoUrl());

        petProfileLauncher.launch(intent);
    }

    private void openPetSelectorSheet() {
        PetSelectorBottomSheet sheet = PetSelectorBottomSheet.newInstance();
        sheet.setOnAddPetRequestedListener(this::openAddPet);
        sheet.show(getSupportFragmentManager(), "pet_selector");
    }

    private void openAddPet() {
        addPetLauncher.launch(new Intent(this, AddPetActivity.class));
    }

    private void onAddPetResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK
                || result.getData() == null) {
            return;
        }

        Intent data = result.getData();

        String name = data.getStringExtra(AddPetActivity.EXTRA_PET_NAME);
        String species = data.getStringExtra(AddPetActivity.EXTRA_PET_SPECIES);
        String breed = data.getStringExtra(AddPetActivity.EXTRA_PET_BREED);
        String age = data.getStringExtra(AddPetActivity.EXTRA_PET_AGE);
        String notes = data.getStringExtra(AddPetActivity.EXTRA_PET_NOTES);

        String weight = data.getStringExtra(AddPetActivity.EXTRA_PET_WEIGHT);
        String photoUrl = data.getStringExtra(AddPetActivity.EXTRA_PET_PHOTO_URL);

        Pet newPet = new Pet(name, species, breed, age, notes, true, weight, photoUrl);

        viewModel.addPet(newPet, () ->
                Toast.makeText(
                        this,
                        name + " added",
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    private void observeViewModel() {
        viewModel.getUserName().observe(this, name ->
                binding.tvGreeting.setText(
                        getString(R.string.ps_dashboard_greeting, name)
                )
        );

        viewModel.getPetName().observe(this, petName -> {
            binding.tvGreetingSubtitle.setText(
                    getString(R.string.ps_dashboard_subtitle, petName)
            );
            binding.tvPetSelectorName.setText(petName);
            binding.ivPetSelectorAvatar.setContentDescription(
                    getString(R.string.ps_cd_pet_card_photo_format, petName)
            );
        });

        viewModel.getPetBreedAge().observe(this, breedAge ->
                binding.tvPetSelectorMeta.setText(breedAge)
        );

        // Avatar photo for the selected pet (placeholder when the pet has none).
        viewModel.getSelectedPet().observe(this, pet ->
                photoLoader.bind(this, binding.ivPetSelectorAvatar,
                        pet != null ? pet.getPhotoUrl() : null)
        );

        // ----- Health score card -----
        viewModel.getHealthScore().observe(this, score -> {
            String status = viewModel.getHealthStatus().getValue();

            binding.healthScoreView.setScore(score, status);
            binding.tvHealthScoreValue.setText(String.valueOf(score));
        });

        viewModel.getHealthStatus().observe(this, status -> {
            binding.healthScoreView.setStatusText(status);
            StatusPill.apply(binding.tvHealthScoreStatus, status);
        });

        viewModel.getHealthScoreDelta().observe(this, this::renderHealthDelta);

        viewModel.getHealthInsight().observe(this, text ->
                binding.tvAiInsightText.setText(text));

        viewModel.getLastUpdatedMillis().observe(this, millis -> {
            if (millis == null) {
                binding.tvLastUpdated.setVisibility(View.GONE);
                return;
            }
            binding.tvLastUpdated.setVisibility(View.VISIBLE);
            binding.tvLastUpdated.setText(formatUpdated(millis));
        });

        // ----- Behavior banner -----
        viewModel.getBehaviorBannerTime().observe(this, time -> {
            bannerTime = time != null ? time : "";
            renderBehaviorBanner();
        });
        viewModel.getBehaviorBannerText().observe(this, text -> {
            bannerText = text != null ? text : "";
            renderBehaviorBanner();
        });

        // ----- Metric tiles -----
        viewModel.getSleepValue().observe(this, binding.tvSleepValue::setText);
        viewModel.getActivityValue().observe(this, binding.tvActivityValue::setText);
        viewModel.getFeedingValue().observe(this, binding.tvFeedingValue::setText);
        viewModel.getWaterValue().observe(this, binding.tvWaterValue::setText);

        viewModel.getSleepStatus().observe(this, s -> StatusPill.apply(binding.chipSleepStatus, s));
        viewModel.getActivityStatus().observe(this, s -> StatusPill.apply(binding.chipActivityStatus, s));
        viewModel.getFeedingStatus().observe(this, s -> StatusPill.apply(binding.chipFeedingStatus, s));
        viewModel.getWaterStatus().observe(this, s -> StatusPill.apply(binding.chipWaterStatus, s));

        viewModel.getSleepSubtitle().observe(this, s -> binding.tvSleepStatus.setText(s));
        viewModel.getActivitySubtitle().observe(this, s -> binding.tvActivityStatus.setText(s));
        viewModel.getFeedingSubtitle().observe(this, s -> binding.tvFeedingStatus.setText(s));

        viewModel.getWaterPercent().observe(this, percent -> {
            int p = percent != null ? Math.max(0, Math.min(100, percent)) : 0;
            binding.progressWater.setProgressCompat(p, true);
            binding.tvWaterPercent.setText(getString(R.string.pd_percent_format, p));
            binding.tvWaterStatus.setText(getString(R.string.pd_water_percent_of_target, p));
        });

        viewModel.getCareReminderDetail().observe(
                this,
                binding.tvPreventiveDetail::setText
        );
    }

    private void renderHealthDelta(@Nullable Integer delta) {
        TextView tv = binding.tvHealthScoreDelta;
        if (delta == null) {
            tv.setVisibility(View.GONE);
            return;
        }
        tv.setVisibility(View.VISIBLE);
        if (delta > 0) {
            tv.setText(getString(R.string.pd_health_delta_up, delta));
            tv.setTextColor(getColor(R.color.pd_green_text));
        } else if (delta < 0) {
            tv.setText(getString(R.string.pd_health_delta_down, Math.abs(delta)));
            tv.setTextColor(getColor(R.color.pd_red_text));
        } else {
            tv.setText(R.string.pd_health_delta_same);
            tv.setTextColor(getColor(R.color.pd_gray_text));
        }
    }

    private void renderBehaviorBanner() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (!bannerTime.isEmpty()) {
            sb.append(bannerTime);
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            int dotStart = sb.length();
            sb.append("  \u00b7  ");
            sb.setSpan(new ForegroundColorSpan(getColor(R.color.pd_amber_icon)), dotStart, sb.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        sb.append(bannerText);
        binding.tvBehaviorBannerText.setText(sb);
    }

    private String formatUpdated(long millis) {
        long minutes = Math.max(0L, System.currentTimeMillis() - millis) / 60000L;
        if (minutes < 1) {
            return getString(R.string.pd_updated_just_now);
        } else if (minutes < 60) {
            return getString(R.string.pd_updated_minutes, (int) minutes);
        } else if (minutes < 24 * 60) {
            return getString(R.string.pd_updated_hours, (int) (minutes / 60));
        } else {
            return getString(R.string.pd_updated_days, (int) (minutes / (24 * 60)));
        }
    }
}


