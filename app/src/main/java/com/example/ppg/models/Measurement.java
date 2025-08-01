package com.example.ppg.models;

public class Measurement {
    private String measurementId;
    private String subjectId;
    private int heartRate;
    private int heartRateConfidence;
    private String signalQuality;
    private String bpCategory;
    private Integer bpConfidence;
    private int measurementDuration;
    private int frameCount;
    private String timestamp;

    // Constructors
    public Measurement() {}

    public Measurement(String subjectId, int heartRate, int heartRateConfidence, 
                      String signalQuality, String bpCategory, Integer bpConfidence,
                      int measurementDuration, int frameCount) {
        this.subjectId = subjectId;
        this.heartRate = heartRate;
        this.heartRateConfidence = heartRateConfidence;
        this.signalQuality = signalQuality;
        this.bpCategory = bpCategory;
        this.bpConfidence = bpConfidence;
        this.measurementDuration = measurementDuration;
        this.frameCount = frameCount;
    }

    // Getters and Setters
    public String getMeasurementId() { return measurementId; }
    public void setMeasurementId(String measurementId) { this.measurementId = measurementId; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public int getHeartRate() { return heartRate; }
    public void setHeartRate(int heartRate) { this.heartRate = heartRate; }

    public int getHeartRateConfidence() { return heartRateConfidence; }
    public void setHeartRateConfidence(int heartRateConfidence) { this.heartRateConfidence = heartRateConfidence; }

    public String getSignalQuality() { return signalQuality; }
    public void setSignalQuality(String signalQuality) { this.signalQuality = signalQuality; }

    public String getBpCategory() { return bpCategory; }
    public void setBpCategory(String bpCategory) { this.bpCategory = bpCategory; }

    public Integer getBpConfidence() { return bpConfidence; }
    public void setBpConfidence(Integer bpConfidence) { this.bpConfidence = bpConfidence; }

    public int getMeasurementDuration() { return measurementDuration; }
    public void setMeasurementDuration(int measurementDuration) { this.measurementDuration = measurementDuration; }

    public int getFrameCount() { return frameCount; }
    public void setFrameCount(int frameCount) { this.frameCount = frameCount; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getBpDisplayText() {
        if (bpCategory == null) return "No BP Data";
        
        switch (bpCategory.toLowerCase()) {
            case "normotensive":
                return "Normal BP";
            case "prehypertensive": 
                return "Pre-Hypertensive";
            case "hypertensive":
                return "Hypertensive";
            default:
                return bpCategory;
        }
    }

    public String getFormattedTimestamp() {
        // You can format this based on your needs
        return timestamp != null ? timestamp.substring(0, 19).replace("T", " ") : "Unknown";
    }
}
