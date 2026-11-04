package com.example.pet.ui.lab;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pet.R;
import com.example.pet.data.LabTestItem;
import com.example.pet.lab.LabRuleEngine;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter powering the interactive OCR Verification Screen.
 * Allows users to review, edit, add, or delete test parameters.
 */
public class LabOcrEditAdapter extends RecyclerView.Adapter<LabOcrEditAdapter.ViewHolder> {

    public interface OnItemDeletedListener {
        void onItemDeleted(int position);
    }

    private List<LabTestItem> items = new ArrayList<>();
    private final OnItemDeletedListener deleteListener;

    public LabOcrEditAdapter(OnItemDeletedListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setItems(List<LabTestItem> newItems) {
        this.items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<LabTestItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lab_ocr_edit_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LabTestItem item = items.get(position);

        holder.bind(item, pos -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION && currentPos < items.size()) {
                items.remove(currentPos);
                notifyItemRemoved(currentPos);
                if (deleteListener != null) {
                    deleteListener.onItemDeleted(currentPos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextInputEditText etTestName;
        private final TextInputEditText etTestValue;
        private final TextInputEditText etTestUnit;
        private final TextInputEditText etTestRange;
        private final TextView tvRowStatusBadge;
        private final ImageButton btnDeleteRow;

        private TextWatcher nameWatcher;
        private TextWatcher valueWatcher;
        private TextWatcher unitWatcher;
        private TextWatcher rangeWatcher;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            etTestName = itemView.findViewById(R.id.etTestName);
            etTestValue = itemView.findViewById(R.id.etTestValue);
            etTestUnit = itemView.findViewById(R.id.etTestUnit);
            etTestRange = itemView.findViewById(R.id.etTestRange);
            tvRowStatusBadge = itemView.findViewById(R.id.tvRowStatusBadge);
            btnDeleteRow = itemView.findViewById(R.id.btnDeleteRow);
        }

        void bind(LabTestItem item, View.OnClickListener onDeleteClick) {
            // Remove previous watchers to avoid recycling feedback loops
            if (nameWatcher != null) etTestName.removeTextChangedListener(nameWatcher);
            if (valueWatcher != null) etTestValue.removeTextChangedListener(valueWatcher);
            if (unitWatcher != null) etTestUnit.removeTextChangedListener(unitWatcher);
            if (rangeWatcher != null) etTestRange.removeTextChangedListener(rangeWatcher);

            etTestName.setText(item.getTestName());
            etTestValue.setText(item.getValue());
            etTestUnit.setText(item.getUnit());
            etTestRange.setText(item.getRefRangeText());

            updateStatusBadge(item);

            btnDeleteRow.setOnClickListener(onDeleteClick);

            nameWatcher = new SimpleTextWatcher(text -> {
                item.setTestName(text);
                item.setCategory(LabRuleEngine.inferCategory(text));
            });
            valueWatcher = new SimpleTextWatcher(text -> {
                item.setValue(text);
                try {
                    item.setNumericValue(Double.parseDouble(text));
                } catch (Exception ignored) {
                    item.setNumericValue(null);
                }
                LabRuleEngine.classifyTestItem(item);
                updateStatusBadge(item);
            });
            unitWatcher = new SimpleTextWatcher(item::setUnit);
            rangeWatcher = new SimpleTextWatcher(text -> {
                item.setRefRangeText(text);
                LabRuleEngine.classifyTestItem(item);
                updateStatusBadge(item);
            });

            etTestName.addTextChangedListener(nameWatcher);
            etTestValue.addTextChangedListener(valueWatcher);
            etTestUnit.addTextChangedListener(unitWatcher);
            etTestRange.addTextChangedListener(rangeWatcher);
        }

        private void updateStatusBadge(LabTestItem item) {
            String status = item.getStatus() != null ? item.getStatus() : LabTestItem.STATUS_UNCLASSIFIED;
            tvRowStatusBadge.setText(status);

            int textColor;
            if (LabTestItem.STATUS_HIGH.equalsIgnoreCase(status)) {
                textColor = ContextCompat.getColor(itemView.getContext(), R.color.pd_red_text);
            } else if (LabTestItem.STATUS_LOW.equalsIgnoreCase(status)) {
                textColor = ContextCompat.getColor(itemView.getContext(), R.color.pd_amber_text);
            } else if (LabTestItem.STATUS_NORMAL.equalsIgnoreCase(status)) {
                textColor = ContextCompat.getColor(itemView.getContext(), R.color.pd_green_text);
            } else {
                textColor = ContextCompat.getColor(itemView.getContext(), R.color.pd_gray_text);
            }
            tvRowStatusBadge.setTextColor(textColor);
        }
    }

    private static class SimpleTextWatcher implements TextWatcher {
        interface OnChanged { void onChange(String text); }
        private final OnChanged callback;
        SimpleTextWatcher(OnChanged callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { callback.onChange(s != null ? s.toString().trim() : ""); }
    }
}
