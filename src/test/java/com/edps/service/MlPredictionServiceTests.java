package com.edps.service;

import com.edps.dto.MlPredictionResult;
import com.edps.exception.InvalidRequestException;
import com.edps.exception.MlServiceException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlPredictionServiceTests {

    @Test
    void mapsLegacyFastApiResponse() {
        MlPredictionResult result = MlPredictionService.parseResponse(Map.of(
                "prediction", 1,
                "disease", "High Risk",
                "probability", 0.82));

        assertEquals("Disease Risk Prediction", result.diseaseType());
        assertEquals("1", result.predictionResult());
        assertEquals("High Risk", result.riskLevel());
        assertEquals(0.82, result.probability());
    }

    @Test
    void mapsExplicitDiseaseTypeAndRiskClassification() {
        MlPredictionResult result = MlPredictionService.parseResponse(Map.of(
                "prediction", 0,
                "disease_type", "Diabetes",
                "risk_level", "Low Risk",
                "probability", "0.15"));

        assertEquals("Diabetes", result.diseaseType());
        assertEquals("0", result.predictionResult());
        assertEquals("Low Risk", result.riskLevel());
        assertEquals(0.15, result.probability());
    }

    @Test
    void rejectsInvalidProbabilityAsBadGateway() {
        MlServiceException exception = assertThrows(
                MlServiceException.class,
                () -> MlPredictionService.parseResponse(Map.of(
                        "prediction", 1,
                        "risk_level", "High Risk",
                        "probability", 1.2)));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    @Test
    void proxiesDashboardMlEndpointsUsingConfiguredBaseUrl() throws Exception {
        AtomicReference<String> individualRequestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/explainability/individual")) {
                individualRequestBody.set(new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 200, "{\"explanation\":\"synthetic\"}");
            } else if (path.equals("/federated/results")) {
                respond(exchange, 200, "[{\"round\":1}]");
            } else {
                respond(exchange, 200, "{\"status\":\"ok\"}");
            }
        });
        server.start();
        try {
            MlPredictionService service = new MlPredictionService(
                    RestClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(30));

            assertTrue(service.getHealth().isObject());
            assertTrue(service.getGlobalExplainability().isObject());
            JsonNode request = JsonMapper.builder().build()
                    .readTree("{\"age\":45,\"glucose\":110}");
            assertTrue(service.explainIndividual(request).isObject());
            assertEquals("{\"age\":45,\"glucose\":110}", individualRequestBody.get());
            assertTrue(service.getFederatedResults().isArray());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsEmptyIndividualExplainabilityRequest() {
        MlPredictionService service = new MlPredictionService(
                RestClient.builder(),
                "http://127.0.0.1:1",
                Duration.ofMillis(100),
                Duration.ofMillis(100));
        JsonNode emptyObject = JsonMapper.builder().build().createObjectNode();

        assertThrows(
                InvalidRequestException.class,
                () -> service.explainIndividual(emptyObject));
    }

    @Test
    void mapsUnavailableDashboardServiceTo503() {
        MlPredictionService service = new MlPredictionService(
                RestClient.builder(),
                "http://127.0.0.1:1",
                Duration.ofMillis(100),
                Duration.ofMillis(100));

        MlServiceException exception = assertThrows(MlServiceException.class, service::getHealth);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String responseBody) throws IOException {
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
