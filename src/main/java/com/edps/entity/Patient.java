package com.edps.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Integer age;

    private String gender;

    private String phone;

    private String email;

    private Integer bloodPressure;

    private Integer glucose;

    private Double bmi;

    private Integer heartRate;

    private Integer cholesterol;

    public Patient() {
    }

    public Patient(String name,
                   Integer age,
                   String gender,
                   String phone,
                   String email,
                   Integer bloodPressure,
                   Integer glucose,
                   Double bmi,
                   Integer heartRate,
                   Integer cholesterol) {

        this.name = name;
        this.age = age;
        this.gender = gender;
        this.phone = phone;
        this.email = email;
        this.bloodPressure = bloodPressure;
        this.glucose = glucose;
        this.bmi = bmi;
        this.heartRate = heartRate;
        this.cholesterol = cholesterol;
    }

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