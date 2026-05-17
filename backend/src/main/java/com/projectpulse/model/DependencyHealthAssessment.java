package com.projectpulse.model;

public record DependencyHealthAssessment(
        String dependencyName,
        String detectedVersion,
        String recommendedVersion,
        HealthStatus healthStatus,
        Severity severity,
        String notes
) {}
