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
import com.example.pet.data.EventRegistration;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for:
 * 1. User's registered events list ("Events I Registered For").
 * 2. Organizer's attendee list in EventParticipantsActivity.
 */
public class EventRegistrationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_REGISTERED_EVENT = 1;
    public static final int VIEW_TYPE_PARTICIPANT_ATTENDEE = 2;

    public interface OnRegistrationActionListener {
        void onViewEventDetails(EventRegistration registration);
        void onCancelRegistration(EventRegistration registration);
    }

    public interface OnItemClickListener {
        void onItemClick(EventRegistration registration);
    }

    public interface OnCancelClickListener {
        void onCancelClick(EventRegistration registration);
    }

    private final int viewType;
    private OnRegistrationActionListener actionListener;
    private OnItemClickListener itemClickListener;
    private OnCancelClickListener cancelClickListener;
    private final List<EventRegistration> items = new ArrayList<>();

    public EventRegistrationAdapter(int viewType, OnRegistrationActionListener listener) {
        this.viewType = viewType;
        this.actionListener = listener;
    }

    public EventRegistrationAdapter(int viewType, OnItemClickListener clickListener, OnCancelClickListener cancelListener) {
        this.viewType = viewType;
        this.itemClickListener = clickListener;
        this.cancelClickListener = cancelListener;
    }

    public void setItems(List<EventRegistration> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void setRegistrations(List<EventRegistration> newItems) {
        setItems(newItems);
    }

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_REGISTERED_EVENT) {
            View v = inflater.inflate(R.layout.item_event_registered_card, parent, false);
            return new RegisteredEventViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_event_participant, parent, false);
            return new ParticipantViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        EventRegistration registration = items.get(position);
        if (holder instanceof RegisteredEventViewHolder) {
            ((RegisteredEventViewHolder) holder).bind(registration, actionListener, itemClickListener, cancelClickListener);
        } else if (holder instanceof ParticipantViewHolder) {
            ((ParticipantViewHolder) holder).bind(registration);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ViewHolder 1: For User's "Events I Registered For"
    static class RegisteredEventViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvRegisteredEventTitle;
        private final TextView tvRegistrationStatusBadge;
        private final TextView tvRegisteredEventDate;
        private final TextView tvRegisteredPetInfo;
        private final MaterialButton btnCancelRegistration;
        private final MaterialButton btnViewEventDetails;

        RegisteredEventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRegisteredEventTitle = itemView.findViewById(R.id.tvRegisteredEventTitle);
            tvRegistrationStatusBadge = itemView.findViewById(R.id.tvRegistrationStatusBadge);
            tvRegisteredEventDate = itemView.findViewById(R.id.tvRegisteredEventDate);
            tvRegisteredPetInfo = itemView.findViewById(R.id.tvRegisteredPetInfo);
            btnCancelRegistration = itemView.findViewById(R.id.btnCancelRegistration);
            btnViewEventDetails = itemView.findViewById(R.id.btnViewEventDetails);
        }

        void bind(EventRegistration reg, OnRegistrationActionListener actionListener,
                  OnItemClickListener clickListener, OnCancelClickListener cancelListener) {
            Context context = itemView.getContext();

            tvRegisteredEventTitle.setText(reg.getEventTitle() != null ? reg.getEventTitle() : "Event");

            // Status badge
            String status = reg.getRegistrationStatus() != null ? reg.getRegistrationStatus() : EventRegistration.STATUS_CONFIRMED;
            tvRegistrationStatusBadge.setText(status);
            if (EventRegistration.STATUS_CONFIRMED.equalsIgnoreCase(status)) {
                tvRegistrationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.pd_green_text));
                tvRegistrationStatusBadge.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.pd_green_bg));
                btnCancelRegistration.setVisibility(View.VISIBLE);
            } else {
                tvRegistrationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.pd_red_text));
                tvRegistrationStatusBadge.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.pd_red_bg));
                btnCancelRegistration.setVisibility(View.GONE);
            }

            // Date & Location
            String dt = "";
            if (reg.getEventDate() != null && reg.getEventDate() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
                dt = "📅 " + sdf.format(new Date(reg.getEventDate()));
            }
            if (reg.getEventVenue() != null && !reg.getEventVenue().isEmpty()) {
                if (!dt.isEmpty()) dt += " · ";
                dt += reg.getEventVenue();
                if (reg.getEventCity() != null && !reg.getEventCity().isEmpty()) {
                    dt += ", " + reg.getEventCity();
                }
            }
            tvRegisteredEventDate.setText(dt);

            // Attending Pet
            String petInfo = "Registered for: " + (reg.getPetName() != null ? reg.getPetName() : "None registered");
            if (reg.getPetSpecies() != null && !reg.getPetSpecies().isEmpty()) {
                petInfo += " (" + reg.getPetSpecies() + ")";
            }
            tvRegisteredPetInfo.setText(petInfo);

            // Click listener on card
            View.OnClickListener detailsClick = v -> {
                if (actionListener != null) {
                    actionListener.onViewEventDetails(reg);
                } else if (clickListener != null) {
                    clickListener.onItemClick(reg);
                }
            };
            itemView.setOnClickListener(detailsClick);
            if (btnViewEventDetails != null) {
                btnViewEventDetails.setOnClickListener(detailsClick);
            }

            // Cancel action
            btnCancelRegistration.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onCancelRegistration(reg);
                } else if (cancelListener != null) {
                    cancelListener.onCancelClick(reg);
                }
            });
        }
    }

    // ViewHolder 2: For Organizer's Attendee List
    static class ParticipantViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvParticipantName;
        private final TextView tvParticipantRegisteredTime;
        private final TextView tvParticipantContact;
        private final TextView tvParticipantPet;
        private final TextView tvParticipantNotes;

        ParticipantViewHolder(@NonNull View itemView) {
            super(itemView);
            tvParticipantName = itemView.findViewById(R.id.tvParticipantName);
            tvParticipantRegisteredTime = itemView.findViewById(R.id.tvParticipantRegisteredTime);
            tvParticipantContact = itemView.findViewById(R.id.tvParticipantContact);
            tvParticipantPet = itemView.findViewById(R.id.tvParticipantPet);
            tvParticipantNotes = itemView.findViewById(R.id.tvParticipantNotes);
        }

        void bind(EventRegistration reg) {
            tvParticipantName.setText(reg.getParticipantName() != null ? reg.getParticipantName() : "Anonymous Attendee");

            if (reg.getRegisteredAt() != null && reg.getRegisteredAt() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
                tvParticipantRegisteredTime.setText(sdf.format(new Date(reg.getRegisteredAt())));
            } else {
                tvParticipantRegisteredTime.setText("");
            }

            // Contact
            StringBuilder contact = new StringBuilder();
            if (reg.getPhone() != null && !reg.getPhone().isEmpty()) {
                contact.append("📞 ").append(reg.getPhone());
            }
            if (reg.getEmail() != null && !reg.getEmail().isEmpty()) {
                if (contact.length() > 0) contact.append("  •  ");
                contact.append("✉️ ").append(reg.getEmail());
            }
            tvParticipantContact.setText(contact.toString());

            // Pet
            String pet = reg.getPetName() != null ? reg.getPetName() : "No pet specified";
            if (reg.getPetSpecies() != null && !reg.getPetSpecies().isEmpty()) {
                pet += " (" + reg.getPetSpecies();
                if (reg.getPetAge() != null && !reg.getPetAge().isEmpty()) {
                    pet += ", " + reg.getPetAge();
                }
                pet += ")";
            }
            tvParticipantPet.setText(pet);

            // Notes
            if (reg.getAdditionalNotes() != null && !reg.getAdditionalNotes().trim().isEmpty()) {
                tvParticipantNotes.setVisibility(View.VISIBLE);
                tvParticipantNotes.setText("Notes: " + reg.getAdditionalNotes().trim());
            } else {
                tvParticipantNotes.setVisibility(View.GONE);
            }
        }
    }
}
