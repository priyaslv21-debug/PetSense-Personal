package com.example.pet.ui.community;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pet.data.Event;
import com.example.pet.data.EventRegistration;
import com.example.pet.data.EventRepository;
import com.example.pet.databinding.ActivityEventParticipantsBinding;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * Organizer-only screen for viewing the list of registered attendees and pets for an event.
 * Also provides an attendee summary export/sharing action.
 */
public class EventParticipantsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "extra_event_id";
    public static final String EXTRA_EVENT_TITLE = "extra_event_title";

    private ActivityEventParticipantsBinding binding;
    private EventRepository repository;
    private String eventId;
    private String eventTitle;

    private EventRegistrationAdapter adapter;
    private List<EventRegistration> attendeeList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventParticipantsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new EventRepository();

        if (getIntent() != null) {
            eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
            eventTitle = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        }

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Event ID required.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (eventTitle != null && !eventTitle.trim().isEmpty()) {
            binding.tvParticipantsHeaderTitle.setText(eventTitle);
        }

        setupViews();
        setupRecyclerView();
        verifyOrganizerAndLoadAttendees();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnShareAttendeeList.setOnClickListener(v -> shareAttendeeSummary());
        binding.swipeRefreshParticipants.setOnRefreshListener(this::loadAttendees);
    }

    private void setupRecyclerView() {
        adapter = new EventRegistrationAdapter(
                EventRegistrationAdapter.VIEW_TYPE_PARTICIPANT_ATTENDEE,
                null,
                null
        );
        binding.rvParticipants.setLayoutManager(new LinearLayoutManager(this));
        binding.rvParticipants.setAdapter(adapter);
    }

    private void verifyOrganizerAndLoadAttendees() {
        binding.pbLoadingParticipants.setVisibility(View.VISIBLE);
        FirebaseUser currentUser = repository.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Authentication required.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        repository.getEvent(eventId, new EventRepository.EventCallback<Event>() {
            @Override
            public void onSuccess(Event event) {
                if (event != null && event.getTitle() != null) {
                    binding.tvParticipantsHeaderTitle.setText(event.getTitle());
                }

                // Security check: only organizer can view participant lists
                if (event != null && !currentUser.getUid().equals(event.getOrganizerId())) {
                    Toast.makeText(EventParticipantsActivity.this, "Only the event organizer can view the participant roster.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                loadAttendees();
            }

            @Override
            public void onError(Exception e) {
                // Try loading attendees anyway in case network hiccuped on event doc
                loadAttendees();
            }
        });
    }

    private void loadAttendees() {
        binding.pbLoadingParticipants.setVisibility(View.VISIBLE);
        repository.getRegistrationsForEvent(eventId, new EventRepository.EventCallback<List<EventRegistration>>() {
            @Override
            public void onSuccess(List<EventRegistration> registrations) {
                binding.pbLoadingParticipants.setVisibility(View.GONE);
                binding.swipeRefreshParticipants.setRefreshing(false);
                attendeeList = registrations != null ? registrations : new ArrayList<>();
                adapter.setRegistrations(attendeeList);

                int count = attendeeList.size();
                binding.tvAttendeeCountSummary.setText(count + (count == 1 ? " Attendee Registered" : " Attendees Registered"));

                if (attendeeList.isEmpty()) {
                    binding.layoutEmptyParticipants.setVisibility(View.VISIBLE);
                    binding.rvParticipants.setVisibility(View.GONE);
                } else {
                    binding.layoutEmptyParticipants.setVisibility(View.GONE);
                    binding.rvParticipants.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                binding.pbLoadingParticipants.setVisibility(View.GONE);
                binding.swipeRefreshParticipants.setRefreshing(false);
                Toast.makeText(EventParticipantsActivity.this, "Failed to load attendees: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareAttendeeSummary() {
        if (attendeeList == null || attendeeList.isEmpty()) {
            Toast.makeText(this, "No attendees to export yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Attendee Roster - ").append(binding.tvParticipantsHeaderTitle.getText()).append("\n");
        sb.append("Total Attendees: ").append(attendeeList.size()).append("\n\n");

        int index = 1;
        for (EventRegistration reg : attendeeList) {
            sb.append(index++).append(". ").append(reg.getParticipantName()).append("\n");
            if (reg.getPhone() != null && !reg.getPhone().isEmpty()) {
                sb.append("   Phone: ").append(reg.getPhone()).append("\n");
            }
            if (reg.getEmail() != null && !reg.getEmail().isEmpty()) {
                sb.append("   Email: ").append(reg.getEmail()).append("\n");
            }
            if (reg.getPetName() != null && !reg.getPetName().isEmpty()) {
                sb.append("   Pet: ").append(reg.getPetName());
                if (reg.getPetSpecies() != null) sb.append(" (").append(reg.getPetSpecies()).append(")");
                sb.append("\n");
            }
            if (reg.getAdditionalNotes() != null && !reg.getAdditionalNotes().trim().isEmpty()) {
                sb.append("   Notes: ").append(reg.getAdditionalNotes().trim()).append("\n");
            }
            sb.append("   Status: ").append(reg.getRegistrationStatus()).append("\n\n");
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Attendee Roster: " + binding.tvParticipantsHeaderTitle.getText());
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share Attendee Roster"));
    }
}
