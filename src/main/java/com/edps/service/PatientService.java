package com.edps.service;

import com.edps.entity.Patient;
import com.edps.exception.ConflictException;
import com.edps.exception.NotFoundException;
import com.edps.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final IntegrityAuditService integrityAuditService;

    public PatientService(PatientRepository patientRepository, IntegrityAuditService integrityAuditService) {
        this.patientRepository = patientRepository;
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

        recordAudit("PATIENT_DELETED", existingPatient);
        patientRepository.delete(existingPatient);
        return true;
    }

    private void recordAudit(String eventType, Patient patient) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", patient.getName());
        snapshot.put("age", patient.getAge());
        snapshot.put("gender", patient.getGender());
        snapshot.put("phone", patient.getPhone());
        snapshot.put("email", patient.getEmail());
        snapshot.put("bloodPressure", patient.getBloodPressure());
        snapshot.put("glucose", patient.getGlucose());
        snapshot.put("bmi", patient.getBmi());
        snapshot.put("heartRate", patient.getHeartRate());
        snapshot.put("cholesterol", patient.getCholesterol());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("patientId", patient.getId());
        payload.put("patientSnapshotHash", integrityAuditService.fingerprint(snapshot));
        integrityAuditService.append("patient:" + patient.getId(), payload);
    }
}