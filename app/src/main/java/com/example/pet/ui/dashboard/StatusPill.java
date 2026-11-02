package com.example.pet.ui.dashboard;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.pet.R;

public class StatusPill {

    public static void apply(TextView textView, String status) {
        if (textView == null) return;
        String safeStatus = status != null ? status : "";

        textView.setText(safeStatus);

        Context context = textView.getContext();
        String lower = safeStatus.toLowerCase().trim();

        int bgColor;
        int textColor;
        int strokeColor;

        if (lower.contains("optimal") || lower.contains("good") || lower.contains("healthy") || lower.contains("normal") || lower.contains("active")) {
            bgColor = R.color.pd_green_bg;
            textColor = R.color.pd_green_text;
            strokeColor = R.color.pd_green_stroke;
        } else if (lower.contains("fair") || lower.contains("warning") || lower.contains("attention") || lower.contains("elevated") || lower.contains("low")) {
            bgColor = R.color.pd_amber_bg;
            textColor = R.color.pd_amber_text;
            strokeColor = R.color.pd_amber_stroke;
        } else if (lower.contains("poor") || lower.contains("critical") || lower.contains("high") || lower.contains("alert")) {
            bgColor = R.color.pd_red_bg;
            textColor = R.color.pd_red_text;
            strokeColor = R.color.pd_red_stroke;
        } else if (lower.contains("resting") || lower.contains("sleep") || lower.contains("deep")) {
            bgColor = R.color.pd_purple_bg;
            textColor = R.color.pd_purple_text;
            strokeColor = R.color.pd_purple_stroke;
        } else {
            bgColor = R.color.pd_gray_bg;
            textColor = R.color.pd_gray_text;
            strokeColor = R.color.pd_gray_stroke;
        }

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dpToPx(context, 16));
        shape.setColor(ContextCompat.getColor(context, bgColor));
        shape.setStroke(dpToPx(context, 1), ContextCompat.getColor(context, strokeColor));

        textView.setBackground(shape);
        textView.setTextColor(ContextCompat.getColor(context, textColor));
    }

    private static int dpToPx(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
