package com.projectpulse.ci;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectpulse.model.RuleFinding;
import java.util.List;
import org.junit.jupiter.api.Test;

class CiRuleEvaluatorTest {

    private final CiRuleEvaluator evaluator = new CiRuleEvaluator();

    @Test
    void noCiProducesNoStrengths() {
        assertThat(evaluator.strengths(CiAnalysis.noCi())).isEmpty();
    }

    @Test
    void noCiProducesNoImprovements() {
        assertThat(evaluator.improvements(CiAnalysis.noCi())).isEmpty();
    }

    @Test
    void noCiProducesNoRiskFlags() {
        assertThat(evaluator.riskFlags(CiAnalysis.noCi())).isEmpty();
    }

    @Test
    void pullRequestTriggerProducesStrength() {
        CiAnalysis ci = ciWith(true, false, false, false, false);
        assertThat(evaluator.strengths(ci))
                .extracting(RuleFinding::ruleId)
                .contains("ci-pull-request-validation");
    }

    @Test
    void noPullRequestTriggerDoesNotProducePrStrength() {
        CiAnalysis ci = ciWith(false, false, false, false, false);
        assertThat(evaluator.strengths(ci))
                .extracting(RuleFinding::ruleId)
                .doesNotContain("ci-pull-request-validation");
    }

    @Test
    void buildJobProducesStrength() {
        CiAnalysis ci = ciWith(false, false, true, false, false);
        assertThat(evaluator.strengths(ci))
                .extracting(RuleFinding::ruleId)
                .contains("ci-build-job-detected");
    }

    @Test
    void testJobProducesStrength() {
        CiAnalysis ci = ciWith(false, false, false, true, false);
        assertThat(evaluator.strengths(ci))
                .extracting(RuleFinding::ruleId)
                .contains("ci-test-job-detected");
    }

    @Test
    void noPullRequestTriggerProducesImprovement() {
        CiAnalysis ci = ciWith(false, false, false, false, false);
        assertThat(evaluator.improvements(ci))
                .extracting(RuleFinding::ruleId)
                .contains("ci-no-pull-request-trigger");
    }

    @Test
    void pullRequestTriggerSuppressesPrImprovement() {
        CiAnalysis ci = ciWith(true, false, false, false, false);
        assertThat(evaluator.improvements(ci))
                .extracting(RuleFinding::ruleId)
                .doesNotContain("ci-no-pull-request-trigger");
    }

    @Test
    void noTestJobProducesImprovement() {
        CiAnalysis ci = ciWith(false, false, false, false, false);
        assertThat(evaluator.improvements(ci))
                .extracting(RuleFinding::ruleId)
                .contains("ci-no-test-job");
    }

    @Test
    void noBuildJobProducesImprovement() {
        CiAnalysis ci = ciWith(false, false, false, false, false);
        assertThat(evaluator.improvements(ci))
                .extracting(RuleFinding::ruleId)
                .contains("ci-no-build-job");
    }

    @Test
    void allCoveredProducesNoImprovements() {
        CiAnalysis ci = ciWith(true, false, true, true, false);
        assertThat(evaluator.improvements(ci)).isEmpty();
    }

    @Test
    void deployWithoutManualTriggerProducesRiskFlag() {
        CiAnalysis ci = ciWith(false, false, false, false, true);
        assertThat(evaluator.riskFlags(ci))
                .extracting(RuleFinding::ruleId)
                .contains("ci-deploy-without-manual-trigger");
    }

    @Test
    void deployWithManualTriggerProducesNoRiskFlag() {
        CiAnalysis ci = ciWith(false, true, false, false, true);
        assertThat(evaluator.riskFlags(ci))
                .extracting(RuleFinding::ruleId)
                .doesNotContain("ci-deploy-without-manual-trigger");
    }

    @Test
    void noDeployJobProducesNoDeployRisk() {
        CiAnalysis ci = ciWith(false, false, false, false, false);
        assertThat(evaluator.riskFlags(ci)).isEmpty();
    }

    // hasPr, hasManual, hasBuild, hasTest, hasDeploy
    private CiAnalysis ciWith(boolean hasPr, boolean hasManual, boolean hasBuild, boolean hasTest, boolean hasDeploy) {
        return new CiAnalysis(
                true, List.of("ci.yml"), 1,
                false, hasPr, hasManual,
                hasBuild, hasTest, hasDeploy,
                List.of(), null
        );
    }
}
