package com.projectpulse.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectpulse.model.DependencyHealthAssessment;
import com.projectpulse.model.HealthStatus;
import com.projectpulse.model.HealthTier;
import com.projectpulse.model.ProjectHealthAssessment;
import com.projectpulse.model.RuleFinding;
import com.projectpulse.model.Severity;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthScoringServiceTest {

    private HealthScoringService service;

    private static final RuleFinding FINDING = new RuleFinding("id", "message", "evidence");
    private static final RuleFinding MISSING_CI = new RuleFinding(
            "missing-ci-workflow",
            "CI workflow not detected.",
            "Expected .github/workflows."
    );
    private static final RuleFinding NO_MANIFEST = new RuleFinding(
            "no-recognized-build-manifest",
            "No recognized build manifest detected.",
            "Expected a known manifest."
    );
    private static final RuleFinding COMPILED_BINARY = new RuleFinding(
            "compiled-binaries-detected",
            "Compiled binaries detected in workspace.",
            "tool.exe"
    );
    private static final RuleFinding GIT_RECENT_ACTIVITY = new RuleFinding(
            "git-recent-activity",
            "Active commit history detected.",
            ".git/logs/HEAD"
    );

    @BeforeEach
    void setUp() {
        service = new HealthScoringService();
    }

    @Test
    void noIssuesScores100WithExcellentTier() {
        ProjectHealthAssessment result = service.assess(List.of(), List.of(), List.of(), List.of());

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.tier()).isEqualTo(HealthTier.EXCELLENT);
        assertThat(result.summary()).isEqualTo("Healthy engineering posture.");
    }

    @Test
    void eachDefaultStrengthAddsTwoPoints() {
        List<RuleFinding> twoImprovements = List.of(FINDING, FINDING);

        int scoreWithoutStrength = service.assess(List.of(), twoImprovements, List.of(), List.of()).score();
        int scoreWithOneStrength = service.assess(List.of(FINDING), twoImprovements, List.of(), List.of()).score();
        int scoreWithTwoStrengths = service.assess(List.of(FINDING, FINDING), twoImprovements, List.of(), List.of()).score();

        assertThat(scoreWithOneStrength).isEqualTo(scoreWithoutStrength + 2);
        assertThat(scoreWithTwoStrengths).isEqualTo(scoreWithoutStrength + 4);
    }

    @Test
    void strengthBonusIsCappedAtEight() {
        List<RuleFinding> threeImprovements = List.of(FINDING, FINDING, FINDING);

        int fourStrengths = service.assess(nCopies(4), threeImprovements, List.of(), List.of()).score();
        int fiveStrengths = service.assess(nCopies(5), threeImprovements, List.of(), List.of()).score();

        assertThat(fourStrengths).isEqualTo(90);
        assertThat(fiveStrengths).isEqualTo(90);
    }

    @Test
    void gitActivityStrengthAddsOnlyOnePoint() {
        List<RuleFinding> twoImprovements = List.of(FINDING, FINDING);

        int scoreWithoutGitActivity = service.assess(List.of(), twoImprovements, List.of(), List.of()).score();
        int scoreWithGitActivity = service.assess(List.of(GIT_RECENT_ACTIVITY), twoImprovements, List.of(), List.of()).score();

        assertThat(scoreWithGitActivity).isEqualTo(scoreWithoutGitActivity + 1);
    }

    @Test
    void eachStandardImprovementDeductsSixPoints() {
        int oneImprovement = service.assess(List.of(), List.of(FINDING), List.of(), List.of()).score();
        int twoImprovements = service.assess(List.of(), List.of(FINDING, FINDING), List.of(), List.of()).score();

        assertThat(oneImprovement).isEqualTo(94);
        assertThat(twoImprovements).isEqualTo(88);
    }

    @Test
    void missingCiWorkflowDeductsTenPoints() {
        assertThat(service.assess(List.of(), List.of(MISSING_CI), List.of(), List.of()).score())
                .isEqualTo(90);
    }

    @Test
    void unknownGenericWorkspaceManifestPenaltyIsMaterial() {
        assertThat(service.assess(List.of(), List.of(NO_MANIFEST), List.of(), List.of()).score())
                .isEqualTo(82);
    }

    @Test
    void eachStandardRiskFlagDeductsFifteenPoints() {
        int oneRisk = service.assess(List.of(), List.of(), List.of(FINDING), List.of()).score();
        int twoRisks = service.assess(List.of(), List.of(), List.of(FINDING, FINDING), List.of()).score();

        assertThat(oneRisk).isEqualTo(85);
        assertThat(twoRisks).isEqualTo(70);
    }

    @Test
    void compiledBinaryRiskDeductsTwentyFivePoints() {
        assertThat(service.assess(List.of(), List.of(), List.of(COMPILED_BINARY), List.of()).score())
                .isEqualTo(75);
    }

    @Test
    void dependencyHealthPenaltiesAreApplied() {
        assertThat(scoreWithSingleDep(HealthStatus.CURRENT)).isEqualTo(100);
        assertThat(scoreWithSingleDep(HealthStatus.ACCEPTABLE)).isEqualTo(99);
        assertThat(scoreWithSingleDep(HealthStatus.UPGRADE_CANDIDATE)).isEqualTo(96);
        assertThat(scoreWithSingleDep(HealthStatus.LEGACY)).isEqualTo(90);
        assertThat(scoreWithSingleDep(HealthStatus.CRITICAL)).isEqualTo(80);
        assertThat(scoreWithSingleDep(HealthStatus.UNKNOWN)).isEqualTo(100);
        assertThat(scoreWithSingleDep(HealthStatus.MANAGED)).isEqualTo(100);
    }

    @Test
    void managedDependencyHasZeroPenalty() {
        ProjectHealthAssessment result = service.assess(
                List.of(), List.of(), List.of(),
                List.of(dep(HealthStatus.MANAGED), dep(HealthStatus.MANAGED), dep(HealthStatus.MANAGED)));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.tier()).isEqualTo(HealthTier.EXCELLENT);
    }

    @Test
    void multipleDependencyPenaltiesAccumulate() {
        List<DependencyHealthAssessment> deps = List.of(
                dep(HealthStatus.LEGACY),
                dep(HealthStatus.CRITICAL),
                dep(HealthStatus.ACCEPTABLE)
        );
        assertThat(service.assess(List.of(), List.of(), List.of(), deps).score()).isEqualTo(69);
    }

    @Test
    void scoreIsClampedToZeroWhenPenaltiesExceedBase() {
        ProjectHealthAssessment result = service.assess(List.of(), List.of(), nCopies(7), List.of());

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.tier()).isEqualTo(HealthTier.CRITICAL);
    }

    @Test
    void scoreIsClampedToOneHundredWhenStrengthsExceedBase() {
        ProjectHealthAssessment result = service.assess(nCopies(10), List.of(), List.of(), List.of());

        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void cppStyleAdHocWorkspaceShouldNotBeGood() {
        ProjectHealthAssessment result = service.assess(
                List.of(),
                List.of(NO_MANIFEST),
                List.of(COMPILED_BINARY),
                List.of()
        );

        assertThat(result.score()).isEqualTo(57);
        assertThat(result.tier()).isEqualTo(HealthTier.AT_RISK);
    }

    @Test
    void realHealthyProjectsRemainGoodOrExcellent() {
        ProjectHealthAssessment excellent = service.assess(
                List.of(
                        new RuleFinding("maven-project-detected", "Maven project detected.", "pom.xml"),
                        new RuleFinding("readme-detected", "README detected.", "README.md"),
                        new RuleFinding("automated-tests-detected", "Automated tests detected.", "1 test file(s)"),
                        new RuleFinding("github-actions-detected", "GitHub Actions workflow detected.", ".github/workflows"),
                        new RuleFinding("env-example-detected", "Environment example file detected.", ".env.example")
                ),
                List.of(),
                List.of(),
                List.of(dep(HealthStatus.MANAGED), dep(HealthStatus.CURRENT))
        );

        ProjectHealthAssessment good = service.assess(
                List.of(
                        new RuleFinding("maven-project-detected", "Maven project detected.", "pom.xml"),
                        new RuleFinding("readme-detected", "README detected.", "README.md"),
                        new RuleFinding("automated-tests-detected", "Automated tests detected.", "1 test file(s)")
                ),
                List.of(FINDING, FINDING, FINDING),
                List.of(),
                List.of(dep(HealthStatus.ACCEPTABLE))
        );

        assertThat(excellent.tier()).isEqualTo(HealthTier.EXCELLENT);
        assertThat(good.tier()).isEqualTo(HealthTier.GOOD);
    }

    @Test
    void gitActivityAloneCannotRescueWeakProjects() {
        ProjectHealthAssessment result = service.assess(
                List.of(
                        new RuleFinding("git-repository-detected", "Git repository detected.", ".git"),
                        GIT_RECENT_ACTIVITY
                ),
                List.of(FINDING, FINDING, FINDING, MISSING_CI),
                List.of(FINDING),
                List.of()
        );

        assertThat(result.score()).isEqualTo(59);
        assertThat(result.tier()).isEqualTo(HealthTier.AT_RISK);
    }

    @Test
    void tierBoundariesMapCorrectly() {
        assertThat(service.assess(List.of(), List.of(), List.of(), List.of()).tier())
                .isEqualTo(HealthTier.EXCELLENT);
        assertThat(service.assess(List.of(), List.of(), List.of(), List.of(dep(HealthStatus.LEGACY))).tier())
                .isEqualTo(HealthTier.EXCELLENT);

        assertThat(service.assess(List.of(), nCopies(2), List.of(), List.of()).tier())
                .isEqualTo(HealthTier.GOOD);
        assertThat(service.assess(List.of(), List.of(), nCopies(1), List.of()).tier())
                .isEqualTo(HealthTier.GOOD);
        assertThat(service.assess(List.of(), nCopies(4), List.of(), List.of()).tier())
                .isEqualTo(HealthTier.GOOD);

        assertThat(service.assess(List.of(), nCopies(5), List.of(), List.of()).tier())
                .isEqualTo(HealthTier.FAIR);
        assertThat(service.assess(List.of(), nCopies(5), List.of(), List.of(dep(HealthStatus.LEGACY))).tier())
                .isEqualTo(HealthTier.FAIR);

        assertThat(service.assess(List.of(), List.of(), nCopies(3), List.of()).tier())
                .isEqualTo(HealthTier.AT_RISK);
        assertThat(service.assess(List.of(), List.of(), nCopies(4), List.of()).tier())
                .isEqualTo(HealthTier.AT_RISK);

        assertThat(service.assess(List.of(), List.of(), nCopies(5), List.of()).tier())
                .isEqualTo(HealthTier.CRITICAL);
        assertThat(service.assess(List.of(), List.of(), nCopies(7), List.of()).tier())
                .isEqualTo(HealthTier.CRITICAL);
    }

    @Test
    void summaryTextMatchesTier() {
        assertThat(service.assess(List.of(), List.of(), List.of(), List.of()).summary())
                .isEqualTo("Healthy engineering posture.");

        assertThat(service.assess(List.of(), nCopies(2), List.of(), List.of()).summary())
                .isEqualTo("Good engineering posture with minor improvements available.");

        assertThat(service.assess(List.of(), nCopies(5), List.of(), List.of()).summary())
                .isEqualTo("Moderate engineering debt detected.");

        assertThat(service.assess(List.of(), List.of(), nCopies(3), List.of()).summary())
                .isEqualTo("Significant engineering risk detected.");

        assertThat(service.assess(List.of(), List.of(), nCopies(7), List.of()).summary())
                .isEqualTo("Critical engineering issues require immediate attention.");
    }

    private int scoreWithSingleDep(HealthStatus status) {
        return service.assess(List.of(), List.of(), List.of(), List.of(dep(status))).score();
    }

    private DependencyHealthAssessment dep(HealthStatus status) {
        return new DependencyHealthAssessment("dep", "1.0", "2.0", status, Severity.INFO, "");
    }

    private List<RuleFinding> nCopies(int n) {
        return Collections.nCopies(n, FINDING);
    }
}
