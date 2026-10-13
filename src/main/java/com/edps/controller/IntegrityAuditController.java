package com.edps.controller;

import com.edps.dto.AuditVerificationResponse;
import com.edps.entity.AuditRecord;
import com.edps.service.IntegrityAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/integrity/audit")
public class IntegrityAuditController {

    private final IntegrityAuditService integrityAuditService;

    public IntegrityAuditController(IntegrityAuditService integrityAuditService) {
        this.integrityAuditService = integrityAuditService;
    }

    @GetMapping("/records")
    public List<AuditRecord> getRecords() {
        return integrityAuditService.getRecords();
    }

    @GetMapping("/verify")
    public AuditVerificationResponse verify() {
        return integrityAuditService.verify();
    }
}
