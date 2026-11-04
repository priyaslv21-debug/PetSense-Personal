package com.example.pet.ui.community;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pet.R;
import com.example.pet.data.Event;
import com.example.pet.data.EventRepository;
import com.example.pet.databinding.ActivityEventBinding;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * Public discovery feed for PetSense Community & Events.
 * Allows searching by title/city/organizer, filtering by category,
 * refreshing event listings, and navigating to Event Details or Creation.
 */
public class EventActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_CATEGORY = "extra_initial_category";

    private ActivityEventBinding binding;
    private EventRepository repository;
    private EventAdapter adapter;
    private String selectedCategory = "All";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new EventRepository();

        setupViews();
        setupSearchAndFilters();
        loadEvents();

        handleInitialIntent();
    }

    private void handleInitialIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_INITIAL_CATEGORY)) {
            String initialCat = intent.getStringExtra(EXTRA_INITIAL_CATEGORY);
            if (initialCat != null && !initialCat.isEmpty()) {
                selectCategoryChip(initialCat);
            }
        }
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        adapter = new EventAdapter(false, event -> {
            Intent intent = new Intent(EventActivity.this, EventDetailsActivity.class);
            intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
            startActivity(intent);
        });

        binding.rvEvents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvEvents.setAdapter(adapter);

        binding.swipeRefresh.setColorSchemeResources(R.color.pd_primary);
        binding.swipeRefresh.setOnRefreshListener(this::loadEvents);

        binding.fabCreateEvent.setOnClickListener(v -> openCreateEvent());
        binding.btnCreateEventTop.setOnClickListener(v -> openCreateEvent());
        binding.btnEmptyCreateEvent.setOnClickListener(v -> openCreateEvent());

        binding.btnMyEvents.setOnClickListener(v -> {
            Intent intent = new Intent(EventActivity.this, MyEventsActivity.class);
            startActivity(intent);
        });
    }

    private void setupSearchAndFilters() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                binding.btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                adapter.filter(query, selectedCategory);
                checkEmptyState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnClearSearch.setOnClickListener(v -> {
            binding.etSearch.setText("");
            adapter.filter("", selectedCategory);
            checkEmptyState();
        });

        binding.chipGroupCategories.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedCategory = "All";
                binding.chipAll.setChecked(true);
            } else {
                int checkedId = checkedIds.get(0);
                Chip chip = findViewById(checkedId);
                if (chip != null) {
                    if (chip.getId() == R.id.chipAll) {
                        selectedCategory = "All";
                    } else {
                        selectedCategory = chip.getText().toString();
                    }
                }
            }
            adapter.filter(binding.etSearch.getText().toString(), selectedCategory);
            checkEmptyState();
        });
    }

    private void selectCategoryChip(String categoryName) {
        for (int i = 0; i < binding.chipGroupCategories.getChildCount(); i++) {
            View child = binding.chipGroupCategories.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.getText().toString().equalsIgnoreCase(categoryName)) {
                    chip.setChecked(true);
                    selectedCategory = categoryName;
                    adapter.filter(binding.etSearch.getText().toString(), selectedCategory);
                    checkEmptyState();
                    return;
                }
            }
        }
    }

    private void loadEvents() {
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.getUpcomingEvents(new EventRepository.EventCallback<List<Event>>() {
            @Override
            public void onSuccess(List<Event> events) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                adapter.setEvents(events);
                adapter.filter(binding.etSearch.getText().toString(), selectedCategory);
                checkEmptyState();
            }

            @Override
            public void onError(Exception e) {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(EventActivity.this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                checkEmptyState();
            }
        });
    }

    private void checkEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        binding.layoutEmptyEvents.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvEvents.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void openCreateEvent() {
        Intent intent = new Intent(EventActivity.this, CreateEventActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
    }
}
