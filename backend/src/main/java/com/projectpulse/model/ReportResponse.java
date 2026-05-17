package com.projectpulse.model;

import java.time.Instant;

public record ReportResponse(
        String reportPath,
        Instant generatedAt,
        String format
) {}
