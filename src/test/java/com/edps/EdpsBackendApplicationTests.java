package com.edps;

import com.edps.dto.MlPredictionResult;
import com.edps.entity.AuditRecord;
import com.edps.entity.Patient;
import com.edps.entity.Prediction;
import com.edps.exception.MlServiceException;
import com.edps.repository.AuditRecordRepository;
import com.edps.repository.PatientRepository;
import com.edps.repository.PredictionRepository;
import com.edps.repository.UserRepository;
import com.edps.service.IntegrityAuditService;
import com.edps.service.MlPredictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EdpsBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntegrityAuditService integrityAuditService;

    @MockitoBean
    private MlPredictionService mlPredictionService;

    @BeforeEach
    void clearDatabase() {
        predictionRepository.deleteAll();
        auditRecordRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void contextLoadsWithoutMySqlOrPythonService() {
    }

    @Test
    void predictionUsesExistingPatientAndRecordsHistoryAndAudit() throws Exception {
        Patient storedPatient = patientRepository.save(new Patient(
                "Stored Patient", 40, "female", null, null, 120, 95, 24.5, 72, 180));
        when(mlPredictionService.predict(anyMap()))
                .thenReturn(new MlPredictionResult("Diabetes", "1", "High Risk", 0.82));

        mockMvc.perform(post("/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": %d,
                                  "patient": {
                                    "name": "Do Not Persist",
                                    "age": 55,
                                    "gender": "Female",
                                    "bloodPressure": 135,
                                    "glucose": 140,
                                    "bmi": 29.4,
                                    "heartRate": 80,
                                    "cholesterol": 210
                                  }
                                }
                                """.formatted(storedPatient.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diseaseType").value("Diabetes"))
                .andExpect(jsonPath("$.riskLevel").value("High Risk"))
                .andExpect(jsonPath("$.probability").value(0.82))
                .andExpect(jsonPath("$.patient.id").value(storedPatient.getId()))
                .andExpect(jsonPath("$.patient.name").value("Stored Patient"));

        Map<String, Object> mlFeatures = Map.of(
                "age", 55,
                "gender", 1,
                "blood_pressure", 135,
                "glucose", 140,
                "bmi", 29.4,
                "heart_rate", 80,
                "cholesterol", 210);
        verify(mlPredictionService).predict(mlFeatures);
        org.junit.jupiter.api.Assertions.assertEquals(1, patientRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Stored Patient", patientRepository.findById(storedPatient.getId()).orElseThrow().getName());
        org.junit.jupiter.api.Assertions.assertEquals(1, predictionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, auditRecordRepository.count());
        String auditPayload = auditRecordRepository.findAllByOrderByIdAsc().getFirst().getPayload();
        org.junit.jupiter.api.Assertions.assertFalse(auditPayload.contains("Diabetes"));
        org.junit.jupiter.api.Assertions.assertFalse(auditPayload.contains("High Risk"));
        org.junit.jupiter.api.Assertions.assertFalse(auditPayload.contains("0.82"));

        mockMvc.perform(get("/predictions/patient/{patientId}", storedPatient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/integrity/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.checkedRecords").value(1));

        AuditRecord record = auditRecordRepository.findAllByOrderByIdAsc().getFirst();
        record.setPayload(record.getPayload() + " ");
        auditRecordRepository.save(record);
        mockMvc.perform(get("/integrity/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.brokenReference").value(record.getReference()));
    }

    @Test
    void patientCrudIsRecordedWithoutPersistingPatientDetailsInAuditPayload() throws Exception {
        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Audit Patient",
                                  "age": 44,
                                  "gender": "male",
                                  "phone": "555-0100",
                                  "email": "audit@example.test",
                                  "bloodPressure": 120,
                                  "glucose": 95,
                                  "bmi": 24.5,
                                  "heartRate": 72,
                                  "cholesterol": 180
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        org.junit.jupiter.api.Assertions.assertEquals(1, auditRecordRepository.count());
        String auditPayload = auditRecordRepository.findAllByOrderByIdAsc().getFirst().getPayload();
        org.junit.jupiter.api.Assertions.assertFalse(auditPayload.contains("Audit Patient"));
        org.junit.jupiter.api.Assertions.assertFalse(auditPayload.contains("555-0100"));
        org.junit.jupiter.api.Assertions.assertFalse(auditPayload.contains("audit@example.test"));
        org.junit.jupiter.api.Assertions.assertFalse(auditPayload.contains("120"));

        mockMvc.perform(get("/integrity/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.checkedRecords").value(1));
    }

    @Test
    void patientCrudValidatesFieldsAndPreservesNotFoundResponses() throws Exception {
        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Missing measurements\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());

        mockMvc.perform(get("/patients/987654"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/patients/987654")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPatientJson("Missing patient", 35)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/patients/987654"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPatientJson("CRUD Patient", 35)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());
        Long patientId = patientRepository.findAll().getFirst().getId();

        mockMvc.perform(get("/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/patients/{id}", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CRUD Patient"));
        mockMvc.perform(put("/patients/{id}", patientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPatientJson("Updated Patient", 36)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Patient"))
                .andExpect(jsonPath("$.age").value(36));
        org.junit.jupiter.api.Assertions.assertEquals(1, patientRepository.count());

        mockMvc.perform(delete("/patients/{id}", patientId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("Patient deleted successfully"));
        org.junit.jupiter.api.Assertions.assertEquals(0, patientRepository.count());
    }

    @Test
    void patientWithPredictionHistoryCannotBeDeleted() throws Exception {
        Patient patient = patientRepository.save(new Patient(
                "History Patient", 42, "female", null, null, 120, 90, 22.0, 70, 170));
        predictionRepository.save(new Prediction(
                "Diabetes", "0", "Low Risk", 0.12, LocalDateTime.now(), patient));

        mockMvc.perform(delete("/patients/{id}", patient.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Patient " + patient.getId() + " has prediction history and cannot be deleted."));

        org.junit.jupiter.api.Assertions.assertTrue(patientRepository.existsById(patient.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(1, predictionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, auditRecordRepository.count());
    }

    @Test
    void newPatientPredictionPersistsHistoryAndMapsMaleToZero() throws Exception {
        when(mlPredictionService.predict(anyMap()))
                .thenReturn(new MlPredictionResult("Diabetes", "0", "Low Risk", 0.18));

        mockMvc.perform(post("/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newPatientRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionResult").value("0"))
                .andExpect(jsonPath("$.riskLevel").value("Low Risk"))
                .andExpect(jsonPath("$.probability").value(0.18))
                .andExpect(jsonPath("$.patient.gender").value("male"));

        verify(mlPredictionService).predict(Map.of(
                "age", 44,
                "gender", 0,
                "blood_pressure", 120,
                "glucose", 95,
                "bmi", 24.5,
                "heart_rate", 72,
                "cholesterol", 180));
        org.junit.jupiter.api.Assertions.assertEquals(1, patientRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, predictionRepository.count());

        mockMvc.perform(get("/predictions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].patient.name").value("New Patient"));
        mockMvc.perform(get("/predictions/patient/{patientId}", patientRepository.findAll().getFirst().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void mlDashboardRoutesProxyStructuredJsonToConfiguredService() throws Exception {
        when(mlPredictionService.getHealth())
                .thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(mlPredictionService.getGlobalExplainability())
                .thenReturn(objectMapper.readTree("{\"features\":[]}"));
        when(mlPredictionService.getFederatedResults())
                .thenReturn(objectMapper.readTree("{\"rounds\":[]}"));
        when(mlPredictionService.explainIndividual(any(JsonNode.class)))
                .thenReturn(objectMapper.readTree("{\"explanation\":\"synthetic\"}"));

        mockMvc.perform(get("/api/ml/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
        mockMvc.perform(get("/api/ml/explainability/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").isArray());
        mockMvc.perform(post("/api/ml/explainability/individual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":45,\"glucose\":110}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("synthetic"));
        mockMvc.perform(get("/api/ml/federated/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rounds").isArray());
    }

    @Test
    void concurrentAuditAppendsRemainInOneVerifiedHashChain() throws Exception {
        int writers = 6;
        var pool = Executors.newFixedThreadPool(writers);
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < writers; i++) {
                int event = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent audit appends.");
                    }
                    integrityAuditService.append("concurrent:" + event, Map.of("event", event));
                    return null;
                }));
            }

            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        org.junit.jupiter.api.Assertions.assertEquals(writers, auditRecordRepository.count());
        assertTrue(integrityAuditService.verify().valid());
    }

    @Test
    void missingPatientIdReturnsNotFoundWithoutCallingMlService() throws Exception {
        mockMvc.perform(post("/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": 987654}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Patient 987654 was not found."));

        verifyNoInteractions(mlPredictionService);
        org.junit.jupiter.api.Assertions.assertEquals(0, predictionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, auditRecordRepository.count());
    }

    @Test
    void failedMlCallDoesNotLeavePatientPredictionOrAuditRecords() throws Exception {
        when(mlPredictionService.predict(anyMap()))
                .thenThrow(new MlServiceException(HttpStatus.SERVICE_UNAVAILABLE, "ML service unavailable."));

        mockMvc.perform(post("/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newPatientRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("ML service unavailable."));

        org.junit.jupiter.api.Assertions.assertEquals(0, patientRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, predictionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, auditRecordRepository.count());
    }

    @Test
    void invalidPatientInputReturnsFieldErrorsWithoutCallingMlService() throws Exception {
        mockMvc.perform(post("/predictions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patient": {
                                    "name": "",
                                    "age": 0,
                                    "gender": "unknown"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());

        verifyNoInteractions(mlPredictionService);
        org.junit.jupiter.api.Assertions.assertEquals(0, patientRepository.count());
    }

    @Test
    void userResponsesNeverExposePasswordsAndStoredPasswordsAreEncoded() throws Exception {
        String rawPassword = "a-strong-password";
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"demo-user","password":"%s","role":"USER"}
                                """.formatted(rawPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo-user"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String storedPassword = userRepository.findAll().getFirst().getPassword();
        org.junit.jupiter.api.Assertions.assertNotEquals(rawPassword, storedPassword);
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(rawPassword, storedPassword));
    }

    private String newPatientRequest() {
        return """
                {
                  "patient": {
                    "name": "New Patient",
                    "age": 44,
                    "gender": "male",
                    "bloodPressure": 120,
                    "glucose": 95,
                    "bmi": 24.5,
                    "heartRate": 72,
                    "cholesterol": 180
                  }
                }
                """;
    }

    private String validPatientJson(String name, int age) {
                return """
                        {
                          "name": "%s",
                          "age": %d,
                          "gender": "male",
                          "bloodPressure": 120,
                          "glucose": 95,
                          "bmi": 24.5,
                          "heartRate": 72,
                          "cholesterol": 180
                        }
                        """.formatted(name, age);
    }
}
