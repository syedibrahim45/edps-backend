package com.edps.repository;

import com.edps.entity.AuditChainLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditChainLockRepository extends JpaRepository<AuditChainLock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select chainLock from AuditChainLock chainLock where chainLock.id = :id")
    Optional<AuditChainLock> findByIdForUpdate(@Param("id") Long id);
}
