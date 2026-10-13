package com.edps.dto;

import java.time.Instant;

public record AuditVerificationResponse(
        boolean valid,
        long checkedRecords,
        String brokenReference,
        Instant verifiedAt) {
}
