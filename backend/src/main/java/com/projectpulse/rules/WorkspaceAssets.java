package com.projectpulse.rules;

public record WorkspaceAssets(
        boolean hasReadme,
        boolean hasGitignore,
        boolean hasCiWorkflow,
        boolean hasEnvTemplate
) {
    public static WorkspaceAssets none() {
        return new WorkspaceAssets(false, false, false, false);
    }
}
