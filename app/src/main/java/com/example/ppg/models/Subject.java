package com.example.ppg.models;

public class Subject {
    private String subjectId;
    private int subjectNumber;
    private String subjectName;
    private Integer age;
    private String gender;
    private String notes;
    private String createdAt;
    private String lastMeasurement;
    private int totalMeasurements;

    // Constructors
    public Subject() {}

    public Subject(String subjectId, int subjectNumber, String subjectName) {
        this.subjectId = subjectId;
        this.subjectNumber = subjectNumber;
        this.subjectName = subjectName;
    }

    // Getters and Setters
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public int getSubjectNumber() { return subjectNumber; }
    public void setSubjectNumber(int subjectNumber) { this.subjectNumber = subjectNumber; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getLastMeasurement() { return lastMeasurement; }
    public void setLastMeasurement(String lastMeasurement) { this.lastMeasurement = lastMeasurement; }

    public int getTotalMeasurements() { return totalMeasurements; }
    public void setTotalMeasurements(int totalMeasurements) { this.totalMeasurements = totalMeasurements; }

    @Override
    public String toString() {
        return subjectId + " - " + subjectName;
    }

    // Display name for spinner
    public String getDisplayName() {
        return String.format("%s - %s (%d measurements)", 
            subjectId, subjectName, totalMeasurements);
    }
}
