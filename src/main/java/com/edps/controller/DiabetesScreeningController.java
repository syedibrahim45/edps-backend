package com.edps.controller;

import com.edps.dto.DiabetesScreeningRequest;
import com.edps.dto.DiabetesScreeningResponse;
import com.edps.service.MlPredictionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/diabetes-screenings")
public class DiabetesScreeningController {

    private final MlPredictionService mlPredictionService;

    public DiabetesScreeningController(MlPredictionService mlPredictionService) {
        this.mlPredictionService = mlPredictionService;
    }

    @PostMapping
    public DiabetesScreeningResponse screen(@RequestBody JsonNode body) {
        DiabetesScreeningRequest request = DiabetesScreeningRequest.fromJson(body);
        return mlPredictionService.predictDiabetes(request);
    }
}
