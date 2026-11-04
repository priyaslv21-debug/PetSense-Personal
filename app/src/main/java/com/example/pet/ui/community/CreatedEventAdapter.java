package com.example.pet.ui.community;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pet.R;
import com.example.pet.data.Event;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for "Events I Created" tab in MyEventsActivity.
 */
public class CreatedEventAdapter extends RecyclerView.Adapter<CreatedEventAdapter.ViewHolder> {

    public interface OnCreatedEventListener {
        void onEventClick(Event event);
        void onViewParticipants(Event event);
        void onEditEvent(Event event);
        void onCancelEvent(Event event);
    }

    private final OnCreatedEventListener listener;
    private final List<Event> items = new ArrayList<>();

    public CreatedEventAdapter(OnCreatedEventListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Event> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void setEvents(List<Event> newItems) {
        setItems(newItems);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event_created_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvCreatedCategory;
        private final TextView tvCreatedStatusBadge;
        private final TextView tvCreatedTitle;
        private final TextView tvCreatedDateTime;
        private final TextView tvCreatedLocation;
        private final TextView tvCreatedParticipantCount;
        private final MaterialButton btnCancelCreatedEvent;
        private final MaterialButton btnEditCreatedEvent;
        private final MaterialButton btnViewParticipants;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCreatedCategory = itemView.findViewById(R.id.tvCreatedCategory);
            tvCreatedStatusBadge = itemView.findViewById(R.id.tvCreatedStatusBadge);
            tvCreatedTitle = itemView.findViewById(R.id.tvCreatedTitle);
            tvCreatedDateTime = itemView.findViewById(R.id.tvCreatedDateTime);
            tvCreatedLocation = itemView.findViewById(R.id.tvCreatedLocation);
            tvCreatedParticipantCount = itemView.findViewById(R.id.tvCreatedParticipantCount);
            btnCancelCreatedEvent = itemView.findViewById(R.id.btnCancelCreatedEvent);
            btnEditCreatedEvent = itemView.findViewById(R.id.btnEditCreatedEvent);
            btnViewParticipants = itemView.findViewById(R.id.btnViewParticipants);
        }

        void bind(Event event, OnCreatedEventListener listener) {
            Context context = itemView.getContext();

            tvCreatedCategory.setText(event.getCategory() != null ? event.getCategory() : "Community");
            tvCreatedTitle.setText(event.getTitle());

            // Status Badge
            String status = event.getEventStatus() != null ? event.getEventStatus() : Event.STATUS_ACTIVE;
            tvCreatedStatusBadge.setText(status);
            if (Event.STATUS_ACTIVE.equalsIgnoreCase(status)) {
                tvCreatedStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.pd_green_text));
                tvCreatedStatusBadge.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.pd_green_bg));
                btnCancelCreatedEvent.setVisibility(View.VISIBLE);
                btnEditCreatedEvent.setVisibility(View.VISIBLE);
            } else {
                tvCreatedStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.pd_red_text));
                tvCreatedStatusBadge.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.pd_red_bg));
                btnCancelCreatedEvent.setVisibility(View.GONE);
                btnEditCreatedEvent.setVisibility(View.GONE);
            }

            // Date & Time
            String dt = event.getFormattedDate() != null ? event.getFormattedDate() : "Date TBD";
            if (event.getStartTime() != null) {
                dt += " • " + event.getStartTime();
            }
            tvCreatedDateTime.setText(dt);

            // Location
            String loc = event.getVenue() != null ? event.getVenue() : "Venue TBD";
            if (event.getCity() != null) loc += ", " + event.getCity();
            tvCreatedLocation.setText(loc);

            // Participant Count
            int reg = event.getRegistrationCount();
            int max = event.getMaximumParticipants();
            if (max > 0) {
                tvCreatedParticipantCount.setText(reg + " / " + max + " Registered");
            } else {
                tvCreatedParticipantCount.setText(reg + " Registered (Open)");
            }

            // Listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onEventClick(event);
            });

            btnViewParticipants.setOnClickListener(v -> {
                if (listener != null) listener.onViewParticipants(event);
            });

            btnEditCreatedEvent.setOnClickListener(v -> {
                if (listener != null) listener.onEditEvent(event);
            });

            btnCancelCreatedEvent.setOnClickListener(v -> {
                if (listener != null) listener.onCancelEvent(event);
            });
        }
    }
}
