package com.edps.dto;

public record MlPredictionResult(
        String diseaseType,
        String predictionResult,
        String riskLevel,
        Double probability) {
}
