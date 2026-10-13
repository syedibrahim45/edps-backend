package com.edps.repository;

import com.edps.entity.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    java.util.List<Prediction> findAllByOrderByCreatedAtDescIdDesc();

    java.util.List<Prediction> findByPatient_IdOrderByCreatedAtDescIdDesc(Long patientId);

    boolean existsByPatient_Id(Long patientId);
}