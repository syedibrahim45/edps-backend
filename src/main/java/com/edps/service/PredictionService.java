package com.edps.service;

import com.edps.entity.Patient;
import com.edps.entity.Prediction;
import com.edps.repository.PatientRepository;
import com.edps.repository.PredictionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final PatientRepository patientRepository;
    private final MlPredictionService mlPredictionService;

    public PredictionService(
            PredictionRepository predictionRepository,
            PatientRepository patientRepository,
            MlPredictionService mlPredictionService) {

        this.predictionRepository = predictionRepository;
        this.patientRepository = patientRepository;
        this.mlPredictionService = mlPredictionService;
    }

    public Prediction createPrediction(Prediction prediction) {

        Patient patient = prediction.getPatient();

        if (patient == null) {
            throw new RuntimeException("Patient information is required");
        }

        // Save patient first
        Patient savedPatient = patientRepository.save(patient);
        prediction.setPatient(savedPatient);

        // Prepare patient data for ML service
        Map<String, Object> patientData = new HashMap<>();

        patientData.put("age", patient.getAge());
        patientData.put("gender", Integer.parseInt(patient.getGender()));
        patientData.put("blood_pressure", patient.getBloodPressure());
        patientData.put("glucose", patient.getGlucose());
        patientData.put("bmi", patient.getBmi());
        patientData.put("heart_rate", patient.getHeartRate());
        patientData.put("cholesterol", patient.getCholesterol());

        // Call Python ML service
        Map<String, Object> mlResult =
                mlPredictionService.predict(patientData);

        // Get ML prediction result
        Object predictionResult = mlResult.get("prediction");
        Object disease = mlResult.get("disease");
        Object probability = mlResult.get("probability");

        // Store ML result in Prediction entity
        prediction.setPredictionResult(String.valueOf(predictionResult));
        prediction.setRiskLevel(String.valueOf(disease));
        prediction.setProbability(
                Double.valueOf(String.valueOf(probability))
        );

        prediction.setDiseaseType("Disease Risk Prediction");

        prediction.setCreatedAt(LocalDateTime.now());

        // Save prediction
        return predictionRepository.save(prediction);
    }

    public List<Prediction> getAllPredictions() {
        return predictionRepository.findAll();
    }

    public Prediction getPredictionById(Long id) {
        return predictionRepository.findById(id).orElse(null);
    }

    public Prediction updatePrediction(Long id, Prediction prediction) {

        Prediction existingPrediction =
                predictionRepository.findById(id).orElse(null);

        if (existingPrediction == null) {
            return null;
        }

        existingPrediction.setDiseaseType(prediction.getDiseaseType());
        existingPrediction.setPredictionResult(prediction.getPredictionResult());
        existingPrediction.setRiskLevel(prediction.getRiskLevel());
        existingPrediction.setProbability(prediction.getProbability());

        return predictionRepository.save(existingPrediction);
    }

    public boolean deletePrediction(Long id) {

        Prediction existingPrediction =
                predictionRepository.findById(id).orElse(null);

        if (existingPrediction == null) {
            return false;
        }

        predictionRepository.delete(existingPrediction);
        return true;
    }
}