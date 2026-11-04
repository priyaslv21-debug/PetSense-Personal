package com.example.pet.ui.community;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.pet.R;
import com.example.pet.data.Event;
import com.example.pet.data.EventRepository;
import com.example.pet.databinding.ActivityCreateEventBinding;
import com.example.pet.ui.dashboard.PetPhotoLoader;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Screen allowing users to create or edit a community pet event.
 */
public class CreateEventActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT = "extra_event";
    public static final String EXTRA_EVENT_ID = "extra_event_id";

    private ActivityCreateEventBinding binding;
    private EventRepository repository;
    private Event editingEvent = null;
    private Uri selectedPosterUri = null;

    private Long selectedEventDateMillis = null;
    private Long selectedDeadlineMillis = null;
    private String selectedStartTime = "";
    private String selectedEndTime = "";

    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedPosterUri = uri;
                    binding.ivPosterPreview.setImageURI(uri);
                    binding.layoutUploadPlaceholder.setVisibility(View.GONE);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new EventRepository();

        setupViews();
        setupCategoryDropdown();
        setupDateTimePickers();
        setupFeeToggle();

        checkForEditMode();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.cardPosterPicker.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        binding.btnSubmitEvent.setOnClickListener(v -> validateAndSubmit());

        // Prefill organizer from current user
        FirebaseUser user = repository.getCurrentUser();
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                binding.etOrganizerName.setText(user.getDisplayName());
            }
            if (user.getEmail() != null) {
                binding.etOrganizerContact.setText(user.getEmail());
            } else if (user.getPhoneNumber() != null) {
                binding.etOrganizerContact.setText(user.getPhoneNumber());
            }
        }
    }

    private void setupCategoryDropdown() {
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                Event.CATEGORIES
        );
        binding.actvCategory.setAdapter(categoryAdapter);
        binding.actvCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = Event.CATEGORIES[position];
            boolean isMedical = selected.toLowerCase().contains("vaccin") || selected.toLowerCase().contains("health");
            binding.layoutMedicalNotice.setVisibility(isMedical ? View.VISIBLE : View.GONE);
        });
    }

    private void setupDateTimePickers() {
        // Event Date Picker
        binding.etEventDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            if (selectedEventDateMillis != null) {
                c.setTimeInMillis(selectedEventDateMillis);
            }
            DatePickerDialog dpd = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar chosen = Calendar.getInstance();
                        chosen.set(year, month, dayOfMonth, 0, 0, 0);
                        selectedEventDateMillis = chosen.getTimeInMillis();
                        binding.etEventDate.setText(displayDateFormat.format(new Date(selectedEventDateMillis)));

                        // Default registration deadline to 1 day before event if not set
                        if (selectedDeadlineMillis == null || selectedDeadlineMillis > selectedEventDateMillis) {
                            selectedDeadlineMillis = selectedEventDateMillis - (24 * 3600 * 1000L);
                            if (selectedDeadlineMillis < System.currentTimeMillis()) {
                                selectedDeadlineMillis = selectedEventDateMillis;
                            }
                            binding.etDeadline.setText(displayDateFormat.format(new Date(selectedDeadlineMillis)));
                        }
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            );
            dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dpd.show();
        });

        // Start Time Picker
        binding.etStartTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(
                    this,
                    (view, hourOfDay, minute) -> {
                        Calendar time = Calendar.getInstance();
                        time.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        time.set(Calendar.MINUTE, minute);
                        selectedStartTime = timeFormat.format(time.getTime());
                        binding.etStartTime.setText(selectedStartTime);
                    },
                    c.get(Calendar.HOUR_OF_DAY),
                    c.get(Calendar.MINUTE),
                    false
            ).show();
        });

        // End Time Picker
        binding.etEndTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(
                    this,
                    (view, hourOfDay, minute) -> {
                        Calendar time = Calendar.getInstance();
                        time.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        time.set(Calendar.MINUTE, minute);
                        selectedEndTime = timeFormat.format(time.getTime());
                        binding.etEndTime.setText(selectedEndTime);
                    },
                    c.get(Calendar.HOUR_OF_DAY) + 2,
                    c.get(Calendar.MINUTE),
                    false
            ).show();
        });

        // Registration Deadline Picker
        binding.etDeadline.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            if (selectedDeadlineMillis != null) {
                c.setTimeInMillis(selectedDeadlineMillis);
            }
            DatePickerDialog dpd = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar chosen = Calendar.getInstance();
                        chosen.set(year, month, dayOfMonth, 23, 59, 59);
                        selectedDeadlineMillis = chosen.getTimeInMillis();
                        binding.etDeadline.setText(displayDateFormat.format(new Date(selectedDeadlineMillis)));
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            );
            dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            if (selectedEventDateMillis != null) {
                dpd.getDatePicker().setMaxDate(selectedEventDateMillis + (24 * 3600 * 1000L));
            }
            dpd.show();
        });
    }

    private void setupFeeToggle() {
        binding.rgEventFee.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbPaid) {
                binding.tilFeeAmount.setVisibility(View.VISIBLE);
            } else {
                binding.tilFeeAmount.setVisibility(View.GONE);
                binding.etFeeAmount.setText("");
            }
        });
    }

    private void checkForEditMode() {
        if (getIntent() != null) {
            if (getIntent().hasExtra(EXTRA_EVENT)) {
                editingEvent = (Event) getIntent().getSerializableExtra(EXTRA_EVENT);
                populateEditingEvent();
            } else if (getIntent().hasExtra(EXTRA_EVENT_ID)) {
                String id = getIntent().getStringExtra(EXTRA_EVENT_ID);
                if (id != null) {
                    repository.getEvent(id, new EventRepository.EventCallback<Event>() {
                        @Override
                        public void onSuccess(Event event) {
                            editingEvent = event;
                            populateEditingEvent();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(CreateEventActivity.this, "Could not load event for editing", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }
    }

    private void populateEditingEvent() {
        if (editingEvent == null) return;
        binding.tvCreateHeaderTitle.setText("Edit Event");
        binding.btnSubmitEvent.setText("Save Changes");

        binding.etTitle.setText(editingEvent.getTitle());
        binding.actvCategory.setText(editingEvent.getCategory(), false);
        binding.etDescription.setText(editingEvent.getDescription());

        if (editingEvent.getEventDate() != null) {
            selectedEventDateMillis = editingEvent.getEventDate();
            binding.etEventDate.setText(displayDateFormat.format(new Date(selectedEventDateMillis)));
        }

        selectedStartTime = editingEvent.getStartTime() != null ? editingEvent.getStartTime() : "";
        binding.etStartTime.setText(selectedStartTime);

        selectedEndTime = editingEvent.getEndTime() != null ? editingEvent.getEndTime() : "";
        binding.etEndTime.setText(selectedEndTime);

        binding.etVenue.setText(editingEvent.getVenue());
        binding.etAddress.setText(editingEvent.getAddress());
        binding.etCity.setText(editingEvent.getCity());

        binding.etCapacity.setText(String.valueOf(editingEvent.getMaximumParticipants()));

        if (editingEvent.getRegistrationDeadline() != null) {
            selectedDeadlineMillis = editingEvent.getRegistrationDeadline();
            binding.etDeadline.setText(displayDateFormat.format(new Date(selectedDeadlineMillis)));
        }

        String fee = editingEvent.getEventFee();
        if (fee != null && !fee.trim().isEmpty() && !"Free".equalsIgnoreCase(fee.trim())) {
            binding.rbPaid.setChecked(true);
            binding.tilFeeAmount.setVisibility(View.VISIBLE);
            binding.etFeeAmount.setText(fee);
        } else {
            binding.rbFree.setChecked(true);
            binding.tilFeeAmount.setVisibility(View.GONE);
        }

        binding.etOrganizerName.setText(editingEvent.getOrganizerName());
        binding.etOrganizerContact.setText(editingEvent.getOrganizerContact());

        if (editingEvent.getImageUrl() != null && !editingEvent.getImageUrl().trim().isEmpty()) {
            new PetPhotoLoader().bind(this, binding.ivPosterPreview, editingEvent.getImageUrl());
            binding.layoutUploadPlaceholder.setVisibility(View.GONE);
        }
    }

    private void validateAndSubmit() {
        String title = binding.etTitle.getText() != null ? binding.etTitle.getText().toString().trim() : "";
        String category = binding.actvCategory.getText() != null ? binding.actvCategory.getText().toString().trim() : "";
        String description = binding.etDescription.getText() != null ? binding.etDescription.getText().toString().trim() : "";
        String venue = binding.etVenue.getText() != null ? binding.etVenue.getText().toString().trim() : "";
        String address = binding.etAddress.getText() != null ? binding.etAddress.getText().toString().trim() : "";
        String city = binding.etCity.getText() != null ? binding.etCity.getText().toString().trim() : "";
        String capacityStr = binding.etCapacity.getText() != null ? binding.etCapacity.getText().toString().trim() : "0";
        String organizerName = binding.etOrganizerName.getText() != null ? binding.etOrganizerName.getText().toString().trim() : "";
        String organizerContact = binding.etOrganizerContact.getText() != null ? binding.etOrganizerContact.getText().toString().trim() : "";

        if (title.isEmpty()) {
            binding.tilTitle.setError("Event title is required");
            return;
        } else binding.tilTitle.setError(null);

        if (category.isEmpty()) {
            binding.tilCategory.setError("Please select a category");
            return;
        } else binding.tilCategory.setError(null);

        if (description.isEmpty()) {
            binding.tilDescription.setError("Event description is required");
            return;
        } else binding.tilDescription.setError(null);

        if (selectedEventDateMillis == null) {
            binding.tilEventDate.setError("Please select the event date");
            return;
        } else binding.tilEventDate.setError(null);

        if (selectedStartTime.isEmpty()) {
            binding.tilStartTime.setError("Start time is required");
            return;
        } else binding.tilStartTime.setError(null);

        if (selectedEndTime.isEmpty()) {
            binding.tilEndTime.setError("End time is required");
            return;
        } else binding.tilEndTime.setError(null);

        if (venue.isEmpty()) {
            binding.tilVenue.setError("Venue name is required");
            return;
        } else binding.tilVenue.setError(null);

        if (address.isEmpty()) {
            binding.tilAddress.setError("Street address is required");
            return;
        } else binding.tilAddress.setError(null);

        if (city.isEmpty()) {
            binding.tilCity.setError("City is required");
            return;
        } else binding.tilCity.setError(null);

        int capacity = 0;
        try {
            capacity = Integer.parseInt(capacityStr);
        } catch (NumberFormatException ignored) {}

        if (selectedDeadlineMillis == null) {
            selectedDeadlineMillis = selectedEventDateMillis;
        }

        String eventFee = "Free";
        if (binding.rbPaid.isChecked()) {
            String feeVal = binding.etFeeAmount.getText() != null ? binding.etFeeAmount.getText().toString().trim() : "";
            if (feeVal.isEmpty()) {
                binding.tilFeeAmount.setError("Please specify the event fee amount");
                return;
            }
            binding.tilFeeAmount.setError(null);
            eventFee = feeVal;
        }

        if (organizerName.isEmpty()) {
            binding.tilOrganizerName.setError("Organizer name is required");
            return;
        } else binding.tilOrganizerName.setError(null);

        if (organizerContact.isEmpty()) {
            binding.tilOrganizerContact.setError("Organizer contact info is required");
            return;
        } else binding.tilOrganizerContact.setError(null);

        setSubmitting(true);

        if (editingEvent != null) {
            // Update existing event
            editingEvent.setTitle(title);
            editingEvent.setCategory(category);
            editingEvent.setDescription(description);
            editingEvent.setEventDate(selectedEventDateMillis);
            editingEvent.setStartTime(selectedStartTime);
            editingEvent.setEndTime(selectedEndTime);
            editingEvent.setVenue(venue);
            editingEvent.setAddress(address);
            editingEvent.setCity(city);
            editingEvent.setMaximumParticipants(capacity);
            editingEvent.setRegistrationDeadline(selectedDeadlineMillis);
            editingEvent.setEventFee(eventFee);
            editingEvent.setOrganizerName(organizerName);
            editingEvent.setOrganizerContact(organizerContact);

            repository.updateEvent(this, editingEvent, selectedPosterUri, new EventRepository.EventCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    setSubmitting(false);
                    Toast.makeText(CreateEventActivity.this, "Event updated successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(Exception e) {
                    setSubmitting(false);
                    Toast.makeText(CreateEventActivity.this, "Failed to update event: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // Create new event
            Event newEvent = new Event(
                    null, title, category, description,
                    null, organizerName, organizerContact,
                    selectedEventDateMillis, selectedStartTime, selectedEndTime,
                    venue, address, city,
                    eventFee, capacity, selectedDeadlineMillis
            );

            repository.createEvent(this, newEvent, selectedPosterUri, new EventRepository.EventCallback<Event>() {
                @Override
                public void onSuccess(Event result) {
                    setSubmitting(false);
                    Toast.makeText(CreateEventActivity.this, "Event published successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }

                @Override
                public void onError(Exception e) {
                    setSubmitting(false);
                    Toast.makeText(CreateEventActivity.this, "Failed to publish event: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void setSubmitting(boolean isSubmitting) {
        binding.createProgressBar.setVisibility(isSubmitting ? View.VISIBLE : View.GONE);
        binding.btnSubmitEvent.setEnabled(!isSubmitting);
        binding.btnSubmitEvent.setText(isSubmitting ? "Publishing..." : (editingEvent != null ? "Save Changes" : "Publish Event"));
    }
}
