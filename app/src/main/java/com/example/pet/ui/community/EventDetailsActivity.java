package com.example.pet.ui.community;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.pet.R;
import com.example.pet.data.Event;
import com.example.pet.data.EventRegistration;
import com.example.pet.data.EventReport;
import com.example.pet.data.EventRepository;
import com.example.pet.databinding.ActivityEventDetailsBinding;
import com.example.pet.ui.dashboard.PetPhotoLoader;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

/**
 * Screen presenting complete details for a community event:
 * - Schedule, venue, organizer credentials and verification status.
 * - Concurrency-safe registration option.
 * - Report event mechanism for moderation.
 * - Creator controls (edit, cancel, view participant list).
 */
public class EventDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";
    public static final String EXTRA_EVENT = "extra_event";

    private ActivityEventDetailsBinding binding;
    private EventRepository repository;
    private String eventId;
    private Event currentEvent;
    private EventRegistration userRegistration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new EventRepository();

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
        loadEventDetails();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnShareEvent.setOnClickListener(v -> shareEvent());
        binding.btnReportEvent.setOnClickListener(v -> showReportEventDialog());

        binding.btnRegisterNow.setOnClickListener(v -> {
            if (currentEvent != null) {
                Intent intent = new Intent(EventDetailsActivity.this, EventRegistrationActivity.class);
                intent.putExtra(EventRegistrationActivity.EXTRA_EVENT, currentEvent);
                startActivity(intent);
            }
        });

        binding.btnCancelMyRegistration.setOnClickListener(v -> confirmCancelRegistration());

        binding.btnOpenMap.setOnClickListener(v -> {
            if (currentEvent != null) {
                String query = (currentEvent.getVenue() != null ? currentEvent.getVenue() : "") + " "
                        + (currentEvent.getAddress() != null ? currentEvent.getAddress() : "") + " "
                        + (currentEvent.getCity() != null ? currentEvent.getCity() : "");
                Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(query.trim()));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    Intent genericMap = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query.trim())));
                    startActivity(genericMap);
                }
            }
        });

        // Organizer controls
        binding.btnOrganizerViewParticipants.setOnClickListener(v -> {
            if (currentEvent != null) {
                Intent intent = new Intent(EventDetailsActivity.this, EventParticipantsActivity.class);
                intent.putExtra(EventParticipantsActivity.EXTRA_EVENT_ID, currentEvent.getEventId());
                intent.putExtra(EventParticipantsActivity.EXTRA_EVENT_TITLE, currentEvent.getTitle());
                startActivity(intent);
            }
        });

        binding.btnOrganizerEdit.setOnClickListener(v -> {
            if (currentEvent != null) {
                Intent intent = new Intent(EventDetailsActivity.this, CreateEventActivity.class);
                intent.putExtra(CreateEventActivity.EXTRA_EVENT, currentEvent);
                startActivity(intent);
            }
        });

        binding.btnOrganizerCancel.setOnClickListener(v -> confirmCancelEvent());
    }

    private void loadEventDetails() {
        repository.getEvent(eventId, new EventRepository.EventCallback<Event>() {
            @Override
            public void onSuccess(Event event) {
                currentEvent = event;
                renderEventDetails(event);
                checkUserStatus(event);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(EventDetailsActivity.this, "Failed to load event details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderEventDetails(Event event) {
        binding.tvDetailTitle.setText(event.getTitle());
        binding.tvDetailCategory.setText(event.getCategory() != null ? event.getCategory() : "Community");

        // Fee
        String fee = event.getEventFee();
        if (fee == null || fee.trim().isEmpty() || "Free".equalsIgnoreCase(fee.trim())) {
            binding.tvDetailFee.setText("Free Event");
            binding.tvDetailFee.setTextColor(ContextCompat.getColor(this, R.color.pd_green_text));
            binding.tvDetailFee.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pd_green_bg));
        } else {
            binding.tvDetailFee.setText("Fee: " + fee);
            binding.tvDetailFee.setTextColor(ContextCompat.getColor(this, R.color.pd_amber_text));
            binding.tvDetailFee.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.pd_amber_bg));
        }

        // Status Overlay
        if (Event.STATUS_CANCELLED.equalsIgnoreCase(event.getEventStatus())) {
            binding.tvDetailStatusOverlay.setText("Cancelled");
            binding.tvDetailStatusOverlay.setVisibility(View.VISIBLE);
        } else if (event.isPastEvent()) {
            binding.tvDetailStatusOverlay.setText("Completed");
            binding.tvDetailStatusOverlay.setVisibility(View.VISIBLE);
        } else {
            binding.tvDetailStatusOverlay.setVisibility(View.GONE);
        }

        // Poster
        new PetPhotoLoader().bind(this, binding.ivDetailPoster, event.getImageUrl());

        // Organizer & Verification
        binding.tvDetailOrganizerName.setText(event.getOrganizerName() != null ? event.getOrganizerName() : "Community Organizer");
        binding.tvDetailOrganizerContact.setText(event.getOrganizerContact() != null ? "📞 " + event.getOrganizerContact() : "Contact information available upon request");

        if (event.isVerifiedOrganizer()) {
            binding.tvDetailVerifiedStatus.setText("Verified Pet Provider");
            binding.tvDetailVerifiedStatus.setTextColor(ContextCompat.getColor(this, R.color.pd_primary));
            binding.ivDetailVerifiedIcon.setVisibility(View.VISIBLE);
        } else {
            binding.tvDetailVerifiedStatus.setText("Community Organizer");
            binding.tvDetailVerifiedStatus.setTextColor(ContextCompat.getColor(this, R.color.pd_text_muted));
            binding.ivDetailVerifiedIcon.setVisibility(View.GONE);
        }

        // Medical Notice
        binding.layoutDetailMedicalNotice.setVisibility(event.isMedicalOrVaccineEvent() ? View.VISIBLE : View.GONE);

        // Date & Time
        binding.tvDetailDate.setText(event.getFormattedDate() != null ? event.getFormattedDate() : "Date to be announced");
        String timeStr = "";
        if (event.getStartTime() != null) timeStr += event.getStartTime();
        if (event.getEndTime() != null) timeStr += " – " + event.getEndTime();
        binding.tvDetailTime.setText(!timeStr.isEmpty() ? timeStr : "Time to be confirmed");

        // Deadline
        if (event.getRegistrationDeadline() != null && event.getRegistrationDeadline() > 0) {
            binding.tvDetailDeadline.setText("⏳ Registration closes on " + event.getFormattedDeadline());
            binding.tvDetailDeadline.setVisibility(View.VISIBLE);
        } else {
            binding.tvDetailDeadline.setVisibility(View.GONE);
        }

        // Venue & Address
        binding.tvDetailVenue.setText(event.getVenue() != null ? event.getVenue() : "Venue TBD");
        String fullAddress = (event.getAddress() != null ? event.getAddress() : "") +
                (event.getCity() != null ? ", " + event.getCity() : "");
        binding.tvDetailAddress.setText(!fullAddress.trim().isEmpty() ? fullAddress.trim() : "Address details provided by organizer");

        // Capacity Progress
        int regCount = event.getRegistrationCount();
        int maxCap = event.getMaximumParticipants();
        if (maxCap > 0) {
            int remaining = Math.max(0, maxCap - regCount);
            binding.tvDetailSeatsStatus.setText(regCount + " / " + maxCap + " Registered (" + remaining + " seats left)");
            int progress = (int) (((float) regCount / maxCap) * 100);
            binding.pbCapacity.setProgress(Math.min(progress, 100));
        } else {
            binding.tvDetailSeatsStatus.setText(regCount + " Registered (Open capacity)");
            binding.pbCapacity.setProgress(100);
        }

        // Description
        binding.tvDetailDescription.setText(event.getDescription() != null ? event.getDescription() : "No detailed description provided.");
    }

    private void checkUserStatus(Event event) {
        FirebaseUser user = repository.getCurrentUser();
        if (user == null) {
            binding.btnRegisterNow.setVisibility(View.VISIBLE);
            binding.layoutAlreadyRegistered.setVisibility(View.GONE);
            binding.layoutOrganizerActions.setVisibility(View.GONE);
            return;
        }

        // Check if user is the organizer
        if (user.getUid().equals(event.getOrganizerId())) {
            binding.btnRegisterNow.setVisibility(View.GONE);
            binding.layoutAlreadyRegistered.setVisibility(View.GONE);
            binding.layoutOrganizerActions.setVisibility(View.VISIBLE);
            return;
        }

        // User is regular participant: check registration status
        binding.layoutOrganizerActions.setVisibility(View.GONE);
        repository.checkUserRegistration(event.getEventId(), user.getUid(), new EventRepository.EventCallback<EventRegistration>() {
            @Override
            public void onSuccess(EventRegistration registration) {
                userRegistration = registration;
                if (registration != null && EventRegistration.STATUS_CONFIRMED.equalsIgnoreCase(registration.getRegistrationStatus())) {
                    binding.btnRegisterNow.setVisibility(View.GONE);
                    binding.layoutAlreadyRegistered.setVisibility(View.VISIBLE);
                } else {
                    binding.layoutAlreadyRegistered.setVisibility(View.GONE);
                    binding.btnRegisterNow.setVisibility(View.VISIBLE);
                    updateRegisterButtonState(event);
                }
            }

            @Override
            public void onError(Exception e) {
                binding.btnRegisterNow.setVisibility(View.VISIBLE);
                binding.layoutAlreadyRegistered.setVisibility(View.GONE);
                updateRegisterButtonState(event);
            }
        });
    }

    private void updateRegisterButtonState(Event event) {
        if (!Event.STATUS_ACTIVE.equalsIgnoreCase(event.getEventStatus())) {
            binding.btnRegisterNow.setEnabled(false);
            binding.btnRegisterNow.setText("Event Cancelled");
        } else if (event.isPastEvent()) {
            binding.btnRegisterNow.setEnabled(false);
            binding.btnRegisterNow.setText("Event Completed");
        } else if (event.isFull()) {
            binding.btnRegisterNow.setEnabled(false);
            binding.btnRegisterNow.setText("Fully Booked");
        } else if (event.isPastDeadline()) {
            binding.btnRegisterNow.setEnabled(false);
            binding.btnRegisterNow.setText("Registration Closed");
        } else {
            binding.btnRegisterNow.setEnabled(true);
            binding.btnRegisterNow.setText("Register Now");
        }
    }

    private void confirmCancelRegistration() {
        if (currentEvent == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel Event Registration?")
                .setMessage("Are you sure you want to cancel your registration for '" + currentEvent.getTitle() + "'? Your reserved seat will be released.")
                .setPositiveButton("Cancel Registration", (dialog, which) -> {
                    repository.cancelRegistration(currentEvent.getEventId(), repository.getCurrentUser().getUid(), new EventRepository.EventCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(EventDetailsActivity.this, "Registration cancelled.", Toast.LENGTH_SHORT).show();
                            loadEventDetails();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(EventDetailsActivity.this, "Failed to cancel: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Keep Registration", null)
                .show();
    }

    private void confirmCancelEvent() {
        if (currentEvent == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel This Event?")
                .setMessage("Are you sure you want to cancel '" + currentEvent.getTitle() + "'? This will notify registered attendees and close further registrations.")
                .setPositiveButton("Cancel Event", (dialog, which) -> {
                    repository.cancelEvent(currentEvent.getEventId(), new EventRepository.EventCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(EventDetailsActivity.this, "Event has been cancelled.", Toast.LENGTH_SHORT).show();
                            EventNotificationHelper.notifyEventCancelled(EventDetailsActivity.this, currentEvent);
                            loadEventDetails();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(EventDetailsActivity.this, "Failed to cancel event: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Keep Event", null)
                .show();
    }

    private void shareEvent() {
        if (currentEvent == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("🐾 PetSense Community Event: ").append(currentEvent.getTitle()).append("\n\n");
        if (currentEvent.getFormattedDate() != null) {
            sb.append("📅 Date: ").append(currentEvent.getFormattedDate());
            if (currentEvent.getStartTime() != null) sb.append(" at ").append(currentEvent.getStartTime());
            sb.append("\n");
        }
        if (currentEvent.getVenue() != null) {
            sb.append("📍 Venue: ").append(currentEvent.getVenue()).append(", ").append(currentEvent.getCity()).append("\n");
        }
        sb.append("🏷️ Category: ").append(currentEvent.getCategory()).append("\n");
        sb.append("🎟️ Fee: ").append(currentEvent.getEventFee()).append("\n\n");
        if (currentEvent.getDescription() != null) {
            sb.append(currentEvent.getDescription()).append("\n\n");
        }
        sb.append("Join pet lovers on PetSense!");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, currentEvent.getTitle());
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share Event"));
    }

    private void showReportEventDialog() {
        if (currentEvent == null) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_report_event, null);
        RadioGroup rgReasons = dialogView.findViewById(R.id.rgReportReasons);
        EditText etDetails = dialogView.findViewById(R.id.etReportDetails);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Report Event")
                .setView(dialogView)
                .setPositiveButton("Submit Report", (dialog, which) -> {
                    int checkedId = rgReasons.getCheckedRadioButtonId();
                    RadioButton rb = dialogView.findViewById(checkedId);
                    String reason = rb != null ? rb.getText().toString() : "Safety concern";
                    String details = etDetails.getText() != null ? etDetails.getText().toString().trim() : "";

                    EventReport report = new EventReport(
                            null, currentEvent.getEventId(), currentEvent.getTitle(),
                            null, null, reason, details
                    );

                    repository.reportEvent(report, new EventRepository.EventCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(EventDetailsActivity.this, "Thank you. Your report has been submitted for review.", Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(EventDetailsActivity.this, "Could not submit report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEventDetails();
    }
}
