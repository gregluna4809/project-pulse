package com.projectpulse.model;

import java.util.List;

public record ScanResponse(
        int workspacesFound,
        List<ProjectWorkspace> workspaces
) {
    public ScanResponse {
        workspaces = List.copyOf(workspaces);
    }
}
