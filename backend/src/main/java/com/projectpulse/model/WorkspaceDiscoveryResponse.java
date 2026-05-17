package com.projectpulse.model;

import java.util.List;

public record WorkspaceDiscoveryResponse(
        String rootPath,
        int workspacesFound,
        List<DiscoveredWorkspace> workspaces
) {
    public WorkspaceDiscoveryResponse {
        workspaces = List.copyOf(workspaces);
    }
}
