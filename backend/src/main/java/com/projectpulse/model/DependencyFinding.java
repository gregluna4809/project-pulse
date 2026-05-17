package com.projectpulse.model;

public record DependencyFinding(
        String ecosystem,
        String name,
        String version,
        String sourceFile,
        String scope,
        String notes
) {}
