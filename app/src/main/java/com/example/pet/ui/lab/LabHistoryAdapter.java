package com.example.pet.ui.lab;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pet.R;
import com.example.pet.data.LabRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter listing past lab reports in LabHistoryActivity with actions to view, share, or delete.
 */
public class LabHistoryAdapter extends RecyclerView.Adapter<LabHistoryAdapter.ViewHolder> {

    public interface OnReportActionListener {
        void onReportClick(LabRecord record);
        void onReportShare(LabRecord record);
        void onReportDelete(LabRecord record);
    }

    private List<LabRecord> reports = new ArrayList<>();
    private final OnReportActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public LabHistoryAdapter(OnReportActionListener listener) {
        this.listener = listener;
    }

    public void setReports(List<LabRecord> newReports) {
        this.reports = newReports != null ? newReports : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lab_history_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LabRecord record = reports.get(position);
        holder.bind(record, dateFormat, listener);
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvHistoryLabName;
        private final TextView tvHistoryDate;
        private final TextView tvHistoryPetInfo;
        private final TextView tvHistoryFileName;
        private final TextView tvHistoryAbnormalBadge;
        private final TextView tvHistoryTotalBadge;
        private final ImageButton btnHistoryShare;
        private final ImageButton btnHistoryDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHistoryLabName = itemView.findViewById(R.id.tvHistoryLabName);
            tvHistoryDate = itemView.findViewById(R.id.tvHistoryDate);
            tvHistoryPetInfo = itemView.findViewById(R.id.tvHistoryPetInfo);
            tvHistoryFileName = itemView.findViewById(R.id.tvHistoryFileName);
            tvHistoryAbnormalBadge = itemView.findViewById(R.id.tvHistoryAbnormalBadge);
            tvHistoryTotalBadge = itemView.findViewById(R.id.tvHistoryTotalBadge);
            btnHistoryShare = itemView.findViewById(R.id.btnHistoryShare);
            btnHistoryDelete = itemView.findViewById(R.id.btnHistoryDelete);
        }

        void bind(LabRecord record, SimpleDateFormat dateFormat, OnReportActionListener listener) {
            tvHistoryLabName.setText(record.getLabName() != null ? record.getLabName() : "Diagnostics Lab");

            if (record.getReportDate() != null) {
                tvHistoryDate.setText(dateFormat.format(record.getReportDate()));
            } else if (record.getCreatedAt() != null) {
                tvHistoryDate.setText(dateFormat.format(record.getCreatedAt()));
            } else {
                tvHistoryDate.setText("Recent");
            }

            String pet = record.getPetName() != null ? record.getPetName() : "Pet";
            String spec = record.getSpecies() != null ? record.getSpecies() : "Pet";
            tvHistoryPetInfo.setText("🐾 " + pet + " (" + spec + ")");

            String file = record.getFileName() != null ? record.getFileName() : "lab_report.pdf";
            String icon = (record.getFileType() != null && record.getFileType().contains("pdf")) ? "📄 " : "🖼️ ";
            tvHistoryFileName.setText(icon + file);

            int abnormal = record.getAbnormalCount();
            if (abnormal > 0) {
                tvHistoryAbnormalBadge.setText(abnormal + " Abnormal");
                tvHistoryAbnormalBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.pd_red_text));
                tvHistoryAbnormalBadge.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.pd_red_bg));
            } else {
                tvHistoryAbnormalBadge.setText("All Normal");
                tvHistoryAbnormalBadge.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.pd_green_text));
                tvHistoryAbnormalBadge.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.pd_green_bg));
            }

            int total = record.getTotalTestsCount();
            tvHistoryTotalBadge.setText(total + " Tests");
            tvHistoryTotalBadge.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.pd_lavender));

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onReportClick(record);
            });

            btnHistoryShare.setOnClickListener(v -> {
                if (listener != null) listener.onReportShare(record);
            });

            btnHistoryDelete.setOnClickListener(v -> {
                if (listener != null) listener.onReportDelete(record);
            });
        }
    }
}
