package com.example.pet.ui.community;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pet.R;
import com.example.pet.data.Event;
import com.example.pet.data.EventRegistration;
import com.example.pet.data.EventRepository;
import com.example.pet.data.Pet;
import com.example.pet.data.PetRepository;
import com.example.pet.databinding.ActivityEventRegistrationBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for registering an authenticated user and their pet for a community event.
 * Handles input validation, pet pre-filling, and concurrency-safe registration.
 */
public class EventRegistrationActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT = "extra_event";
    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private ActivityEventRegistrationBinding binding;
    private EventRepository eventRepository;
    private PetRepository petRepository;
    private Event currentEvent;
    private String eventId;

    private List<Pet> userPets = new ArrayList<>();
    private String selectedPetId = null;

    private static final String[] SPECIES_OPTIONS = new String[]{
            "Dog", "Cat", "Bird", "Rabbit", "Fish", "Other"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        eventRepository = new EventRepository();
        petRepository = new PetRepository();

        if (getIntent() != null) {
            eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
            if (getIntent().hasExtra(EXTRA_EVENT)) {
                currentEvent = (Event) getIntent().getSerializableExtra(EXTRA_EVENT);
                if (currentEvent != null) {
                    eventId = currentEvent.getEventId();
                }
            }
        }

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Event not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupViews();

        if (currentEvent != null) {
            populateEventCard(currentEvent);
        } else {
            loadEvent();
        }

        loadUserAndPetProfiles();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Species dropdown
        ArrayAdapter<String> speciesAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, SPECIES_OPTIONS);
        binding.actvPetSpecies.setAdapter(speciesAdapter);

        binding.btnSubmitRegistration.setOnClickListener(v -> submitRegistration());
    }

    private void loadEvent() {
        eventRepository.getEvent(eventId, new EventRepository.EventCallback<Event>() {
            @Override
            public void onSuccess(Event event) {
                currentEvent = event;
                populateEventCard(event);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(EventRegistrationActivity.this, "Failed to load event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void populateEventCard(@NonNull Event event) {
        binding.tvRegEventTitle.setText(event.getTitle() != null ? event.getTitle() : "Event");
        binding.tvRegEventCategory.setText(event.getCategory() != null ? event.getCategory() : "Community");

        if (event.isFree()) {
            binding.tvRegEventFee.setText("Free");
            binding.tvRegEventFee.setTextColor(getColor(R.color.pd_accent_green));
        } else {
            binding.tvRegEventFee.setText(event.getEventFee() != null ? event.getEventFee() : "Paid");
            binding.tvRegEventFee.setTextColor(getColor(R.color.pd_primary));
        }

        String dateTimeStr = "";
        if (event.getFormattedDate() != null) {
            dateTimeStr = "📅 " + event.getFormattedDate();
            if (event.getStartTime() != null) {
                dateTimeStr += " • " + event.getStartTime();
                if (event.getEndTime() != null) {
                    dateTimeStr += " - " + event.getEndTime();
                }
            }
        }
        binding.tvRegEventDateTime.setText(dateTimeStr);

        String venueStr = "📍 " + (event.getVenue() != null ? event.getVenue() : "Venue TBD");
        if (event.getCity() != null) {
            venueStr += ", " + event.getCity();
        }
        binding.tvRegEventVenue.setText(venueStr);
    }

    private void loadUserAndPetProfiles() {
        FirebaseUser currentUser = eventRepository.getCurrentUser();
        if (currentUser != null) {
            if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().trim().isEmpty()) {
                binding.etParticipantName.setText(currentUser.getDisplayName());
            }
            if (currentUser.getEmail() != null) {
                binding.etParticipantEmail.setText(currentUser.getEmail());
            }
            if (currentUser.getPhoneNumber() != null && !currentUser.getPhoneNumber().trim().isEmpty()) {
                binding.etParticipantPhone.setText(currentUser.getPhoneNumber());
            }
        }

        // Load pets from PetRepository
        try {
            petRepository.loadPets(new PetRepository.PetsCallback() {
                @Override
                public void onSuccess(List<Pet> pets) {
                    userPets = pets != null ? pets : new ArrayList<>();
                    setupPetPickerDropdown();
                }

                @Override
                public void onError(Exception e) {
                    // Fallback to manual entry silently
                    setupPetPickerDropdown();
                }
            });
        } catch (Exception e) {
            setupPetPickerDropdown();
        }
    }

    private void setupPetPickerDropdown() {
        List<String> pickerOptions = new ArrayList<>();
        pickerOptions.add("-- Select from your pets --");

        for (Pet p : userPets) {
            String label = p.getName();
            if (p.getSpecies() != null) {
                label += " (" + p.getSpecies() + ")";
            }
            pickerOptions.add(label);
        }
        pickerOptions.add("✍️ Enter Pet Details Manually");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, pickerOptions);
        binding.actvPetPicker.setAdapter(adapter);

        // If user has exactly one pet, pre-select it
        if (userPets.size() == 1) {
            selectPet(userPets.get(0));
            binding.actvPetPicker.setText(pickerOptions.get(1), false);
        }

        binding.actvPetPicker.setOnItemClickListener((parent, view, position, id) -> {
            if (position > 0 && position <= userPets.size()) {
                Pet chosen = userPets.get(position - 1);
                selectPet(chosen);
            } else {
                // Clear or manual
                selectedPetId = null;
                binding.etPetName.setText("");
                binding.actvPetSpecies.setText("", false);
                binding.etPetAge.setText("");
            }
        });
    }

    private void selectPet(Pet pet) {
        if (pet == null) return;
        selectedPetId = pet.getId();
        binding.etPetName.setText(pet.getName() != null ? pet.getName() : "");
        binding.actvPetSpecies.setText(pet.getSpecies() != null ? pet.getSpecies() : "", false);
        binding.etPetAge.setText(pet.getAge() != null ? pet.getAge() : "");
    }

    private void submitRegistration() {
        if (currentEvent == null) {
            Toast.makeText(this, "Event information is still loading.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = binding.etParticipantName.getText() != null ? binding.etParticipantName.getText().toString().trim() : "";
        String email = binding.etParticipantEmail.getText() != null ? binding.etParticipantEmail.getText().toString().trim() : "";
        String phone = binding.etParticipantPhone.getText() != null ? binding.etParticipantPhone.getText().toString().trim() : "";
        String petName = binding.etPetName.getText() != null ? binding.etPetName.getText().toString().trim() : "";
        String petSpecies = binding.actvPetSpecies.getText() != null ? binding.actvPetSpecies.getText().toString().trim() : "";
        String petAge = binding.etPetAge.getText() != null ? binding.etPetAge.getText().toString().trim() : "";
        String notes = binding.etAdditionalNotes.getText() != null ? binding.etAdditionalNotes.getText().toString().trim() : "";

        // Validations
        if (TextUtils.isEmpty(name)) {
            binding.etParticipantName.setError("Participant full name is required");
            binding.etParticipantName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etParticipantEmail.setError("A valid email address is required");
            binding.etParticipantEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone) || phone.length() < 7) {
            binding.etParticipantPhone.setError("Please provide a valid contact phone number");
            binding.etParticipantPhone.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(petName)) {
            binding.etPetName.setError("Pet name is required");
            binding.etPetName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(petSpecies)) {
            binding.actvPetSpecies.setError("Please select pet species");
            binding.actvPetSpecies.requestFocus();
            return;
        }

        // Show loading state
        setLoadingState(true);

        FirebaseUser currentUser = eventRepository.getCurrentUser();
        String userId = currentUser != null ? currentUser.getUid() : "";

        EventRegistration registration = new EventRegistration(
                currentEvent.getEventId(),
                userId,
                name,
                email,
                phone,
                selectedPetId,
                petName,
                petSpecies,
                petAge,
                notes,
                currentEvent.getTitle(),
                currentEvent.getEventDate(),
                currentEvent.getVenue(),
                currentEvent.getCity(),
                currentEvent.getEventFee()
        );

        eventRepository.registerForEvent(registration, new EventRepository.EventCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoadingState(false);
                // Trigger local notification
                EventNotificationHelper.notifyRegistrationSuccess(
                        EventRegistrationActivity.this, registration, currentEvent);

                showRegistrationSuccessDialog(registration);
            }

            @Override
            public void onError(Exception e) {
                setLoadingState(false);
                String message = e.getMessage() != null ? e.getMessage() : "Registration failed. Please try again.";
                new MaterialAlertDialogBuilder(EventRegistrationActivity.this)
                        .setTitle("Registration Could Not Complete")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private void showRegistrationSuccessDialog(EventRegistration reg) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("🎉 Registration Confirmed!")
                .setMessage("You and " + reg.getPetName() + " are registered for \"" + currentEvent.getTitle() + "\"!\n\n" +
                        "📅 " + (currentEvent.getFormattedDate() != null ? currentEvent.getFormattedDate() : "") + "\n" +
                        "📍 " + (currentEvent.getVenue() != null ? currentEvent.getVenue() : "") + "\n\n" +
                        "You can manage your registration anytime in 'My Events'.")
                .setCancelable(false)
                .setPositiveButton("View In My Events", (dialog, which) -> {
                    finish();
                })
                .show();
    }

    private void setLoadingState(boolean isLoading) {
        binding.btnSubmitRegistration.setEnabled(!isLoading);
        binding.btnSubmitRegistration.setText(isLoading ? "" : "Confirm Registration");
        binding.pbRegistering.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}
