package com.example.pet.ui.behavior;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pet.R;

import java.util.List;

public class BehaviorAdapter extends RecyclerView.Adapter<BehaviorAdapter.ViewHolder> {

    // A simple list of behaviors to display
    private List<String> behaviorList;

    public BehaviorAdapter(List<String> behaviorList) {
        this.behaviorList = behaviorList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // IMPORTANT: Replace "item_behavior_analysis" with the actual name of your XML file
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_behavior_analysis, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // This puts the text into the specific row
        String currentBehavior = behaviorList.get(position);
        holder.tvItemBehavior.setText(currentBehavior);

        // You can add the real emoji, time, and confidence data here later
    }

    @Override
    public int getItemCount() {
        return behaviorList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvItemEmoji, tvItemBehavior, tvItemTime, tvItemConfidence;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Connects the Java variables to your XML IDs
            tvItemEmoji = itemView.findViewById(R.id.tvItemEmoji);
            tvItemBehavior = itemView.findViewById(R.id.tvItemBehavior);
            tvItemTime = itemView.findViewById(R.id.tvItemTime);
            tvItemConfidence = itemView.findViewById(R.id.tvItemConfidence);
        }
    }
}