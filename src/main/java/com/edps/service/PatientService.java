package com.edps.service;

import com.edps.entity.Patient;
import com.edps.exception.ConflictException;
import com.edps.exception.NotFoundException;
import com.edps.repository.PatientRepository;
import com.edps.repository.PredictionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final PredictionRepository predictionRepository;
    private final IntegrityAuditService integrityAuditService;

    public PatientService(
            PatientRepository patientRepository,
            PredictionRepository predictionRepository,
            IntegrityAuditService integrityAuditService) {
        this.patientRepository = patientRepository;
        this.predictionRepository = predictionRepository;
        this.integrityAuditService = integrityAuditService;
    }

    @Transactional
    public Patient createPatient(Patient patient) {
        if (patient.getId() != null) {
            throw new ConflictException("A patient ID cannot be supplied when creating a patient.");
        }
        Patient savedPatient = patientRepository.save(patient);
        recordAudit("PATIENT_CREATED", savedPatient);
        return savedPatient;
    }

    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    public Patient getPatientById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Patient " + id + " was not found."));
    }

    @Transactional
    public Patient updatePatient(Long id, Patient patient) {
        Patient existingPatient = patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Patient " + id + " was not found."));

        existingPatient.setId(id);
        existingPatient.setName(patient.getName());
        existingPatient.setAge(patient.getAge());
        existingPatient.setGender(patient.getGender());
        existingPatient.setPhone(patient.getPhone());
        existingPatient.setEmail(patient.getEmail());
        existingPatient.setBloodPressure(patient.getBloodPressure());
        existingPatient.setGlucose(patient.getGlucose());
        existingPatient.setBmi(patient.getBmi());
        existingPatient.setHeartRate(patient.getHeartRate());
        existingPatient.setCholesterol(patient.getCholesterol());

        Patient savedPatient = patientRepository.save(existingPatient);
        recordAudit("PATIENT_UPDATED", savedPatient);
        return savedPatient;
    }

    @Transactional
    public boolean deletePatient(Long id) {
        Patient existingPatient = patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Patient " + id + " was not found."));
        if (predictionRepository.existsByPatient_Id(id)) {
            throw new ConflictException(
                    "Patient " + id + " has prediction history and cannot be deleted.");
        }

        recordAudit("PATIENT_DELETED", existingPatient);
        patientRepository.delete(existingPatient);
        return true;
    }

    private void recordAudit(String eventType, Patient patient) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("patientId", patient.getId());
        integrityAuditService.append("patient:" + patient.getId(), payload);
    }
}