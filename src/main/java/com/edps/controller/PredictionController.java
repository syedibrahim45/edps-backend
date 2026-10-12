package com.edps.controller;

import com.edps.entity.Prediction;
import com.edps.service.PredictionService;
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
    public Prediction createPrediction(@RequestBody Prediction prediction) {
        return predictionService.createPrediction(prediction);
    }

    @GetMapping
    public List<Prediction> getAllPredictions() {
        return predictionService.getAllPredictions();
    }

    @GetMapping("/{id}")
    public Prediction getPredictionById(@PathVariable Long id) {
        return predictionService.getPredictionById(id);
    }

    @PutMapping("/{id}")
    public Prediction updatePrediction(
            @PathVariable Long id,
            @RequestBody Prediction prediction) {

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