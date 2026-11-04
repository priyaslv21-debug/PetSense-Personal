package com.example.pet.reminder;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pet.R;
import com.example.pet.data.Reminder;
import com.example.pet.databinding.ItemReminderCardBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter displaying pet care reminders with live status badges,
 * category icons, inline completion, snoozing, and options menu.
 */
public class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.ViewHolder> {

    public interface OnReminderActionListener {
        void onItemClick(Reminder reminder);
        void onCompleteClick(Reminder reminder);
        void onSnoozeClick(Reminder reminder);
        void onToggleEnabled(Reminder reminder, boolean isEnabled);
        void onEditClick(Reminder reminder);
        void onDeleteClick(Reminder reminder);
    }

    private final List<Reminder> items = new ArrayList<>();
    private final OnReminderActionListener listener;

    public ReminderAdapter(OnReminderActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Reminder> newItems) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return newItems != null ? newItems.size() : 0;
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldId = items.get(oldItemPosition).getReminderId();
                String newId = newItems.get(newItemPosition).getReminderId();
                return oldId != null && oldId.equals(newId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Reminder o = items.get(oldItemPosition);
                Reminder n = newItems.get(newItemPosition);
                return o.isEnabled() == n.isEnabled()
                        && o.getScheduledTimestamp() == n.getScheduledTimestamp()
                        && o.getStatus().equals(n.getStatus())
                        && o.getTitle().equals(n.getTitle());
            }
        });

        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReminderCardBinding binding = ItemReminderCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemReminderCardBinding binding;

        ViewHolder(ItemReminderCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Reminder reminder) {
            Context context = itemView.getContext();

            // Title & Description
            binding.tvReminderTitle.setText(reminder.getTitle());

            String desc = reminder.getDescription();
            if (desc != null && !desc.trim().isEmpty()) {
                binding.tvDescription.setText(desc.trim());
                binding.tvDescription.setVisibility(View.VISIBLE);
            } else if (reminder.getMedicationName() != null && !reminder.getMedicationName().isEmpty()) {
                String medInfo = "Medication: " + reminder.getMedicationName()
                        + (reminder.getDosageFrequency() != null ? " (" + reminder.getDosageFrequency() + ")" : "");
                binding.tvDescription.setText(medInfo);
                binding.tvDescription.setVisibility(View.VISIBLE);
            } else {
                binding.tvDescription.setVisibility(View.GONE);
            }

            // Pet Name Badge
            String petName = reminder.getPetName();
            binding.tvPetBadge.setText(petName != null && !petName.isEmpty() ? petName : "Pet");

            // Category Badge
            String category = reminder.getReminderType() != null ? reminder.getReminderType() : "Care";
            binding.tvCategoryBadge.setText(category);

            // Repeat Badge
            String repeat = reminder.getRepeatFrequency();
            if (repeat != null && !Reminder.FREQ_NONE.equalsIgnoreCase(repeat)) {
                binding.tvRepeatBadge.setText("\u21BB " + repeat);
                binding.tvRepeatBadge.setVisibility(View.VISIBLE);
            } else {
                binding.tvRepeatBadge.setVisibility(View.GONE);
            }

            // Date and Time
            binding.tvDateTime.setText(reminder.getFormattedDateTime());

            // Advance notice
            String advance = reminder.getAdvanceNotification();
            if (advance != null && !Reminder.ADVANCE_EXACT.equalsIgnoreCase(advance)) {
                binding.tvAdvanceNotice.setText("Alert: " + advance);
                binding.tvAdvanceNotice.setVisibility(View.VISIBLE);
            } else {
                binding.tvAdvanceNotice.setVisibility(View.GONE);
            }

            // Category Icon styling
            configureCategoryStyle(context, reminder.getReminderType());

            // Dynamic Status styling
            String status = reminder.calculateCurrentStatus();
            configureStatusStyle(context, status);

            // Enabled Switch state
            binding.switchEnabled.setOnCheckedChangeListener(null);
            binding.switchEnabled.setChecked(reminder.isEnabled());
            binding.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleEnabled(reminder, isChecked);
                }
            });

            // Completed vs Active presentation
            boolean isCompleted = Reminder.STATUS_COMPLETED.equalsIgnoreCase(status);
            if (isCompleted) {
                binding.btnMarkComplete.setVisibility(View.GONE);
                binding.btnSnooze.setVisibility(View.GONE);
                itemView.setAlpha(0.65f);
            } else {
                binding.btnMarkComplete.setVisibility(View.VISIBLE);
                binding.btnSnooze.setVisibility(View.VISIBLE);
                itemView.setAlpha(reminder.isEnabled() ? 1.0f : 0.60f);
            }

            // Click Listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(reminder);
            });

            binding.btnMarkComplete.setOnClickListener(v -> {
                if (listener != null) listener.onCompleteClick(reminder);
            });

            binding.btnSnooze.setOnClickListener(v -> {
                if (listener != null) listener.onSnoozeClick(reminder);
            });

            binding.btnMoreOptions.setOnClickListener(v -> showPopupMenu(v, reminder));
        }

        private void configureCategoryStyle(Context context, String type) {
            int iconRes;
            int tintColor;
            int bgColor;

            if (Reminder.TYPE_VACCINATION.equalsIgnoreCase(type)) {
                iconRes = R.drawable.ic_reminder_vaccine;
                tintColor = ContextCompat.getColor(context, R.color.ps_primary);
                bgColor = Color.parseColor("#EDE7F6"); // Light purple
            } else if (Reminder.TYPE_CHECKUP.equalsIgnoreCase(type)) {
                iconRes = R.drawable.ic_reminder_checkup;
                tintColor = Color.parseColor("#1976D2");
                bgColor = Color.parseColor("#E3F2FD"); // Light blue
            } else if (Reminder.TYPE_DEWORMING.equalsIgnoreCase(type)) {
                iconRes = R.drawable.ic_reminder_deworming;
                tintColor = Color.parseColor("#00897B");
                bgColor = Color.parseColor("#E0F2F1"); // Light teal
            } else if (Reminder.TYPE_MEDICATION.equalsIgnoreCase(type)) {
                iconRes = R.drawable.ic_reminder_medication;
                tintColor = Color.parseColor("#FFA000");
                bgColor = Color.parseColor("#FFF8E1"); // Light amber
            } else {
                iconRes = R.drawable.ic_ps_notifications;
                tintColor = ContextCompat.getColor(context, R.color.ps_primary);
                bgColor = Color.parseColor("#EDE7F6");
            }

            binding.ivCategoryIcon.setImageResource(iconRes);
            binding.ivCategoryIcon.setColorFilter(tintColor);
            binding.categoryIconBg.getBackground().setTint(bgColor);
        }

        private void configureStatusStyle(Context context, String status) {
            binding.tvStatusPill.setText(status);

            if (Reminder.STATUS_DUE_TODAY.equalsIgnoreCase(status)) {
                binding.tvStatusPill.setBackgroundResource(R.drawable.ps_bg_pill_due_today);
                binding.tvStatusPill.setTextColor(ContextCompat.getColor(context, R.color.pd_amber_text));
            } else if (Reminder.STATUS_OVERDUE.equalsIgnoreCase(status)) {
                binding.tvStatusPill.setBackgroundResource(R.drawable.ps_bg_pill_overdue);
                binding.tvStatusPill.setTextColor(ContextCompat.getColor(context, R.color.pd_red_text));
            } else if (Reminder.STATUS_COMPLETED.equalsIgnoreCase(status)) {
                binding.tvStatusPill.setBackgroundResource(R.drawable.ps_bg_pill_completed);
                binding.tvStatusPill.setTextColor(ContextCompat.getColor(context, R.color.pd_green_text));
            } else { // Upcoming
                binding.tvStatusPill.setBackgroundResource(R.drawable.ps_bg_pill_upcoming);
                binding.tvStatusPill.setTextColor(ContextCompat.getColor(context, R.color.pd_primary));
            }
        }

        private void showPopupMenu(View view, Reminder reminder) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenu().add(0, 1, 0, "Edit Reminder");
            popup.getMenu().add(0, 2, 1, "Snooze 1 Hour");
            popup.getMenu().add(0, 3, 2, "Delete Reminder");

            popup.setOnMenuItemClickListener(item -> {
                if (listener == null) return false;
                switch (item.getItemId()) {
                    case 1:
                        listener.onEditClick(reminder);
                        return true;
                    case 2:
                        listener.onSnoozeClick(reminder);
                        return true;
                    case 3:
                        listener.onDeleteClick(reminder);
                        return true;
                }
                return false;
            });
            popup.show();
        }
    }
}
