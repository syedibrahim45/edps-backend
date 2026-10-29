package com.edps.dto;

public record DiabetesScreeningResponse(
        int predicted_class,
        String predicted_label,
        double positive_class_score,
        double threshold,
        String model_id,
        String model_version,
        String disclaimer) {
}
