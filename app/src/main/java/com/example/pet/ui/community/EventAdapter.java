package com.example.pet.ui.community;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pet.R;
import com.example.pet.data.Event;
import com.example.pet.ui.dashboard.PetPhotoLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for rendering Community & Events cards in both Discovery Feed and Home Screen Carousel.
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    private final boolean isCompact;
    private final OnEventClickListener listener;
    private final List<Event> allEvents = new ArrayList<>();
    private final List<Event> filteredEvents = new ArrayList<>();
    private final PetPhotoLoader photoLoader = new PetPhotoLoader();

    public EventAdapter(boolean isCompact, OnEventClickListener listener) {
        this.isCompact = isCompact;
        this.listener = listener;
    }

    public void setEvents(List<Event> events) {
        this.allEvents.clear();
        if (events != null) {
            this.allEvents.addAll(events);
        }
        this.filteredEvents.clear();
        this.filteredEvents.addAll(this.allEvents);
        notifyDataSetChanged();
    }

    public void filter(String query, String category) {
        filteredEvents.clear();
        String q = query != null ? query.trim().toLowerCase() : "";
        String cat = category != null ? category.trim() : "";

        for (Event event : allEvents) {
            boolean matchesCat = cat.isEmpty() || "All".equalsIgnoreCase(cat)
                    || (event.getCategory() != null && event.getCategory().equalsIgnoreCase(cat));

            boolean matchesQuery = q.isEmpty()
                    || (event.getTitle() != null && event.getTitle().toLowerCase().contains(q))
                    || (event.getCity() != null && event.getCity().toLowerCase().contains(q))
                    || (event.getVenue() != null && event.getVenue().toLowerCase().contains(q))
                    || (event.getOrganizerName() != null && event.getOrganizerName().toLowerCase().contains(q));

            if (matchesCat && matchesQuery) {
                filteredEvents.add(event);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = isCompact ? R.layout.item_event_card_compact : R.layout.item_event_card;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new EventViewHolder(view, isCompact);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = filteredEvents.get(position);
        holder.bind(event, listener, photoLoader);
    }

    @Override
    public int getItemCount() {
        return filteredEvents.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivEventPoster;
        private final TextView tvEventCategory;
        private final TextView tvEventFee;
        private final TextView tvEventTitle;
        private final TextView tvEventDateTime;
        private final TextView tvEventLocation;
        private final TextView tvOrganizerName;
        private final TextView tvEventSeats;
        private final ImageView ivVerifiedBadge;
        private final TextView tvEventStatusOverlay;

        EventViewHolder(@NonNull View itemView, boolean isCompact) {
            super(itemView);
            ivEventPoster = itemView.findViewById(R.id.ivEventPoster);
            tvEventCategory = itemView.findViewById(R.id.tvEventCategory);
            tvEventFee = itemView.findViewById(R.id.tvEventFee);
            tvEventTitle = itemView.findViewById(R.id.tvEventTitle);
            tvEventDateTime = itemView.findViewById(R.id.tvEventDateTime);
            tvEventLocation = itemView.findViewById(R.id.tvEventLocation);
            tvOrganizerName = itemView.findViewById(R.id.tvOrganizerName);
            tvEventSeats = itemView.findViewById(R.id.tvEventSeats);

            if (!isCompact) {
                ivVerifiedBadge = itemView.findViewById(R.id.ivVerifiedBadge);
                tvEventStatusOverlay = itemView.findViewById(R.id.tvEventStatusOverlay);
            } else {
                ivVerifiedBadge = null;
                tvEventStatusOverlay = null;
            }
        }

        void bind(Event event, OnEventClickListener listener, PetPhotoLoader loader) {
            Context context = itemView.getContext();

            tvEventTitle.setText(event.getTitle());
            tvEventCategory.setText(event.getCategory() != null ? event.getCategory() : "Community");

            // Event Fee
            String fee = event.getEventFee();
            if (fee == null || fee.trim().isEmpty() || "Free".equalsIgnoreCase(fee.trim())) {
                tvEventFee.setText("Free");
                tvEventFee.setTextColor(ContextCompat.getColor(context, R.color.pd_green_text));
                tvEventFee.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.pd_green_bg));
            } else {
                tvEventFee.setText(fee);
                tvEventFee.setTextColor(ContextCompat.getColor(context, R.color.pd_amber_text));
                tvEventFee.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.pd_amber_bg));
            }

            // Date & Time
            String dateFormatted = event.getFormattedDate();
            String timeStr = event.getStartTime() != null ? event.getStartTime() : "";
            if (dateFormatted != null && !timeStr.isEmpty()) {
                tvEventDateTime.setText(dateFormatted + " · " + timeStr);
            } else if (dateFormatted != null) {
                tvEventDateTime.setText(dateFormatted);
            } else {
                tvEventDateTime.setText("Upcoming date");
            }

            // Location
            String loc = "";
            if (event.getVenue() != null && !event.getVenue().trim().isEmpty()) {
                loc = event.getVenue();
                if (event.getCity() != null && !event.getCity().trim().isEmpty()) {
                    loc += ", " + event.getCity();
                }
            } else if (event.getCity() != null) {
                loc = event.getCity();
            }
            tvEventLocation.setText(!loc.isEmpty() ? loc : "Venue to be announced");

            // Organizer
            String organizer = event.getOrganizerName() != null ? event.getOrganizerName() : "Community Organizer";
            tvOrganizerName.setText(organizer);

            // Verification Badge (Full card only)
            if (ivVerifiedBadge != null) {
                if (event.isVerifiedOrganizer()) {
                    ivVerifiedBadge.setVisibility(View.VISIBLE);
                } else {
                    ivVerifiedBadge.setVisibility(View.GONE);
                }
            }

            // Seats left / Capacity
            if (Event.STATUS_CANCELLED.equalsIgnoreCase(event.getEventStatus())) {
                tvEventSeats.setText("Cancelled");
                tvEventSeats.setTextColor(ContextCompat.getColor(context, R.color.pd_red_text));
                if (tvEventStatusOverlay != null) {
                    tvEventStatusOverlay.setText("Cancelled");
                    tvEventStatusOverlay.setVisibility(View.VISIBLE);
                }
            } else if (event.isFull()) {
                tvEventSeats.setText("Fully Booked");
                tvEventSeats.setTextColor(ContextCompat.getColor(context, R.color.pd_red_text));
                if (tvEventStatusOverlay != null) {
                    tvEventStatusOverlay.setText("Fully Booked");
                    tvEventStatusOverlay.setVisibility(View.VISIBLE);
                }
            } else if (event.isPastDeadline()) {
                tvEventSeats.setText("Registration Closed");
                tvEventSeats.setTextColor(ContextCompat.getColor(context, R.color.pd_text_muted));
                if (tvEventStatusOverlay != null) {
                    tvEventStatusOverlay.setText("Closed");
                    tvEventStatusOverlay.setVisibility(View.VISIBLE);
                }
            } else {
                int seats = event.getAvailableSeats();
                if (seats > 0) {
                    tvEventSeats.setText(seats + " seats left");
                    tvEventSeats.setTextColor(ContextCompat.getColor(context, R.color.pd_primary));
                } else {
                    tvEventSeats.setText(event.getRegistrationCount() + " attending");
                    tvEventSeats.setTextColor(ContextCompat.getColor(context, R.color.pd_primary));
                }
                if (tvEventStatusOverlay != null) {
                    tvEventStatusOverlay.setVisibility(View.GONE);
                }
            }

            // Poster Image
            loader.bind(context, ivEventPoster, event.getImageUrl());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEventClick(event);
                }
            });
        }
    }
}
