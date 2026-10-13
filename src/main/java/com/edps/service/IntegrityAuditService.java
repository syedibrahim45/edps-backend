package com.edps.service;

import com.edps.dto.AuditVerificationResponse;
import com.edps.entity.AuditRecord;
import com.edps.repository.AuditRecordRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class IntegrityAuditService {

    private static final String GENESIS_HASH = "GENESIS";

    private final AuditRecordRepository auditRecordRepository;
    private final ObjectMapper objectMapper;

    public IntegrityAuditService(
            AuditRecordRepository auditRecordRepository,
            ObjectMapper objectMapper) {
        this.auditRecordRepository = auditRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public synchronized AuditRecord append(String reference, Map<String, Object> payloadValues) {
        String payload = serialize(new TreeMap<>(payloadValues));
        String payloadHash = sha256(payload);
        var latestRecord = auditRecordRepository.findTopByOrderByIdDesc();
        String previousHash = latestRecord.isPresent()
                ? latestRecord.get().getCurrentHash()
                : GENESIS_HASH;
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String stableReference = reference + ":" + UUID.randomUUID();

        AuditRecord record = new AuditRecord();
        record.setReference(stableReference);
        record.setTimestamp(timestamp);
        record.setPayload(payload);
        record.setPayloadHash(payloadHash);
        record.setPreviousHash(previousHash);
        record.setCurrentHash(hashRecord(stableReference, timestamp, payloadHash, previousHash));
        return auditRecordRepository.save(record);
    }

    public String fingerprint(Map<String, Object> values) {
        return sha256(serialize(new TreeMap<>(values)));
    }

    @Transactional(readOnly = true)
    public AuditVerificationResponse verify() {
        String expectedPreviousHash = GENESIS_HASH;
        long checkedRecords = 0;

        for (AuditRecord record : auditRecordRepository.findAllByOrderByIdAsc()) {
            checkedRecords++;
            String recomputedPayloadHash = sha256(record.getPayload());
            String recomputedCurrentHash = hashRecord(
                    record.getReference(),
                    record.getTimestamp(),
                    recomputedPayloadHash,
                    expectedPreviousHash);
            if (!record.getPreviousHash().equals(expectedPreviousHash)
                    || !record.getPayloadHash().equals(recomputedPayloadHash)
                    || !record.getCurrentHash().equals(recomputedCurrentHash)) {
                return new AuditVerificationResponse(false, checkedRecords, record.getReference(), Instant.now());
            }
            expectedPreviousHash = record.getCurrentHash();
        }

        return new AuditVerificationResponse(true, checkedRecords, null, Instant.now());
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditRecord> getRecords() {
        return auditRecordRepository.findAllByOrderByIdAsc();
    }

    private String serialize(Map<String, Object> payloadValues) {
        try {
            return objectMapper.writeValueAsString(payloadValues);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize audit payload.", exception);
        }
    }

    private String hashRecord(String reference, Instant timestamp, String payloadHash, String previousHash) {
        return sha256(reference + "\n" + timestamp + "\n" + payloadHash + "\n" + previousHash);
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", exception);
        }
    }
}
