package com.projectpulse.model;

import jakarta.validation.constraints.NotBlank;

public record WorkspaceDiscoveryRequest(
        @NotBlank(message = "rootPath is required")
        String rootPath
) {
}
