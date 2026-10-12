package com.edps.service;

import com.edps.entity.Patient;
import com.edps.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public Patient createPatient(Patient patient) {
        return patientRepository.save(patient);
    }

    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    public Patient getPatientById(Long id) {
        return patientRepository.findById(id).orElse(null);
    }

    public Patient updatePatient(Long id, Patient patient) {
        Patient existingPatient = patientRepository.findById(id).orElse(null);

        if (existingPatient == null) {
            return null;
        }

        existingPatient.setName(patient.getName());
        existingPatient.setAge(patient.getAge());
        existingPatient.setGender(patient.getGender());
        existingPatient.setPhone(patient.getPhone());
        existingPatient.setEmail(patient.getEmail());

        return patientRepository.save(existingPatient);
    }

    public boolean deletePatient(Long id) {
        Patient existingPatient = patientRepository.findById(id).orElse(null);

        if (existingPatient == null) {
            return false;
        }

        patientRepository.delete(existingPatient);
        return true;
    }
}