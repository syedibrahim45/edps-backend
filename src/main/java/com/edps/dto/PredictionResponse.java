package com.edps.dto;

import java.time.LocalDateTime;

public record PredictionResponse(
        Long id,
        String diseaseType,
        String predictionResult,
        String riskLevel,
        Double probability,
        LocalDateTime createdAt,
        PatientResponse patient) {
}
