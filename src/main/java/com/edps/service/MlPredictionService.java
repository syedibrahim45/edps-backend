package com.edps.service;

import com.edps.dto.MlPredictionResult;
import com.edps.exception.InvalidRequestException;
import com.edps.exception.MlServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Service
public class MlPredictionService {

    private static final String DEFAULT_DISEASE_TYPE = "Disease Risk Prediction";

    private final RestClient restClient;

    public MlPredictionService(
            RestClient.Builder restClientBuilder,
            @Value("${app.ml.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.ml.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.ml.read-timeout:5s}") Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public MlPredictionResult predict(Map<String, Object> patientData) {
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/predict")
                    .body(patientData)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (ResourceAccessException exception) {
            throw new MlServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The Python prediction service is unavailable or timed out.",
                    exception);
        } catch (RestClientResponseException exception) {
            throw new MlServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "The Python prediction service rejected the request.",
                    exception);
        } catch (RestClientException exception) {
            throw new MlServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "The Python prediction service returned an unreadable response.",
                    exception);
        }

        return parseResponse(response);
    }

    public JsonNode getHealth() {
        return getJson("/health");
    }

    public JsonNode getGlobalExplainability() {
        return getJson("/explainability/global");
    }

    public JsonNode explainIndividual(JsonNode request) {
        if (request == null || !request.isObject() || request.isEmpty()) {
            throw new InvalidRequestException("A non-empty JSON object is required.");
        }
        return postJson("/explainability/individual", request);
    }

    public JsonNode getFederatedResults() {
        return getJson("/federated/results");
    }

    private JsonNode getJson(String path) {
        try {
            return validateJsonResponse(restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (ResourceAccessException exception) {
            throw unavailable(exception);
        } catch (RestClientResponseException exception) {
            throw rejected(exception);
        } catch (RestClientException exception) {
            throw unreadable(exception);
        }
    }

    private JsonNode postJson(String path, JsonNode request) {
        try {
            return validateJsonResponse(restClient.post()
                    .uri(path)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (ResourceAccessException exception) {
            throw unavailable(exception);
        } catch (RestClientResponseException exception) {
            throw rejected(exception);
        } catch (RestClientException exception) {
            throw unreadable(exception);
        }
    }

    private JsonNode validateJsonResponse(JsonNode response) {
        if (response == null || response.isNull()
                || (!response.isObject() && !response.isArray())
                || response.isEmpty()) {
            throw invalidResponse();
        }
        return response;
    }

    private MlServiceException unavailable(Exception cause) {
        return new MlServiceException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The Python prediction service is unavailable or timed out.",
                cause);
    }

    private MlServiceException rejected(Exception cause) {
        return new MlServiceException(
                HttpStatus.BAD_GATEWAY,
                "The Python prediction service rejected the request.",
                cause);
    }

    private MlServiceException unreadable(Exception cause) {
        return new MlServiceException(
                HttpStatus.BAD_GATEWAY,
                "The Python prediction service returned an unreadable response.",
                cause);
    }

    static MlPredictionResult parseResponse(Map<String, Object> response) {
        if (response == null) {
            throw invalidResponse();
        }

        Object prediction = response.get("prediction");
        Object riskLevel = firstPresent(
                response,
                "risk_level",
                "riskLevel",
                "classification",
                "risk",
                "disease");
        Object diseaseType = firstPresent(response, "disease_type", "diseaseType");
        if (diseaseType == null && response.containsKey("risk_level")) {
            diseaseType = response.get("disease");
        }
        Object probabilityValue = response.get("probability");

        if (prediction == null || String.valueOf(prediction).isBlank()
                || riskLevel == null || String.valueOf(riskLevel).isBlank()) {
            throw invalidResponse();
        }

        Double probability = parseProbability(probabilityValue);
        if (probability == null || !Double.isFinite(probability)
                || probability < 0.0 || probability > 1.0) {
            throw invalidResponse();
        }

        String resolvedDiseaseType = diseaseType == null
                ? DEFAULT_DISEASE_TYPE
                : String.valueOf(diseaseType);
        return new MlPredictionResult(
                resolvedDiseaseType,
                String.valueOf(prediction),
                String.valueOf(riskLevel),
                probability);
    }

    private static Object firstPresent(Map<String, Object> response, String... keys) {
        for (String key : keys) {
            Object value = response.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Double parseProbability(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static MlServiceException invalidResponse() {
        return new MlServiceException(
                HttpStatus.BAD_GATEWAY,
                "The Python prediction service returned an invalid prediction response.");
    }
}
