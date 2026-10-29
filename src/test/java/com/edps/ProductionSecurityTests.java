package com.edps;

import com.edps.dto.DiabetesScreeningResponse;
import com.edps.repository.AuditRecordRepository;
import com.edps.repository.PatientRepository;
import com.edps.repository.PredictionRepository;
import com.edps.service.MlPredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:edps-production-security;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=test-db-password",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.security.admin.username=test-admin",
        "app.security.admin.password=test-password",
        "app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProductionSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @MockitoBean
    private MlPredictionService mlPredictionService;

    @Test
    void productionPatientApiRequiresAdminAuthentication() throws Exception {
        mockMvc.perform(get("/patients"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/ml/health"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/patients").with(httpBasic("test-admin", "test-password")))
                .andExpect(status().isOk());
    }

    @Test
    void productionWritesRequireCsrfToken() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(
                csrfResult.getResponse().getContentAsString(),
                "$.token");
        var cookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/diabetes-screenings")
                        .cookie(new MockCookie(cookie.getName(), cookie.getValue()))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/patients")
                        .with(httpBasic("test-admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Synthetic Patient\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/diabetes-screenings")
                        .with(httpBasic("test-admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/patients")
                        .with(httpBasic("test-admin", "test-password"))
                        .cookie(new MockCookie(cookie.getName(), cookie.getValue()))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Synthetic Patient",
                                  "age": 40,
                                  "gender": "female",
                                  "bloodPressure": 120,
                                  "glucose": 95,
                                  "bmi": 24.5,
                                  "heartRate": 72,
                                  "cholesterol": 180
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedDiabetesScreeningWithCsrfPassesProductionSecurityWithoutPersistence() throws Exception {
        when(mlPredictionService.predictDiabetes(any()))
                .thenReturn(new DiabetesScreeningResponse(
                        1,
                        "Prediabetes or diabetes reported in dataset label",
                        0.72,
                        0.6447012298480324,
                        "logistic_regression_balanced",
                        "cdc-brfss2015-binary-v1",
                        "Experimental screening output; not a clinical diagnosis."));

        long patientsBefore = patientRepository.count();
        long predictionsBefore = predictionRepository.count();
        long auditRecordsBefore = auditRecordRepository.count();

        MvcResult csrfResult = mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
                csrfResult.getResponse().getContentAsString(),
                "$.token");
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/diabetes-screenings")
                        .with(httpBasic("test-admin", "test-password"))
                        .cookie(new MockCookie(csrfCookie.getName(), csrfCookie.getValue()))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "HighBP": 1,
                                  "HighChol": 1,
                                  "CholCheck": 1,
                                  "Smoker": 0,
                                  "Stroke": 0,
                                  "HeartDiseaseorAttack": 0,
                                  "PhysActivity": 1,
                                  "Fruits": 1,
                                  "Veggies": 1,
                                  "HvyAlcoholConsump": 0,
                                  "AnyHealthcare": 1,
                                  "NoDocbcCost": 0,
                                  "DiffWalk": 0,
                                  "Sex": 0,
                                  "BMI": 27.5,
                                  "GenHlth": 3,
                                  "MentHlth": 0,
                                  "PhysHlth": 0,
                                  "Age": 9,
                                  "Education": 4,
                                  "Income": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predicted_class").value(1))
                .andExpect(jsonPath("$.predicted_label")
                        .value("Prediabetes or diabetes reported in dataset label"))
                .andExpect(jsonPath("$.positive_class_score").value(0.72))
                .andExpect(jsonPath("$.threshold").value(0.6447012298480324))
                .andExpect(jsonPath("$.model_id").value("logistic_regression_balanced"))
                .andExpect(jsonPath("$.model_version").value("cdc-brfss2015-binary-v1"))
                .andExpect(jsonPath("$.disclaimer").exists());

        verify(mlPredictionService, times(1)).predictDiabetes(any());
        org.junit.jupiter.api.Assertions.assertEquals(patientsBefore, patientRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(predictionsBefore, predictionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(auditRecordsBefore, auditRecordRepository.count());
    }

    @Test
    void healthEndpointIsAvailableWithoutAuthenticationAndCorsRejectsUnknownOrigins() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/patients")
                        .header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
