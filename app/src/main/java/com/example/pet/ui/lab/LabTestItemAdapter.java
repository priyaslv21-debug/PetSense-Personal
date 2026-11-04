package com.example.pet.ui.lab;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pet.R;
import com.example.pet.data.LabTestItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter presenting verified clinical lab results in LabReportDetailActivity
 * with expandable AI explanations and color-coded status badges.
 */
public class LabTestItemAdapter extends RecyclerView.Adapter<LabTestItemAdapter.ViewHolder> {

    private List<LabTestItem> allItems = new ArrayList<>();
    private List<LabTestItem> displayedItems = new ArrayList<>();

    public void setItems(List<LabTestItem> newItems) {
        this.allItems = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        this.displayedItems = new ArrayList<>(this.allItems);
        notifyDataSetChanged();
    }

    public void filter(String filterType) {
        if ("ABNORMAL".equalsIgnoreCase(filterType)) {
            List<LabTestItem> filtered = new ArrayList<>();
            for (LabTestItem item : allItems) {
                if (item.isAbnormal()) filtered.add(item);
            }
            this.displayedItems = filtered;
        } else if ("NORMAL".equalsIgnoreCase(filterType)) {
            List<LabTestItem> filtered = new ArrayList<>();
            for (LabTestItem item : allItems) {
                if (LabTestItem.STATUS_NORMAL.equalsIgnoreCase(item.getStatus())) filtered.add(item);
            }
            this.displayedItems = filtered;
        } else {
            this.displayedItems = new ArrayList<>(allItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lab_test_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LabTestItem item = displayedItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return displayedItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTestName;
        private final TextView tvTestCategory;
        private final TextView tvStatusPill;
        private final TextView tvMeasuredValue;
        private final TextView tvUnit;
        private final TextView tvRefRange;
        private final TextView tvToggleExplanation;
        private final View layoutAiExplanation;
        private final TextView tvAiExplanation;
        private final TextView lblSignificance;
        private final TextView tvClinicalSignificance;
        private final TextView lblFollowUp;
        private final TextView tvRecommendedFollowUp;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTestName = itemView.findViewById(R.id.tvTestName);
            tvTestCategory = itemView.findViewById(R.id.tvTestCategory);
            tvStatusPill = itemView.findViewById(R.id.tvStatusPill);
            tvMeasuredValue = itemView.findViewById(R.id.tvMeasuredValue);
            tvUnit = itemView.findViewById(R.id.tvUnit);
            tvRefRange = itemView.findViewById(R.id.tvRefRange);
            tvToggleExplanation = itemView.findViewById(R.id.tvToggleExplanation);
            layoutAiExplanation = itemView.findViewById(R.id.layoutAiExplanation);
            tvAiExplanation = itemView.findViewById(R.id.tvAiExplanation);
            lblSignificance = itemView.findViewById(R.id.lblSignificance);
            tvClinicalSignificance = itemView.findViewById(R.id.tvClinicalSignificance);
            lblFollowUp = itemView.findViewById(R.id.lblFollowUp);
            tvRecommendedFollowUp = itemView.findViewById(R.id.tvRecommendedFollowUp);
        }

        void bind(LabTestItem item) {
            tvTestName.setText(item.getTestName());
            tvTestCategory.setText(item.getCategory() != null ? item.getCategory() : "General Vitals");
            tvMeasuredValue.setText(item.getValue());
            tvUnit.setText(item.getUnit());

            String range = item.getRefRangeText();
            if (range != null && !range.trim().isEmpty()) {
                tvRefRange.setText("Report Range: " + range + " " + (item.getUnit() != null ? item.getUnit() : ""));
                tvRefRange.setVisibility(View.VISIBLE);
            } else {
                tvRefRange.setText("No range provided in report");
                tvRefRange.setVisibility(View.VISIBLE);
            }

            // Status Badge Styling
            String status = item.getStatus() != null ? item.getStatus() : LabTestItem.STATUS_UNCLASSIFIED;
            tvStatusPill.setText(status);

            if (LabTestItem.STATUS_HIGH.equalsIgnoreCase(status)) {
                tvStatusPill.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.pd_red_text));
                tvStatusPill.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.pd_red_bg));
            } else if (LabTestItem.STATUS_LOW.equalsIgnoreCase(status)) {
                tvStatusPill.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.pd_amber_text));
                tvStatusPill.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.pd_amber_bg));
            } else if (LabTestItem.STATUS_NORMAL.equalsIgnoreCase(status)) {
                tvStatusPill.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.pd_green_text));
                tvStatusPill.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.pd_green_bg));
            } else {
                tvStatusPill.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.pd_gray_text));
                tvStatusPill.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.pd_gray_bg));
            }

            // Expandable AI Explanation & Clinical Significance
            String explanation = item.getAiExplanation();
            String significance = item.getClinicalSignificance();
            String followUp = item.getRecommendedFollowUp();

            boolean hasAiContent = (explanation != null && !explanation.trim().isEmpty())
                    || (significance != null && !significance.trim().isEmpty())
                    || (followUp != null && !followUp.trim().isEmpty());

            if (hasAiContent) {
                if (explanation != null && !explanation.trim().isEmpty()) {
                    tvAiExplanation.setText(explanation);
                    tvAiExplanation.setVisibility(View.VISIBLE);
                } else {
                    tvAiExplanation.setVisibility(View.GONE);
                }

                if (significance != null && !significance.trim().isEmpty()) {
                    tvClinicalSignificance.setText(significance);
                    lblSignificance.setVisibility(View.VISIBLE);
                    tvClinicalSignificance.setVisibility(View.VISIBLE);
                } else {
                    lblSignificance.setVisibility(View.GONE);
                    tvClinicalSignificance.setVisibility(View.GONE);
                }

                if (followUp != null && !followUp.trim().isEmpty()) {
                    tvRecommendedFollowUp.setText(followUp);
                    lblFollowUp.setVisibility(View.VISIBLE);
                    tvRecommendedFollowUp.setVisibility(View.VISIBLE);
                } else {
                    lblFollowUp.setVisibility(View.GONE);
                    tvRecommendedFollowUp.setVisibility(View.GONE);
                }

                tvToggleExplanation.setVisibility(View.VISIBLE);

                // Auto-expand abnormal items by default, keep normal collapsed
                boolean autoExpand = item.isAbnormal();
                layoutAiExplanation.setVisibility(autoExpand ? View.VISIBLE : View.GONE);
                tvToggleExplanation.setText(autoExpand ? "💡 Clinical Context ▲" : "💡 What this means ▼");

                tvToggleExplanation.setOnClickListener(v -> {
                    boolean currentlyVisible = layoutAiExplanation.getVisibility() == View.VISIBLE;
                    layoutAiExplanation.setVisibility(currentlyVisible ? View.GONE : View.VISIBLE);
                    tvToggleExplanation.setText(currentlyVisible ? "💡 What this means ▼" : "💡 Clinical Context ▲");
                });
            } else {
                tvToggleExplanation.setVisibility(View.GONE);
                layoutAiExplanation.setVisibility(View.GONE);
            }
        }
    }
}
