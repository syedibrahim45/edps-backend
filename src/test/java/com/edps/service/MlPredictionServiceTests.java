package com.edps.service;

import com.edps.dto.MlPredictionResult;
import com.edps.exception.MlServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MlPredictionServiceTests {

    @Test
    void mapsLegacyFastApiResponse() {
        MlPredictionResult result = MlPredictionService.parseResponse(Map.of(
                "prediction", 1,
                "disease", "High Risk",
                "probability", 0.82));

        assertEquals("Disease Risk Prediction", result.diseaseType());
        assertEquals("1", result.predictionResult());
        assertEquals("High Risk", result.riskLevel());
        assertEquals(0.82, result.probability());
    }

    @Test
    void mapsExplicitDiseaseTypeAndRiskClassification() {
        MlPredictionResult result = MlPredictionService.parseResponse(Map.of(
                "prediction", 0,
                "disease_type", "Diabetes",
                "risk_level", "Low Risk",
                "probability", "0.15"));

        assertEquals("Diabetes", result.diseaseType());
        assertEquals("0", result.predictionResult());
        assertEquals("Low Risk", result.riskLevel());
        assertEquals(0.15, result.probability());
    }

    @Test
    void rejectsInvalidProbabilityAsBadGateway() {
        MlServiceException exception = assertThrows(
                MlServiceException.class,
                () -> MlPredictionService.parseResponse(Map.of(
                        "prediction", 1,
                        "risk_level", "High Risk",
                        "probability", 1.2)));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }
}
