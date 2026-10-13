package com.edps.service;

import com.edps.dto.PatientRequest;
import com.edps.dto.PatientResponse;
import com.edps.dto.PredictionRequest;
import com.edps.dto.PredictionResponse;
import com.edps.entity.Patient;
import com.edps.entity.Prediction;
import com.edps.exception.InvalidRequestException;
import com.edps.exception.NotFoundException;
import com.edps.repository.PatientRepository;
import com.edps.repository.PredictionRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final PatientRepository patientRepository;
    private final MlPredictionService mlPredictionService;
    private final IntegrityAuditService integrityAuditService;
    private final Validator validator;

    public PredictionService(
            PredictionRepository predictionRepository,
            PatientRepository patientRepository,
            MlPredictionService mlPredictionService,
            IntegrityAuditService integrityAuditService,
            Validator validator) {
        this.predictionRepository = predictionRepository;
        this.patientRepository = patientRepository;
        this.mlPredictionService = mlPredictionService;
        this.integrityAuditService = integrityAuditService;
        this.validator = validator;
    }

    @Transactional
    public PredictionResponse createPrediction(PredictionRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Prediction request is required.");
        }

        PatientRequest suppliedPatient = request.getPatient();
        Long suppliedPatientId = request.getPatientId();
        Long nestedPatientId = suppliedPatient == null ? null : suppliedPatient.getId();
        if (suppliedPatientId != null && nestedPatientId != null
                && !suppliedPatientId.equals(nestedPatientId)) {
            throw new InvalidRequestException("patientId and patient.id must refer to the same patient.");
        }

        Long patientId = suppliedPatientId == null ? nestedPatientId : suppliedPatientId;
        Patient patient;
        if (patientId == null) {
            validatePatientInput(suppliedPatient, true);
            patient = toPatient(suppliedPatient);
        } else {
            patient = patientRepository.findById(patientId)
                    .orElseThrow(() -> new NotFoundException("Patient " + patientId + " was not found."));
            if (suppliedPatient != null) {
                validatePatientInput(suppliedPatient, false);
            }
        }

        Map<String, Object> features = toFeatures(patient, suppliedPatient);
        var mlResult = mlPredictionService.predict(features);

        if (patient.getId() == null) {
            patient = patientRepository.save(patient);
        }

        Prediction prediction = new Prediction(
                mlResult.diseaseType(),
                mlResult.predictionResult(),
                mlResult.riskLevel(),
                mlResult.probability(),
                LocalDateTime.now(),
                patient);
        Prediction savedPrediction = predictionRepository.save(prediction);
        recordAudit("PREDICTION_CREATED", savedPrediction);
        return toResponse(savedPrediction);
    }

    @Transactional(readOnly = true)
    public List<PredictionResponse> getAllPredictions() {
        return predictionRepository.findAllByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PredictionResponse getPredictionById(Long id) {
        return predictionRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Prediction " + id + " was not found."));
    }

    @Transactional(readOnly = true)
    public List<PredictionResponse> getPredictionsByPatientId(Long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new NotFoundException("Patient " + patientId + " was not found.");
        }
        return predictionRepository.findByPatient_IdOrderByCreatedAtDescIdDesc(patientId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PredictionResponse updatePrediction(Long id, Prediction changes) {
        Prediction existing = predictionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Prediction " + id + " was not found."));

        existing.setDiseaseType(changes.getDiseaseType());
        existing.setPredictionResult(changes.getPredictionResult());
        existing.setRiskLevel(changes.getRiskLevel());
        existing.setProbability(changes.getProbability());
        Prediction saved = predictionRepository.save(existing);
        recordAudit("PREDICTION_UPDATED", saved);
        return toResponse(saved);
    }

    @Transactional
    public boolean deletePrediction(Long id) {
        Prediction existing = predictionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Prediction " + id + " was not found."));
        recordAudit("PREDICTION_DELETED", existing);
        predictionRepository.delete(existing);
        return true;
    }

    private void validatePatientInput(PatientRequest patient, boolean requireNewPatientFields) {
        if (patient == null) {
            throw new InvalidRequestException("Patient details or a patient ID are required.");
        }

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<PatientRequest> violation : validator.validate(patient)) {
            fieldErrors.put("patient." + violation.getPropertyPath(), violation.getMessage());
        }
        if (requireNewPatientFields && isBlank(patient.getName())) {
            fieldErrors.put("patient.name", "is required when creating a new patient");
        }

        if (requireNewPatientFields || hasAnyFeature(patient)) {
            requireValue(fieldErrors, "patient.age", patient.getAge());
            requireValue(fieldErrors, "patient.gender", patient.getGender());
            requireValue(fieldErrors, "patient.bloodPressure", patient.getBloodPressure());
            requireValue(fieldErrors, "patient.glucose", patient.getGlucose());
            requireValue(fieldErrors, "patient.bmi", patient.getBmi());
            requireValue(fieldErrors, "patient.heartRate", patient.getHeartRate());
            requireValue(fieldErrors, "patient.cholesterol", patient.getCholesterol());
        }
        if (!fieldErrors.isEmpty()) {
            throw new InvalidRequestException("Patient details are invalid.", fieldErrors);
        }
    }

    private boolean hasAnyFeature(PatientRequest patient) {
        return patient.getAge() != null
                || patient.getGender() != null
                || patient.getBloodPressure() != null
                || patient.getGlucose() != null
                || patient.getBmi() != null
                || patient.getHeartRate() != null
                || patient.getCholesterol() != null;
    }

    private void requireValue(Map<String, String> errors, String field, Object value) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            errors.put(field, "is required for prediction");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Patient toPatient(PatientRequest request) {
        Patient patient = new Patient();
        patient.setName(request.getName().trim());
        patient.setAge(request.getAge());
        patient.setGender(request.getGender().trim());
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setBloodPressure(request.getBloodPressure());
        patient.setGlucose(request.getGlucose());
        patient.setBmi(request.getBmi());
        patient.setHeartRate(request.getHeartRate());
        patient.setCholesterol(request.getCholesterol());
        return patient;
    }

    private Map<String, Object> toFeatures(Patient patient, PatientRequest suppliedPatient) {
        Integer age = valueOr(patient.getAge(), suppliedPatient == null ? null : suppliedPatient.getAge());
        String gender = valueOr(patient.getGender(), suppliedPatient == null ? null : suppliedPatient.getGender());
        Integer bloodPressure = valueOr(
                patient.getBloodPressure(), suppliedPatient == null ? null : suppliedPatient.getBloodPressure());
        Integer glucose = valueOr(patient.getGlucose(), suppliedPatient == null ? null : suppliedPatient.getGlucose());
        Double bmi = valueOr(patient.getBmi(), suppliedPatient == null ? null : suppliedPatient.getBmi());
        Integer heartRate = valueOr(
                patient.getHeartRate(), suppliedPatient == null ? null : suppliedPatient.getHeartRate());
        Integer cholesterol = valueOr(
                patient.getCholesterol(), suppliedPatient == null ? null : suppliedPatient.getCholesterol());

        Map<String, String> errors = new LinkedHashMap<>();
        requireValue(errors, "patient.age", age);
        requireValue(errors, "patient.gender", gender);
        requireValue(errors, "patient.bloodPressure", bloodPressure);
        requireValue(errors, "patient.glucose", glucose);
        requireValue(errors, "patient.bmi", bmi);
        requireValue(errors, "patient.heartRate", heartRate);
        requireValue(errors, "patient.cholesterol", cholesterol);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException("Patient health parameters are required for prediction.", errors);
        }

        PatientRequest effectiveValues = new PatientRequest();
        effectiveValues.setAge(age);
        effectiveValues.setGender(gender);
        effectiveValues.setBloodPressure(bloodPressure);
        effectiveValues.setGlucose(glucose);
        effectiveValues.setBmi(bmi);
        effectiveValues.setHeartRate(heartRate);
        effectiveValues.setCholesterol(cholesterol);
        Map<String, String> rangeErrors = new LinkedHashMap<>();
        for (ConstraintViolation<PatientRequest> violation : validator.validate(effectiveValues)) {
            rangeErrors.put(
                    "patient." + violation.getPropertyPath(),
                    violation.getMessage());
        }
        if (!rangeErrors.isEmpty()) {
            throw new InvalidRequestException("Patient health parameters are invalid.", rangeErrors);
        }

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("age", age);
        features.put("gender", genderCode(gender));
        features.put("blood_pressure", bloodPressure);
        features.put("glucose", glucose);
        features.put("bmi", bmi);
        features.put("heart_rate", heartRate);
        features.put("cholesterol", cholesterol);
        return features;
    }

    private <T> T valueOr(T storedValue, T suppliedValue) {
        return suppliedValue == null ? storedValue : suppliedValue;
    }

    private int genderCode(String value) {
        return switch (value.trim().toLowerCase()) {
            case "male", "m", "0" -> 0;
            case "female", "f", "1" -> 1;
            default -> throw new InvalidRequestException(
                    "Gender must be male/female or the corresponding code 0/1.",
                    Map.of("patient.gender", "must be male/female or 0/1"));
        };
    }

    private void recordAudit(String eventType, Prediction prediction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("predictionId", prediction.getId());
        payload.put("patientId", prediction.getPatient().getId());
        integrityAuditService.append("prediction:" + prediction.getId(), payload);
    }

    private PredictionResponse toResponse(Prediction prediction) {
        Patient patient = prediction.getPatient();
        PatientResponse patientResponse = new PatientResponse(
                patient.getId(),
                patient.getName(),
                patient.getAge(),
                patient.getGender(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getBloodPressure(),
                patient.getGlucose(),
                patient.getBmi(),
                patient.getHeartRate(),
                patient.getCholesterol());
        return new PredictionResponse(
                prediction.getId(),
                prediction.getDiseaseType(),
                prediction.getPredictionResult(),
                prediction.getRiskLevel(),
                prediction.getProbability(),
                prediction.getCreatedAt(),
                patientResponse);
    }
}
