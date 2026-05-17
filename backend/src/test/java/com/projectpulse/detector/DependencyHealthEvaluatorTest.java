package com.projectpulse.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectpulse.model.DependencyFinding;
import com.projectpulse.model.DependencyHealthAssessment;
import com.projectpulse.model.HealthStatus;
import com.projectpulse.model.Severity;
import org.junit.jupiter.api.Test;

class DependencyHealthEvaluatorTest {

    private final DependencyHealthEvaluator evaluator = new DependencyHealthEvaluator();

    @Test
    void parseVersionExtractsConcreteVersionFromCommonSpecifiers() {
        assertThat(evaluator.parseVersion("^18.3.1")).hasValueSatisfying(version -> {
            assertThat(version.major()).isEqualTo(18);
            assertThat(version.minor()).isEqualTo(3);
        });
        assertThat(evaluator.parseVersion(">=0.110.0,<1.0")).hasValueSatisfying(version -> {
            assertThat(version.major()).isEqualTo(0);
            assertThat(version.minor()).isEqualTo(110);
        });
        assertThat(evaluator.parseVersion("version managed by parent")).isEmpty();
        assertThat(evaluator.parseVersion("")).isEmpty();
    }

    @Test
    void evaluateClassifiesSpringBootVersions() {
        assertHealth("spring-boot-starter-parent", "3.5.0", HealthStatus.CURRENT, Severity.INFO);
        assertHealth("spring-boot-starter-parent", "3.4.7", HealthStatus.CURRENT, Severity.INFO);
        assertHealth("spring-boot-starter-parent", "3.3.6", HealthStatus.ACCEPTABLE, Severity.LOW);
        assertHealth("spring-boot-starter-parent", "3.2.12", HealthStatus.UPGRADE_CANDIDATE, Severity.MEDIUM);
        assertHealth("spring-boot-starter-parent", "2.7.18", HealthStatus.LEGACY, Severity.HIGH);
        assertHealth("spring-boot-starter-parent", "1.5.22", HealthStatus.CRITICAL, Severity.CRITICAL);
    }

    @Test
    void evaluateClassifiesJavaVersions() {
        assertHealth("java", "21", HealthStatus.CURRENT, Severity.INFO);
        assertHealth("java", "17", HealthStatus.ACCEPTABLE, Severity.LOW);
        assertHealth("java", "11", HealthStatus.LEGACY, Severity.HIGH);
        assertHealth("java", "8", HealthStatus.CRITICAL, Severity.CRITICAL);
    }

    @Test
    void evaluateClassifiesReactVersions() {
        assertHealth("react", "^19.0.0", HealthStatus.CURRENT, Severity.INFO);
        assertHealth("react", "^18.3.1", HealthStatus.CURRENT, Severity.INFO);
        assertHealth("react", "^17.0.2", HealthStatus.ACCEPTABLE, Severity.LOW);
        assertHealth("react", "^16.14.0", HealthStatus.LEGACY, Severity.HIGH);
    }

    @Test
    void evaluateClassifiesRepresentativeCatalogEntries() {
        assertHealth("vite", "^5.4.0", HealthStatus.UPGRADE_CANDIDATE, Severity.MEDIUM);
        assertHealth("streamlit", "1.56.0", HealthStatus.CURRENT, Severity.INFO);
        assertHealth("fastapi", "0.100.0", HealthStatus.ACCEPTABLE, Severity.LOW);
        assertHealth("pandas", "3.0.2", HealthStatus.CURRENT, Severity.INFO);
        assertHealth("sqlalchemy", "1.4.54", HealthStatus.LEGACY, Severity.HIGH);
        assertHealth("express", "^4.18.2", HealthStatus.ACCEPTABLE, Severity.LOW);
    }

    @Test
    void evaluateUnknownDependencyReturnsUnknownHealth() {
        DependencyHealthAssessment assessment = evaluator.evaluate(finding("custom-library", "1.2.3"));

        assertThat(assessment.dependencyName()).isEqualTo("custom-library");
        assertThat(assessment.detectedVersion()).isEqualTo("1.2.3");
        assertThat(assessment.recommendedVersion()).isEmpty();
        assertThat(assessment.healthStatus()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(assessment.severity()).isEqualTo(Severity.INFO);
        assertThat(assessment.notes()).contains("No curated local version intelligence");
    }

    @Test
    void evaluateManagedDependencyClassifiesAsManagedWhenNotesContainsVersionManagedByParent() {
        DependencyFinding finding = new DependencyFinding(
                "Maven", "spring-boot-starter-web", "", "pom.xml", "compile", "version managed by parent");

        DependencyHealthAssessment assessment = evaluator.evaluate(finding);

        assertThat(assessment.dependencyName()).isEqualTo("spring-boot-starter-web");
        assertThat(assessment.detectedVersion()).isEmpty();
        assertThat(assessment.healthStatus()).isEqualTo(HealthStatus.MANAGED);
        assertThat(assessment.severity()).isEqualTo(Severity.INFO);
        assertThat(assessment.notes()).isNotBlank();
    }

    @Test
    void evaluateManagedClassificationAppliesToAnyDependencyNameNotJustKnownOnes() {
        DependencyFinding finding = new DependencyFinding(
                "Maven", "lombok", "", "pom.xml", "provided", "version managed by parent");

        DependencyHealthAssessment assessment = evaluator.evaluate(finding);

        assertThat(assessment.healthStatus()).isEqualTo(HealthStatus.MANAGED);
    }

    @Test
    void evaluateExplicitVersionIsNotClassifiedAsManagedEvenIfNameIsABomStarter() {
        // An explicit version on a Spring Boot starter should go through normal evaluation.
        DependencyHealthAssessment assessment = evaluator.evaluate(
                new DependencyFinding("Maven", "spring-boot-starter-web", "3.5.0", "pom.xml", "compile", ""));

        assertThat(assessment.healthStatus()).isNotEqualTo(HealthStatus.MANAGED);
    }

    private void assertHealth(String dependencyName, String version, HealthStatus status, Severity severity) {
        DependencyHealthAssessment assessment = evaluator.evaluate(finding(dependencyName, version));

        assertThat(assessment.dependencyName()).isEqualTo(dependencyName);
        assertThat(assessment.detectedVersion()).isEqualTo(version);
        assertThat(assessment.healthStatus()).isEqualTo(status);
        assertThat(assessment.severity()).isEqualTo(severity);
        assertThat(assessment.notes()).isNotBlank();
    }

    private DependencyFinding finding(String dependencyName, String version) {
        return new DependencyFinding("test", dependencyName, version, "manifest", "main", "");
    }
}
