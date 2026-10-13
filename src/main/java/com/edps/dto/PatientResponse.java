package com.edps.dto;

public record PatientResponse(
        Long id,
        String name,
        Integer age,
        String gender,
        String phone,
        String email,
        Integer bloodPressure,
        Integer glucose,
        Double bmi,
        Integer heartRate,
        Integer cholesterol) {
}
