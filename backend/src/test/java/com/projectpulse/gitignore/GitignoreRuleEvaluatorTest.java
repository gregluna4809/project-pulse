package com.projectpulse.gitignore;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectpulse.model.ProjectType;
import com.projectpulse.model.RuleFinding;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitignoreRuleEvaluatorTest {

    private final GitignoreRuleEvaluator evaluator = new GitignoreRuleEvaluator();

    // --- No .gitignore ---

    @Test
    void noGitignoreProducesNoStrengths() {
        assertThat(evaluator.strengths(GitignoreAnalysis.noGitignore(), List.of(ProjectType.MAVEN)))
                .isEmpty();
    }

    @Test
    void noGitignoreProducesNoImprovements() {
        assertThat(evaluator.improvements(GitignoreAnalysis.noGitignore(), List.of(ProjectType.MAVEN)))
                .isEmpty();
    }

    @Test
    void noGitignoreProducesNoRiskFlags() {
        assertThat(evaluator.riskFlags(GitignoreAnalysis.noGitignore(), List.of(ProjectType.NODE, ProjectType.REACT)))
                .isEmpty();
    }

    // --- Strengths ---

    @Test
    void gitignorePresentStrengthFiresWhenGitignoreExists() {
        GitignoreAnalysis analysis = fullCoverageAnalysis(List.of(ProjectType.MAVEN));

        assertThat(evaluator.strengths(analysis, List.of(ProjectType.MAVEN)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-present");
    }

    @Test
    void strongCoverageStrengthFiresWhenHygieneScoreIs80OrAbove() {
        GitignoreAnalysis analysis = analysisWithScore(85);

        assertThat(evaluator.strengths(analysis, List.of(ProjectType.MAVEN)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-strong-coverage");
    }

    @Test
    void strongCoverageStrengthDoesNotFireWhenHygieneScoreBelow80() {
        GitignoreAnalysis analysis = analysisWithScore(79);

        assertThat(evaluator.strengths(analysis, List.of(ProjectType.MAVEN)))
                .extracting(RuleFinding::ruleId)
                .doesNotContain("gitignore-strong-coverage");
    }

    @Test
    void exactlyAt80FiresStrongCoverageStrength() {
        GitignoreAnalysis analysis = analysisWithScore(80);

        assertThat(evaluator.strengths(analysis, List.of()))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-strong-coverage");
    }

    // --- Improvements ---

    @Test
    void missingTargetForJavaFiresBuildArtifactsImprovement() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of("target/"), List.of(ProjectType.MAVEN));

        assertThat(evaluator.improvements(analysis, List.of(ProjectType.MAVEN)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-missing-build-artifacts");
    }

    @Test
    void missingDistAndBuildForNodeFiresBuildArtifactsImprovement() {
        GitignoreAnalysis analysis = analysisWithMissing(
                List.of("dist/", "build/"), List.of(ProjectType.NODE, ProjectType.REACT));

        assertThat(evaluator.improvements(analysis, List.of(ProjectType.NODE, ProjectType.REACT)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-missing-build-artifacts");
    }

    @Test
    void missingPycacheForPythonFiresBuildArtifactsImprovement() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of("__pycache__/"), List.of(ProjectType.PYTHON));

        assertThat(evaluator.improvements(analysis, List.of(ProjectType.PYTHON)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-missing-build-artifacts");
    }

    @Test
    void missingLogPatternForJavaFiresDependencyArtifactsImprovement() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of("*.log"), List.of(ProjectType.MAVEN));

        assertThat(evaluator.improvements(analysis, List.of(ProjectType.MAVEN)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-missing-dependency-artifacts");
    }

    @Test
    void missingCoverageForNodeFiresDependencyArtifactsImprovement() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of("coverage/"), List.of(ProjectType.NODE));

        assertThat(evaluator.improvements(analysis, List.of(ProjectType.NODE)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-missing-dependency-artifacts");
    }

    @Test
    void missingPytestCacheForPythonFiresDependencyArtifactsImprovement() {
        GitignoreAnalysis analysis = analysisWithMissing(
                List.of(".pytest_cache/", "*.pyc"), List.of(ProjectType.PYTHON));

        assertThat(evaluator.improvements(analysis, List.of(ProjectType.PYTHON)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-missing-dependency-artifacts");
    }

    @Test
    void noBuildArtifactsImprovementWhenBuildPatternsAreCovered() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of(), List.of(ProjectType.MAVEN));

        assertThat(evaluator.improvements(analysis, List.of(ProjectType.MAVEN)))
                .extracting(RuleFinding::ruleId)
                .doesNotContain("gitignore-missing-build-artifacts");
    }

    // --- Risk flags ---

    @Test
    void missingDotEnvFiresEnvNotIgnoredRisk() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of(".env"), List.of(ProjectType.MAVEN));

        assertThat(evaluator.riskFlags(analysis, List.of(ProjectType.MAVEN)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-env-not-ignored");
    }

    @Test
    void missingVenvForPythonFiresVenvNotIgnoredRisk() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of(".venv/"), List.of(ProjectType.PYTHON));

        assertThat(evaluator.riskFlags(analysis, List.of(ProjectType.PYTHON)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-venv-not-ignored");
    }

    @Test
    void venvRiskDoesNotFireForNonPythonProject() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of(".venv/"), List.of(ProjectType.MAVEN));

        assertThat(evaluator.riskFlags(analysis, List.of(ProjectType.MAVEN)))
                .extracting(RuleFinding::ruleId)
                .doesNotContain("gitignore-venv-not-ignored");
    }

    @Test
    void missingNodeModulesForNodeProjectFiresNodeModulesRisk() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of("node_modules/"),
                List.of(ProjectType.NODE, ProjectType.REACT));

        assertThat(evaluator.riskFlags(analysis, List.of(ProjectType.NODE, ProjectType.REACT)))
                .extracting(RuleFinding::ruleId)
                .contains("gitignore-node-modules-not-ignored");
    }

    @Test
    void nodeModulesRiskDoesNotFireForNonNodeProject() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of("node_modules/"), List.of(ProjectType.PYTHON));

        assertThat(evaluator.riskFlags(analysis, List.of(ProjectType.PYTHON)))
                .extracting(RuleFinding::ruleId)
                .doesNotContain("gitignore-node-modules-not-ignored");
    }

    @Test
    void noRisksWhenAllCriticalPatternsAreCovered() {
        GitignoreAnalysis analysis = analysisWithMissing(List.of(),
                List.of(ProjectType.NODE, ProjectType.REACT, ProjectType.PYTHON));

        assertThat(evaluator.riskFlags(analysis, List.of(ProjectType.NODE, ProjectType.REACT, ProjectType.PYTHON)))
                .isEmpty();
    }

    // --- Helpers ---

    private GitignoreAnalysis fullCoverageAnalysis(List<ProjectType> projectTypes) {
        return new GitignoreAnalysis(true, List.of("target/", ".env", ".idea/"), List.of(), 100,
                "All expected patterns covered.");
    }

    private GitignoreAnalysis analysisWithScore(int score) {
        return new GitignoreAnalysis(true, List.of("target/"), List.of(), score, "N of M expected patterns covered.");
    }

    private GitignoreAnalysis analysisWithMissing(List<String> missing, List<ProjectType> projectTypes) {
        return new GitignoreAnalysis(true, List.of("target/"), missing, 50, "Partial coverage.");
    }
}
