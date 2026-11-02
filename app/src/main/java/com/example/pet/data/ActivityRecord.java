package com.example.pet.data;

import androidx.annotation.Keep;
import java.util.Date;

@Keep
public class ActivityRecord {
    private String id;
    private Date date;
    private String activity;
    private double sleep;
    private String nutrition;
    private String behavior;
    private int healthScore; // 🔴 NEW: Stores the calculated baseline score

    public ActivityRecord() {}

    public ActivityRecord(Date date, String activity, double sleep, String nutrition, String behavior, int healthScore) {
        this.date = date;
        this.activity = activity;
        this.sleep = sleep;
        this.nutrition = nutrition;
        this.behavior = behavior;
        this.healthScore = healthScore;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }
    public double getSleep() { return sleep; }
    public void setSleep(double sleep) { this.sleep = sleep; }
    public String getNutrition() { return nutrition; }
    public void setNutrition(String nutrition) { this.nutrition = nutrition; }
    public String getBehavior() { return behavior; }
    public void setBehavior(String behavior) { this.behavior = behavior; }
    public int getHealthScore() { return healthScore; }
    public void setHealthScore(int healthScore) { this.healthScore = healthScore; }
}