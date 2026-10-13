package com.edps.service;

import com.edps.entity.AuditChainLock;
import com.edps.repository.AuditChainLockRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class AuditChainLockInitializer implements ApplicationRunner {

    private static final long LOCK_ID = 1L;

    private final AuditChainLockRepository auditChainLockRepository;

    public AuditChainLockInitializer(AuditChainLockRepository auditChainLockRepository) {
        this.auditChainLockRepository = auditChainLockRepository;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (auditChainLockRepository.existsById(LOCK_ID)) {
            return;
        }

        try {
            auditChainLockRepository.saveAndFlush(new AuditChainLock(LOCK_ID));
        } catch (DataIntegrityViolationException concurrentInitialization) {
            if (!auditChainLockRepository.existsById(LOCK_ID)) {
                throw concurrentInitialization;
            }
        }
    }
}
