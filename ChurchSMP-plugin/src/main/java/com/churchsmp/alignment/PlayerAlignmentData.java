package com.churchsmp.alignment;

import java.util.UUID;

/**
 * Holds a single player's alignment score plus a couple of daily caps
 * (tithing, altar prayer) that reset each Minecraft day.
 */
public class PlayerAlignmentData {

    private final UUID uuid;
    private double score;
    private int tithedToday;
    private int prayersToday;
    private long lastDecayDay;

    public PlayerAlignmentData(UUID uuid, double score) {
        this.uuid = uuid;
        this.score = score;
    }

    public UUID getUuid() {
        return uuid;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score, double min, double max) {
        this.score = Math.max(min, Math.min(max, score));
    }

    public void addPoints(double delta, double min, double max) {
        setScore(this.score + delta, min, max);
    }

    public int getTithedToday() {
        return tithedToday;
    }

    public void addTithedToday(int amount) {
        this.tithedToday += amount;
    }

    public int getPrayersToday() {
        return prayersToday;
    }

    public void incrementPrayersToday() {
        this.prayersToday++;
    }

    public long getLastDecayDay() {
        return lastDecayDay;
    }

    public void setLastDecayDay(long day) {
        this.lastDecayDay = day;
    }

    public void resetDailyCaps() {
        this.tithedToday = 0;
        this.prayersToday = 0;
    }
}
