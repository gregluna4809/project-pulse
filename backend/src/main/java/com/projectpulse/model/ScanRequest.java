package com.projectpulse.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ScanRequest(
        @NotBlank(message = "rootPath is required")
        String rootPath,
        List<String> includeProjectNames,
        List<String> excludeProjectNames,
        List<String> includePaths,
        List<String> excludePaths
) {
    public ScanRequest(String rootPath) {
        this(rootPath, List.of(), List.of(), List.of(), List.of());
    }

    public ScanRequest {
        includeProjectNames = includeProjectNames == null ? List.of() : List.copyOf(includeProjectNames);
        excludeProjectNames = excludeProjectNames == null ? List.of() : List.copyOf(excludeProjectNames);
        includePaths = includePaths == null ? List.of() : List.copyOf(includePaths);
        excludePaths = excludePaths == null ? List.of() : List.copyOf(excludePaths);
    }
}
