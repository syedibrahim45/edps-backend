package com.edps;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
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

    @Test
    void productionPatientApiRequiresAdminAuthentication() throws Exception {
        mockMvc.perform(get("/patients"))
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

        mockMvc.perform(post("/patients")
                        .with(httpBasic("test-admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Synthetic Patient\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/patients")
                        .with(httpBasic("test-admin", "test-password"))
                        .cookie(new MockCookie(cookie.getName(), cookie.getValue()))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Synthetic Patient\"}"))
                .andExpect(status().isOk());
    }
}
