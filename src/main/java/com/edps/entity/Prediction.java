package com.edps.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "predictions")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String diseaseType;

    private String predictionResult;

    private String riskLevel;

    private Double probability;

    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    private Patient patient;

    public Prediction() {
    }

    public Prediction(String diseaseType, String predictionResult,
                      String riskLevel, Double probability,
                      LocalDateTime createdAt, Patient patient) {
        this.diseaseType = diseaseType;
        this.predictionResult = predictionResult;
        this.riskLevel = riskLevel;
        this.probability = probability;
        this.createdAt = createdAt;
        this.patient = patient;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiseaseType() {
        return diseaseType;
    }

    public void setDiseaseType(String diseaseType) {
        this.diseaseType = diseaseType;
    }

    public String getPredictionResult() {
        return predictionResult;
    }

    public void setPredictionResult(String predictionResult) {
        this.predictionResult = predictionResult;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getProbability() {
        return probability;
    }

    public void setProbability(Double probability) {
        this.probability = probability;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }
}