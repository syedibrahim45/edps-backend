package com.edps.dto;

import com.edps.exception.InvalidRequestException;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record DiabetesScreeningRequest(
        int highBp,
        int highChol,
        int cholCheck,
        int smoker,
        int stroke,
        int heartDiseaseorAttack,
        int physActivity,
        int fruits,
        int veggies,
        int hvyAlcoholConsump,
        int anyHealthcare,
        int noDocbcCost,
        int diffWalk,
        int sex,
        double bmi,
        int genHlth,
        int mentHlth,
        int physHlth,
        int age,
        int education,
        int income) {

    private static final Map<String, Integer> CATEGORICAL_MAXIMUMS = Map.ofEntries(
            Map.entry("HighBP", 1),
            Map.entry("HighChol", 1),
            Map.entry("CholCheck", 1),
            Map.entry("Smoker", 1),
            Map.entry("Stroke", 1),
            Map.entry("HeartDiseaseorAttack", 1),
            Map.entry("PhysActivity", 1),
            Map.entry("Fruits", 1),
            Map.entry("Veggies", 1),
            Map.entry("HvyAlcoholConsump", 1),
            Map.entry("AnyHealthcare", 1),
            Map.entry("NoDocbcCost", 1),
            Map.entry("DiffWalk", 1),
            Map.entry("Sex", 1),
            Map.entry("GenHlth", 5),
            Map.entry("MentHlth", 30),
            Map.entry("PhysHlth", 30),
            Map.entry("Age", 13),
            Map.entry("Education", 6),
            Map.entry("Income", 8));

    private static final Map<String, Integer> CATEGORICAL_MINIMUMS = Map.of(
            "GenHlth", 1,
            "Age", 1,
            "Education", 1,
            "Income", 1);

    private static final Set<String> FEATURE_NAMES;

    static {
        Set<String> names = new LinkedHashSet<>(CATEGORICAL_MAXIMUMS.keySet());
        names.add("BMI");
        FEATURE_NAMES = Set.copyOf(names);
    }

    public static DiabetesScreeningRequest fromJson(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new InvalidRequestException("A diabetes screening JSON object is required.");
        }

        Map<String, String> errors = new LinkedHashMap<>();
        body.propertyNames().forEach(name -> {
            if (!FEATURE_NAMES.contains(name)) {
                errors.put(name, "is not an accepted survey feature");
            }
        });
        for (String name : FEATURE_NAMES) {
            if (!body.has(name)) {
                errors.put(name, "is required");
            }
        }

        Map<String, Integer> categories = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : CATEGORICAL_MAXIMUMS.entrySet()) {
            String name = entry.getKey();
            JsonNode value = body.get(name);
            if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
                errors.put(name, "must be an integer JSON number");
                continue;
            }
            int category = value.intValue();
            int minimum = CATEGORICAL_MINIMUMS.getOrDefault(name, 0);
            if (category < minimum || category > entry.getValue()) {
                errors.put(name, "must be between " + minimum + " and " + entry.getValue());
                continue;
            }
            categories.put(name, category);
        }

        JsonNode bmiNode = body.get("BMI");
        double bmi = Double.NaN;
        if (bmiNode == null || !bmiNode.isNumber() || !Double.isFinite(bmiNode.doubleValue())) {
            errors.put("BMI", "must be a finite JSON number");
        } else {
            bmi = bmiNode.doubleValue();
        }

        if (!errors.isEmpty()) {
            throw new InvalidRequestException("Diabetes screening request validation failed.", errors);
        }
        return new DiabetesScreeningRequest(
                categories.get("HighBP"),
                categories.get("HighChol"),
                categories.get("CholCheck"),
                categories.get("Smoker"),
                categories.get("Stroke"),
                categories.get("HeartDiseaseorAttack"),
                categories.get("PhysActivity"),
                categories.get("Fruits"),
                categories.get("Veggies"),
                categories.get("HvyAlcoholConsump"),
                categories.get("AnyHealthcare"),
                categories.get("NoDocbcCost"),
                categories.get("DiffWalk"),
                categories.get("Sex"),
                bmi,
                categories.get("GenHlth"),
                categories.get("MentHlth"),
                categories.get("PhysHlth"),
                categories.get("Age"),
                categories.get("Education"),
                categories.get("Income"));
    }

    public Map<String, Object> toFeatures() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("HighBP", highBp);
        features.put("HighChol", highChol);
        features.put("CholCheck", cholCheck);
        features.put("Smoker", smoker);
        features.put("Stroke", stroke);
        features.put("HeartDiseaseorAttack", heartDiseaseorAttack);
        features.put("PhysActivity", physActivity);
        features.put("Fruits", fruits);
        features.put("Veggies", veggies);
        features.put("HvyAlcoholConsump", hvyAlcoholConsump);
        features.put("AnyHealthcare", anyHealthcare);
        features.put("NoDocbcCost", noDocbcCost);
        features.put("DiffWalk", diffWalk);
        features.put("Sex", sex);
        features.put("BMI", bmi);
        features.put("GenHlth", genHlth);
        features.put("MentHlth", mentHlth);
        features.put("PhysHlth", physHlth);
        features.put("Age", age);
        features.put("Education", education);
        features.put("Income", income);
        return features;
    }
}
