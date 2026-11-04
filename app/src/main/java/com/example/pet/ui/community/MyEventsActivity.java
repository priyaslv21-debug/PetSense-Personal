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
import com.example.pet.databinding.ActivityMyEventsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

/**
 * Screen displaying the user's event history:
 * Tab 0: "Registered Events" (events user booked with their pet)
 * Tab 1: "Events I Created" (events user organized and is managing)
 */
public class MyEventsActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_TAB = "extra_initial_tab";

    private ActivityMyEventsBinding binding;
    private EventRepository repository;

    private EventRegistrationAdapter registeredAdapter;
    private CreatedEventAdapter createdAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyEventsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new EventRepository();

        setupViews();
        setupRecyclerViews();

        int initialTab = getIntent().getIntExtra(EXTRA_INITIAL_TAB, 0);
        if (initialTab > 0 && initialTab < binding.tabLayoutMyEvents.getTabCount()) {
            TabLayout.Tab tab = binding.tabLayoutMyEvents.getTabAt(initialTab);
            if (tab != null) tab.select();
        }

        loadCurrentTabData();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        View.OnClickListener openCreateListener = v -> {
            Intent intent = new Intent(MyEventsActivity.this, CreateEventActivity.class);
            startActivity(intent);
        };
        binding.btnHeaderCreate.setOnClickListener(openCreateListener);
        binding.fabHostEvent.setOnClickListener(openCreateListener);
        binding.btnEmptyCreateEvent.setOnClickListener(openCreateListener);

        binding.btnExploreEvents.setOnClickListener(v -> {
            Intent intent = new Intent(MyEventsActivity.this, EventActivity.class);
            startActivity(intent);
            finish();
        });

        binding.tabLayoutMyEvents.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                loadCurrentTabData();
            }
        });

        binding.swipeRefreshMyEvents.setOnRefreshListener(this::loadCurrentTabData);
    }

    private void setupRecyclerViews() {
        // Registered events
        registeredAdapter = new EventRegistrationAdapter(
                EventRegistrationAdapter.VIEW_TYPE_REGISTERED_EVENT,
                reg -> {
                    Intent intent = new Intent(MyEventsActivity.this, EventDetailsActivity.class);
                    intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, reg.getEventId());
                    startActivity(intent);
                },
                this::confirmCancelRegistration
        );
        binding.rvRegisteredEvents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRegisteredEvents.setAdapter(registeredAdapter);

        // Created events
        createdAdapter = new CreatedEventAdapter(
                new CreatedEventAdapter.OnCreatedEventListener() {
                    @Override
                    public void onEventClick(Event event) {
                        Intent intent = new Intent(MyEventsActivity.this, EventDetailsActivity.class);
                        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
                        startActivity(intent);
                    }

                    @Override
                    public void onViewParticipants(Event event) {
                        Intent intent = new Intent(MyEventsActivity.this, EventParticipantsActivity.class);
                        intent.putExtra(EventParticipantsActivity.EXTRA_EVENT_ID, event.getEventId());
                        intent.putExtra(EventParticipantsActivity.EXTRA_EVENT_TITLE, event.getTitle());
                        startActivity(intent);
                    }

                    @Override
                    public void onEditEvent(Event event) {
                        Intent intent = new Intent(MyEventsActivity.this, CreateEventActivity.class);
                        intent.putExtra(CreateEventActivity.EXTRA_EVENT, event);
                        startActivity(intent);
                    }

                    @Override
                    public void onCancelEvent(Event event) {
                        confirmCancelEvent(event);
                    }
                }
        );
        binding.rvCreatedEvents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCreatedEvents.setAdapter(createdAdapter);
    }

    private void switchTab(int position) {
        if (position == 0) {
            binding.rvRegisteredEvents.setVisibility(View.VISIBLE);
            binding.rvCreatedEvents.setVisibility(View.GONE);
            binding.fabHostEvent.setVisibility(View.GONE);
        } else {
            binding.rvRegisteredEvents.setVisibility(View.GONE);
            binding.rvCreatedEvents.setVisibility(View.VISIBLE);
            binding.fabHostEvent.setVisibility(View.VISIBLE);
        }
        loadCurrentTabData();
    }

    private void loadCurrentTabData() {
        int selectedTab = binding.tabLayoutMyEvents.getSelectedTabPosition();
        FirebaseUser user = repository.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in to view your events.", Toast.LENGTH_SHORT).show();
            binding.swipeRefreshMyEvents.setRefreshing(false);
            binding.pbLoadingMyEvents.setVisibility(View.GONE);
            return;
        }

        if (selectedTab == 0) {
            loadRegisteredEvents(user.getUid());
        } else {
            loadCreatedEvents(user.getUid());
        }
    }

    private void loadRegisteredEvents(String userId) {
        binding.layoutEmptyCreated.setVisibility(View.GONE);
        binding.pbLoadingMyEvents.setVisibility(View.VISIBLE);

        repository.getRegisteredEventsForUser(userId, new EventRepository.EventCallback<List<EventRegistration>>() {
            @Override
            public void onSuccess(List<EventRegistration> registrations) {
                binding.pbLoadingMyEvents.setVisibility(View.GONE);
                binding.swipeRefreshMyEvents.setRefreshing(false);
                registeredAdapter.setRegistrations(registrations);

                if (registrations == null || registrations.isEmpty()) {
                    binding.layoutEmptyRegistered.setVisibility(View.VISIBLE);
                    binding.rvRegisteredEvents.setVisibility(View.GONE);
                } else {
                    binding.layoutEmptyRegistered.setVisibility(View.GONE);
                    binding.rvRegisteredEvents.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                binding.pbLoadingMyEvents.setVisibility(View.GONE);
                binding.swipeRefreshMyEvents.setRefreshing(false);
                Toast.makeText(MyEventsActivity.this, "Could not load registered events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCreatedEvents(String userId) {
        binding.layoutEmptyRegistered.setVisibility(View.GONE);
        binding.pbLoadingMyEvents.setVisibility(View.VISIBLE);

        repository.getEventsCreatedByUser(userId, new EventRepository.EventCallback<List<Event>>() {
            @Override
            public void onSuccess(List<Event> events) {
                binding.pbLoadingMyEvents.setVisibility(View.GONE);
                binding.swipeRefreshMyEvents.setRefreshing(false);
                createdAdapter.setEvents(events);

                if (events == null || events.isEmpty()) {
                    binding.layoutEmptyCreated.setVisibility(View.VISIBLE);
                    binding.rvCreatedEvents.setVisibility(View.GONE);
                } else {
                    binding.layoutEmptyCreated.setVisibility(View.GONE);
                    binding.rvCreatedEvents.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                binding.pbLoadingMyEvents.setVisibility(View.GONE);
                binding.swipeRefreshMyEvents.setRefreshing(false);
                Toast.makeText(MyEventsActivity.this, "Could not load your created events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmCancelRegistration(EventRegistration reg) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel Event Registration?")
                .setMessage("Are you sure you want to cancel your registration for '" + reg.getEventTitle() + "'? Your seat will be released.")
                .setPositiveButton("Cancel Registration", (dialog, which) -> {
                    repository.cancelRegistration(reg.getEventId(), new EventRepository.EventCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(MyEventsActivity.this, "Registration cancelled successfully.", Toast.LENGTH_SHORT).show();
                            loadCurrentTabData();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(MyEventsActivity.this, "Failed to cancel: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Keep Registration", null)
                .show();
    }

    private void confirmCancelEvent(Event event) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cancel This Event?")
                .setMessage("Are you sure you want to cancel '" + event.getTitle() + "'? This will notify attendees and stop further registrations.")
                .setPositiveButton("Cancel Event", (dialog, which) -> {
                    repository.cancelEvent(event.getEventId(), new EventRepository.EventCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(MyEventsActivity.this, "Event has been cancelled.", Toast.LENGTH_SHORT).show();
                            EventNotificationHelper.notifyEventCancelled(MyEventsActivity.this, event);
                            loadCurrentTabData();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(MyEventsActivity.this, "Failed to cancel event: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Keep Event", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentTabData();
    }
}
