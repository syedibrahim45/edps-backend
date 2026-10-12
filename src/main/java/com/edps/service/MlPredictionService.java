package com.edps.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class MlPredictionService {

    private final RestClient restClient;

    public MlPredictionService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("http://localhost:8001")
                .build();
    }

    public Map<String, Object> predict(Map<String, Object> patientData) {

        return restClient.post()
                .uri("/predict")
                .body(patientData)
                .retrieve()
                .body(Map.class);
    }
}