package com.example.ppg;

public class PPGData {
    public long timestamp;
    public float ppgValue;
    public float heartRate;
    public String signalQuality;
    
    public PPGData() {
        this.timestamp = System.currentTimeMillis();
        this.ppgValue = 0.0f;
        this.heartRate = 0.0f;
        this.signalQuality = "Poor";
    }
}
