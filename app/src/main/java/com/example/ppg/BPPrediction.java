package com.example.ppg;

public class BPPrediction {
    public float systolic;
    public float diastolic;
    public String category;
    public float confidence;
    
    public BPPrediction() {
        this.systolic = 0.0f;
        this.diastolic = 0.0f;
        this.category = "Unknown";
        this.confidence = 0.0f;
    }
}
