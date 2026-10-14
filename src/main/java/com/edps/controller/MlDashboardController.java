package com.edps.controller;

import com.edps.service.MlPredictionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/ml")
public class MlDashboardController {

    private final MlPredictionService mlPredictionService;

    public MlDashboardController(MlPredictionService mlPredictionService) {
        this.mlPredictionService = mlPredictionService;
    }

    @GetMapping("/health")
    public JsonNode health() {
        return mlPredictionService.getHealth();
    }

    @GetMapping("/explainability/global")
    public JsonNode globalExplainability() {
        return mlPredictionService.getGlobalExplainability();
    }

    @PostMapping("/explainability/individual")
    public JsonNode individualExplainability(@RequestBody JsonNode request) {
        return mlPredictionService.explainIndividual(request);
    }

    @GetMapping("/federated/results")
    public JsonNode federatedResults() {
        return mlPredictionService.getFederatedResults();
    }
}
