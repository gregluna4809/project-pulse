package com.projectpulse.model;

public record ProjectHealthAssessment(
        int score,
        HealthTier tier,
        String summary
) {}
