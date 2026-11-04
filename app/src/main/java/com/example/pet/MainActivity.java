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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pet.data.Event;
import com.example.pet.data.EventRepository;
import com.example.pet.data.Pet;
import com.example.pet.databinding.ActivityMainBinding;
import com.example.pet.ui.behavior.BehaviorActivity;
import com.example.pet.ui.community.CreateEventActivity;
import com.example.pet.ui.community.EventActivity;
import com.example.pet.ui.community.EventAdapter;
import com.example.pet.ui.community.EventDetailsActivity;
import com.example.pet.ui.community.MyEventsActivity;
import com.example.pet.ui.dashboard.DashboardViewModel;
import com.example.pet.ui.dashboard.PetPhotoLoader;
import com.example.pet.ui.dashboard.StatusPill;
import com.example.pet.ui.health.HealthActivity;
import com.example.pet.ui.insights.InsightsActivity;
import com.example.pet.ui.pets.AddPetActivity;
import com.example.pet.ui.pets.PetSelectorBottomSheet;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Hosts the PetSense dashboard.
 * Preserves existing navigation, supports multi-pet selector, and handles empty pet states.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private DashboardViewModel viewModel;
    private final PetPhotoLoader photoLoader = new PetPhotoLoader();

    // Behavior banner is rendered from two LiveData values (time + text).
    private String bannerTime = "";
    private String bannerText = "";

    private EventRepository eventRepository;
    private EventAdapter homeEventAdapter;

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
                healthIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(healthIntent);
                return true;
            }

            else if (id == R.id.nav_behavior) {
                Intent behaviorIntent = new Intent(MainActivity.this, BehaviorActivity.class);
                behaviorIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

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
                insightsIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

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
        if (viewModel.getSelectedPet().getValue() == null) {
            binding.emptyPetContainer.setVisibility(View.VISIBLE);
            binding.dashboardScroll.setVisibility(View.GONE);
            binding.placeholderContainer.setVisibility(View.GONE);
        } else {
            binding.emptyPetContainer.setVisibility(View.GONE);
            binding.dashboardScroll.setVisibility(View.VISIBLE);
            binding.placeholderContainer.setVisibility(View.GONE);
        }
    }

    private void showPlaceholder(String title) {
        binding.dashboardScroll.setVisibility(View.GONE);
        binding.emptyPetContainer.setVisibility(View.GONE);
        binding.placeholderContainer.setVisibility(View.VISIBLE);
        binding.tvPlaceholderTitle.setText(title);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MenuItem home = binding.bottomNavigation.getMenu().findItem(R.id.nav_home);
        if (home != null) home.setChecked(true);

        if (viewModel == null) return;

        // If Onboarding or Profile screen sent a specific petId, select that pet
        Intent current = getIntent();
        if (current != null) {
            String petIdFromIntent = current.getStringExtra("selected_pet_id");
            if (petIdFromIntent != null) {
                viewModel.selectPetById(petIdFromIntent);
                current.removeExtra("selected_pet_id");
            }
        }

        // Always refresh live metrics whenever returning to Dashboard
        viewModel.refreshCurrentPetActivity();

        // Re-bind avatar in case the photo changed while away
        Pet currentPet = viewModel.getSelectedPet().getValue();
        if (currentPet != null) {
            photoLoader.bind(this, binding.ivPetSelectorAvatar, currentPet.getPhotoUrl());
        }

        loadHomeUpcomingEvents();
    }

    @Override
    protected void onDestroy() {
        photoLoader.shutdown();
        super.onDestroy();
    }

    private void setUpClickListeners() {
        // Pet name + dropdown caret -> pet selector bottom sheet
        binding.cardPetSelector.setOnClickListener(v -> openPetSelectorSheet());

        // The avatar opens the selected pet's profile
        binding.ivPetSelectorAvatar.setOnClickListener(v -> openPetProfile());

        // Add-pet button in toolbar
        binding.btnAddPet.setOnClickListener(v -> openAddPet());

        // Add-pet button in empty state
        binding.btnEmptyAddPet.setOnClickListener(v -> openAddPet());

        // Add-activity shortcut: tap the behavior banner card
        binding.cardBehaviorBanner.setOnClickListener(v -> {
            Pet selected = viewModel.getSelectedPet().getValue();
            if (selected != null && selected.getId() != null) {
                Intent intent = new Intent(MainActivity.this,
                        com.example.pet.ui.insights.AddActivityRecordActivity.class);
                intent.putExtra("petId", selected.getId());
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Please select or add a pet first",
                        Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnViewCareSchedule.setOnClickListener(v -> {
            Pet selected = viewModel.getSelectedPet().getValue();
            Intent labIntent = new Intent(this, com.example.pet.ui.lab.LabHistoryActivity.class);
            if (selected != null) {
                labIntent.putExtra(com.example.pet.ui.lab.LabHistoryActivity.EXTRA_PET_ID, selected.getId());
                labIntent.putExtra(com.example.pet.ui.lab.LabHistoryActivity.EXTRA_PET_NAME, selected.getName());
                labIntent.putExtra(com.example.pet.ui.lab.LabHistoryActivity.EXTRA_PET_SPECIES, selected.getSpecies());
            }
            startActivity(labIntent);
        });

        // Menu button -> provides Log Out option
        binding.btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, "Log Out");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Log Out")
                            .setMessage("Are you sure you want to log out of PetSense?")
                            .setPositiveButton("Log Out", (dialog, which) -> {
                                AuthRepository authRepo = new AuthRepository();
                                authRepo.logout();
                                Intent intent = new Intent(this, LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                }
                return false;
            });
            popup.show();
        });

        setUpCommunityEventsSection();
    }

    private void setUpCommunityEventsSection() {
        eventRepository = new EventRepository();

        binding.btnHomeViewAllEvents.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, EventActivity.class);
            startActivity(intent);
        });

        binding.btnHomeCreateEvent.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreateEventActivity.class);
            startActivity(intent);
        });

        binding.btnHomeMyEvents.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MyEventsActivity.class);
            startActivity(intent);
        });

        // Category shortcuts
        binding.chipHomeVaccination.setOnClickListener(v -> openCategory(Event.CATEGORY_VACCINATION));
        binding.chipHomeMeetups.setOnClickListener(v -> openCategory(Event.CATEGORY_MEETUP));
        binding.chipHomeWorkshops.setOnClickListener(v -> openCategory(Event.CATEGORY_TRAINING));
        binding.chipHomeHealth.setOnClickListener(v -> openCategory(Event.CATEGORY_HEALTH_CHECKUP));
        binding.chipHomeAdoption.setOnClickListener(v -> openCategory(Event.CATEGORY_ADOPTION));
        binding.chipHomeAwareness.setOnClickListener(v -> openCategory(Event.CATEGORY_AWARENESS));

        // Horizontal Carousel
        homeEventAdapter = new EventAdapter(true, event -> {
            Intent intent = new Intent(MainActivity.this, EventDetailsActivity.class);
            intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
            startActivity(intent);
        });
        binding.rvHomeUpcomingEvents.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvHomeUpcomingEvents.setAdapter(homeEventAdapter);
    }

    private void openCategory(String category) {
        Intent intent = new Intent(MainActivity.this, EventActivity.class);
        intent.putExtra(EventActivity.EXTRA_INITIAL_CATEGORY, category);
        startActivity(intent);
    }

    private void loadHomeUpcomingEvents() {
        if (eventRepository == null) {
            eventRepository = new EventRepository();
        }
        binding.pbHomeEventsLoading.setVisibility(View.VISIBLE);
        eventRepository.getUpcomingEvents(new EventRepository.EventCallback<java.util.List<Event>>() {
            @Override
            public void onSuccess(java.util.List<Event> events) {
                binding.pbHomeEventsLoading.setVisibility(View.GONE);
                if (events == null || events.isEmpty()) {
                    binding.rvHomeUpcomingEvents.setVisibility(View.GONE);
                    binding.layoutHomeEventsEmpty.setVisibility(View.VISIBLE);
                } else {
                    binding.layoutHomeEventsEmpty.setVisibility(View.GONE);
                    binding.rvHomeUpcomingEvents.setVisibility(View.VISIBLE);
                    homeEventAdapter.setEvents(events);
                }
            }

            @Override
            public void onError(Exception e) {
                binding.pbHomeEventsLoading.setVisibility(View.GONE);
                binding.rvHomeUpcomingEvents.setVisibility(View.GONE);
                binding.layoutHomeEventsEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private final ActivityResultLauncher<Intent> petProfileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
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
        if (selected == null) {
            Toast.makeText(this, "Please add a pet first", Toast.LENGTH_SHORT).show();
            return;
        }

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
        String petId = data.getStringExtra("id");

        Pet newPet = new Pet(name, species, breed, age, notes, true, weight, photoUrl);

        if (petId != null && !petId.isEmpty()) {
            newPet.setId(petId);
            // Already saved to Firestore in AddPetActivity, update local state directly
            viewModel.addPetLocallyOnly(newPet);
            Toast.makeText(this, name + " added", Toast.LENGTH_SHORT).show();
        } else {
            // Fallback for legacy flows
            viewModel.addPet(newPet, () ->
                    Toast.makeText(
                            this,
                            name + " added",
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }
    }

    private void observeViewModel() {
        viewModel.getUserName().observe(this, name ->
                binding.tvGreeting.setText(
                        getString(R.string.ps_dashboard_greeting, name)
                )
        );

        viewModel.getPetName().observe(this, petName -> {
            if (petName != null && !petName.isEmpty()) {
                binding.tvGreetingSubtitle.setText(
                        getString(R.string.ps_dashboard_subtitle, petName)
                );
                binding.tvPetSelectorName.setText(petName);
                binding.ivPetSelectorAvatar.setContentDescription(
                        getString(R.string.ps_cd_pet_card_photo_format, petName)
                );
            } else {
                binding.tvGreetingSubtitle.setText("Welcome to PetSense");
                binding.tvPetSelectorName.setText("No Pet Selected");
            }
        });

        viewModel.getPetBreedAge().observe(this, breedAge ->
                binding.tvPetSelectorMeta.setText(breedAge != null ? breedAge : "")
        );

        // Avatar photo and visibility toggle for the selected pet
        viewModel.getSelectedPet().observe(this, pet -> {
            if (pet == null) {
                binding.emptyPetContainer.setVisibility(View.VISIBLE);
                binding.dashboardScroll.setVisibility(View.GONE);
                binding.placeholderContainer.setVisibility(View.GONE);
            } else {
                binding.emptyPetContainer.setVisibility(View.GONE);
                if (binding.placeholderContainer.getVisibility() != View.VISIBLE) {
                    binding.dashboardScroll.setVisibility(View.VISIBLE);
                }
                photoLoader.bind(this, binding.ivPetSelectorAvatar, pet.getPhotoUrl());
            }
        });

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
