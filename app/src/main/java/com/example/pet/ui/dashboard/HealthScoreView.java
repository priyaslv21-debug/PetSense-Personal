
package com.example.pet.ui.dashboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.pet.R;

/**
 * Semicircular health-score gauge drawn with Canvas/Paint (no charting library).
 *
 * Public API is unchanged:
 *   setScore(int), setScore(int, String), setStatusText(String), getScore(), setMaxScore(int)
 * Added:
 *   showLabels (XML attr / setShowLabels) - when false (dashboard) only the arc is drawn, because the
 *   score number and status pill are separate views in activity_main.xml.
 *
 * Fixes vs. the previous version:
 *   - The sweep gradient now spans the visible arc (180deg..360deg). Before, both visible stops were the
 *     same colour, so the arc rendered as a solid colour.
 *   - The animator is cancelled when the view detaches.
 */
public class HealthScoreView extends View {

    private static final int DEFAULT_MAX_SCORE = 100;
    private static final int ANIM_DURATION_MS = 700;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private int targetScore = 0;
    private int maxScore = DEFAULT_MAX_SCORE;
    private float displayedScore = 0f; // animates towards targetScore
    private String statusText = "";
    private float strokeWidthPx;
    private boolean showLabels = true;

    @ColorInt private int gaugeStartColor;
    @ColorInt private int gaugeEndColor;
    @ColorInt private int trackColor;

    @Nullable private ValueAnimator animator;

    public HealthScoreView(Context context) {
        this(context, null);
    }

    public HealthScoreView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HealthScoreView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        gaugeStartColor = ContextCompat.getColor(context, R.color.pd_gauge_start);
        gaugeEndColor = ContextCompat.getColor(context, R.color.pd_gauge_end);
        trackColor = ContextCompat.getColor(context, R.color.pd_gauge_track);

        strokeWidthPx = getResources().getDimension(R.dimen.pd_gauge_stroke_width);
        statusText = getResources().getString(R.string.ps_health_score_status_good);

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs, R.styleable.HealthScoreView, 0, 0);
            try {
                targetScore = a.getInt(R.styleable.HealthScoreView_score, 0);
                maxScore = Math.max(1, a.getInt(R.styleable.HealthScoreView_maxScore, DEFAULT_MAX_SCORE));
                String attrStatus = a.getString(R.styleable.HealthScoreView_statusText);
                if (attrStatus != null) {
                    statusText = attrStatus;
                }
                strokeWidthPx = a.getDimension(R.styleable.HealthScoreView_gaugeStrokeWidth, strokeWidthPx);
                showLabels = a.getBoolean(R.styleable.HealthScoreView_showLabels, true);
            } finally {
                a.recycle();
            }
        }
        displayedScore = targetScore;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(strokeWidthPx);
        trackPaint.setColor(trackColor);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(strokeWidthPx);

        percentTextPaint.setColor(ContextCompat.getColor(context, R.color.ps_text_primary));
        percentTextPaint.setTextAlign(Paint.Align.CENTER);
        percentTextPaint.setFakeBoldText(true);

        statusTextPaint.setColor(ContextCompat.getColor(context, R.color.ps_text_secondary));
        statusTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Sets the score (0..maxScore) and animates the arc to it. */
    public void setScore(int score) {
        setScore(score, null);
    }

    /** Same as {@link #setScore(int)} but also updates the status label (only drawn when showLabels is true). */
    public void setScore(int score, @Nullable String newStatusText) {
        int clamped = Math.max(0, Math.min(score, maxScore));
        if (newStatusText != null) {
            this.statusText = newStatusText;
        }
        animateTo(clamped);
        this.targetScore = clamped;
    }

    public void setStatusText(String text) {
        this.statusText = text;
        invalidate();
    }

    public int getScore() {
        return targetScore;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = Math.max(1, maxScore);
        invalidate();
    }

    public void setShowLabels(boolean showLabels) {
        this.showLabels = showLabels;
        invalidate();
    }

    private void animateTo(int newScore) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(displayedScore, newScore);
        animator.setDuration(ANIM_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            displayedScore = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        // Semicircle: usable height is roughly half the width, plus room for the optional labels.
        int desiredHeight = (int) (width * (showLabels ? 0.62f : 0.55f));
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float s = strokeWidthPx;
        float availW = w - getPaddingLeft() - getPaddingRight();
        float availH = h - getPaddingTop() - getPaddingBottom();

        // Centre-line radius: the round caps (s/2) must stay inside the view horizontally and vertically.
        float radius = Math.max(1f, Math.min(availW / 2f - s / 2f, availH - s));
        float cx = getPaddingLeft() + availW / 2f;
        float cy = getPaddingTop() + radius + s / 2f;
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Visible arc covers sweep positions 0.5 (left, 180deg) .. 1.0 (right, 360deg):
        // start colour at the left end, fading to the end colour at the right end.
        SweepGradient gradient = new SweepGradient(
                cx, cy,
                new int[]{gaugeStartColor, gaugeStartColor, gaugeEndColor},
                new float[]{0f, 0.5f, 1f});
        progressPaint.setShader(gradient);

        percentTextPaint.setTextSize(radius * 0.55f);
        statusTextPaint.setTextSize(radius * 0.24f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Background track: full semicircle from 180deg to 360deg.
        canvas.drawArc(arcBounds, 180f, 180f, false, trackPaint);

        float fraction = maxScore == 0 ? 0f : Math.min(1f, displayedScore / maxScore);
        float sweep = 180f * fraction;
        if (sweep > 0.5f) { // skip a lone round-cap dot when the score is 0
            canvas.drawArc(arcBounds, 180f, sweep, false, progressPaint);
        }

        if (!showLabels) return;

        float centerX = arcBounds.centerX();
        float centerY = arcBounds.centerY();
        String percentLabel = Math.round(displayedScore) + "%";
        float percentBaselineY = centerY - arcBounds.height() * 0.04f;
        canvas.drawText(percentLabel, centerX, percentBaselineY, percentTextPaint);

        if (statusText != null && !statusText.isEmpty()) {
            canvas.drawText(statusText, centerX,
                    percentBaselineY + statusTextPaint.getTextSize() + dp(4), statusTextPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        displayedScore = targetScore;
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}


