package com.edps.dto;

import jakarta.validation.constraints.Positive;

public class PredictionRequest {

    @Positive
    private Long patientId;

    private PatientRequest patient;

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public PatientRequest getPatient() {
        return patient;
    }

    public void setPatient(PatientRequest patient) {
        this.patient = patient;
    }
}
