package com.edps.service;

import com.edps.dto.MlPredictionResult;
import com.edps.dto.DiabetesScreeningRequest;
import com.edps.dto.DiabetesScreeningResponse;
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
    void callsDiabetesEndpointWithExactFeaturesAndValidatesResponse() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, diabetesResponse());
        });
        server.start();
        try {
            MlPredictionService service = serviceFor(server, Duration.ofSeconds(2));
            DiabetesScreeningResponse result = service.predictDiabetes(diabetesRequest());

            assertEquals("/predict/diabetes", requestPath.get());
            assertEquals(
                    "{\"HighBP\":1,\"HighChol\":1,\"CholCheck\":1,\"Smoker\":0,\"Stroke\":0,"
                            + "\"HeartDiseaseorAttack\":0,\"PhysActivity\":1,\"Fruits\":1,\"Veggies\":1,"
                            + "\"HvyAlcoholConsump\":0,\"AnyHealthcare\":1,\"NoDocbcCost\":0,"
                            + "\"DiffWalk\":0,\"Sex\":0,\"BMI\":27.5,\"GenHlth\":3,\"MentHlth\":0,"
                            + "\"PhysHlth\":0,\"Age\":9,\"Education\":4,\"Income\":5}",
                    requestBody.get());
            assertEquals(1, result.predicted_class());
            assertEquals("Prediabetes or diabetes reported in dataset label", result.predicted_label());
            assertEquals(0.72, result.positive_class_score());
            assertEquals(0.6447012298480324, result.threshold());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsDiabetesUpstream422To502And503To503() throws Exception {
        for (int upstreamStatus : new int[] {422, 503}) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange ->
                    respond(exchange, upstreamStatus, "{\"detail\":\"internal upstream detail\"}"));
            server.start();
            try {
                MlServiceException exception = assertThrows(
                        MlServiceException.class,
                        () -> serviceFor(server, Duration.ofSeconds(2))
                                .predictDiabetes(diabetesRequest()));
                assertEquals(
                        upstreamStatus == 503 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                        exception.getStatus());
                assertTrue(!exception.getMessage().contains("internal upstream detail"));
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void rejectsMalformedDiabetesResponseAsBadGateway() throws Exception {
        for (String invalidResponse : new String[] {
                diabetesResponse().replace(
                        "Prediabetes or diabetes reported in dataset label",
                        "Confirmed diabetes"),
                diabetesResponse().replace("\"threshold\": 0.6447012298480324", "\"threshold\": 0.8")
        }) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> respond(exchange, 200, invalidResponse));
            server.start();
            try {
                MlServiceException exception = assertThrows(
                        MlServiceException.class,
                        () -> serviceFor(server, Duration.ofSeconds(2))
                                .predictDiabetes(diabetesRequest()));
                assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void mapsDiabetesConnectionFailureAndTimeoutTo503() throws Exception {
        MlPredictionService unavailable = new MlPredictionService(
                RestClient.builder(),
                "http://127.0.0.1:1",
                Duration.ofMillis(100),
                Duration.ofMillis(100));
        MlServiceException connectionError = assertThrows(
                MlServiceException.class,
                () -> unavailable.predictDiabetes(diabetesRequest()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, connectionError.getStatus());

        HttpServer slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowServer.createContext("/", exchange -> {
            try {
                Thread.sleep(250);
                respond(exchange, 200, diabetesResponse());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        slowServer.start();
        try {
            MlServiceException timeoutError = assertThrows(
                    MlServiceException.class,
                    () -> serviceFor(slowServer, Duration.ofMillis(50))
                            .predictDiabetes(diabetesRequest()));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, timeoutError.getStatus());
        } finally {
            slowServer.stop(0);
        }
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

    private static MlPredictionService serviceFor(HttpServer server, Duration readTimeout) {
        return new MlPredictionService(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(2),
                readTimeout);
    }

    private static DiabetesScreeningRequest diabetesRequest() throws Exception {
        return DiabetesScreeningRequest.fromJson(JsonMapper.builder().build().readTree("""
                {
                  "HighBP": 1, "HighChol": 1, "CholCheck": 1, "Smoker": 0,
                  "Stroke": 0, "HeartDiseaseorAttack": 0, "PhysActivity": 1, "Fruits": 1,
                  "Veggies": 1, "HvyAlcoholConsump": 0, "AnyHealthcare": 1, "NoDocbcCost": 0,
                  "DiffWalk": 0, "Sex": 0, "BMI": 27.5, "GenHlth": 3, "MentHlth": 0,
                  "PhysHlth": 0, "Age": 9, "Education": 4, "Income": 5
                }
                """));
    }

    private static String diabetesResponse() {
        return """
                {
                  "predicted_class": 1,
                  "predicted_label": "Prediabetes or diabetes reported in dataset label",
                  "positive_class_score": 0.72,
                  "threshold": 0.6447012298480324,
                  "model_id": "logistic_regression_balanced",
                  "model_version": "cdc-brfss2015-binary-v1",
                  "disclaimer": "Experimental screening output; not a clinical diagnosis."
                }
                """;
    }
}
