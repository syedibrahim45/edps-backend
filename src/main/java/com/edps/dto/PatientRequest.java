package com.edps.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class PatientRequest {

    @Positive
    private Long id;

    @Size(max = 120)
    private String name;

    @Min(1)
    @Max(120)
    private Integer age;

    @Pattern(regexp = "(?i)male|female|m|f|0|1", message = "must be male, female, m, f, 0, or 1")
    private String gender;

    @Size(max = 30)
    private String phone;

    @Email
    @Size(max = 254)
    private String email;

    @Min(1)
    @Max(400)
    private Integer bloodPressure;

    @Min(1)
    @Max(1000)
    private Integer glucose;

    @DecimalMin("0.1")
    @DecimalMax("100.0")
    private Double bmi;

    @Min(1)
    @Max(300)
    private Integer heartRate;

    @Min(1)
    @Max(1000)
    private Integer cholesterol;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getBloodPressure() {
        return bloodPressure;
    }

    public void setBloodPressure(Integer bloodPressure) {
        this.bloodPressure = bloodPressure;
    }

    public Integer getGlucose() {
        return glucose;
    }

    public void setGlucose(Integer glucose) {
        this.glucose = glucose;
    }

    public Double getBmi() {
        return bmi;
    }

    public void setBmi(Double bmi) {
        this.bmi = bmi;
    }

    public Integer getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(Integer heartRate) {
        this.heartRate = heartRate;
    }

    public Integer getCholesterol() {
        return cholesterol;
    }

    public void setCholesterol(Integer cholesterol) {
        this.cholesterol = cholesterol;
    }
}
