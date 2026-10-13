package com.edps.controller;

import com.edps.dto.PredictionRequest;
import com.edps.dto.PredictionResponse;
import com.edps.entity.Prediction;
import com.edps.service.PredictionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping
    public PredictionResponse createPrediction(@Valid @RequestBody PredictionRequest prediction) {
        return predictionService.createPrediction(prediction);
    }

    @GetMapping
    public List<PredictionResponse> getAllPredictions() {
        return predictionService.getAllPredictions();
    }

    @GetMapping("/patient/{patientId}")
    public List<PredictionResponse> getPredictionsByPatientId(@PathVariable Long patientId) {
        return predictionService.getPredictionsByPatientId(patientId);
    }

    @GetMapping("/{id}")
    public PredictionResponse getPredictionById(@PathVariable Long id) {
        return predictionService.getPredictionById(id);
    }

    @PutMapping("/{id}")
    public PredictionResponse updatePrediction(
            @PathVariable Long id,
            @Valid @RequestBody Prediction prediction) {

        return predictionService.updatePrediction(id, prediction);
    }

    @DeleteMapping("/{id}")
    public String deletePrediction(@PathVariable Long id) {

        boolean deleted = predictionService.deletePrediction(id);

        if (deleted) {
            return "Prediction deleted successfully";
        }

        return "Prediction not found";
    }
}