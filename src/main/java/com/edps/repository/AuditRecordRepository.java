package com.edps.repository;

import com.edps.entity.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    Optional<AuditRecord> findTopByOrderByIdDesc();

    List<AuditRecord> findAllByOrderByIdAsc();
}
