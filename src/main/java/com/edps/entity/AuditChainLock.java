package com.edps.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "integrity_audit_chain_lock")
public class AuditChainLock {

    @Id
    private Long id;

    protected AuditChainLock() {
    }

    public AuditChainLock(Long id) {
        this.id = id;
    }
}
