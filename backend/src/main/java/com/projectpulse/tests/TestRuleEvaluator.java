package com.projectpulse.tests;

import com.projectpulse.ci.CiAnalysis;
import com.projectpulse.model.ProjectType;
import com.projectpulse.model.RuleFinding;
import com.projectpulse.model.TestAnalysis;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TestRuleEvaluator {

    public List<RuleFinding> strengths(TestAnalysis testAnalysis) {
        List<RuleFinding> findings = new ArrayList<>();
        if (testAnalysis.hasTests()) {
            findings.add(new RuleFinding(
                    "automated-tests-detected",
                    "Automated tests detected.",
                    testAnalysis.testFileCount() + " test file(s)"
            ));
        }
        if (testAnalysis.integrationTestFileCount() > 0) {
            findings.add(new RuleFinding(
                    "integration-tests-detected",
                    "Integration tests detected.",
                    testAnalysis.integrationTestFileCount() + " integration test file(s)"
            ));
        }
        if (testAnalysis.hasTestScript()) {
            findings.add(new RuleFinding(
                    "test-script-detected",
                    "Test script detected.",
                    "package.json scripts.test"
            ));
        }
        if (!testAnalysis.detectedFrameworks().isEmpty()) {
            findings.add(new RuleFinding(
                    "known-test-framework-detected",
                    "Known test framework detected.",
                    String.join(", ", testAnalysis.detectedFrameworks())
            ));
        }
        return List.copyOf(findings);
    }

    public List<RuleFinding> improvements(TestAnalysis testAnalysis, List<ProjectType> projectTypes) {
        List<RuleFinding> findings = new ArrayList<>();
        if (!testAnalysis.hasTests()) {
            if (testAnalysis.testDirectories().isEmpty()) {
                findings.add(new RuleFinding(
                        "no-test-files-detected",
                        "No test files detected.",
                        "Expected recognizable Java, Node, Python, or C++ test files."
                ));
            } else {
                findings.add(new RuleFinding(
                        "test-directory-without-test-files",
                        "Test directory exists but no test files were detected.",
                        String.join(", ", testAnalysis.testDirectories())
                ));
            }
        }
        if (projectTypes.contains(ProjectType.NODE) && !testAnalysis.hasTestScript()) {
            findings.add(new RuleFinding(
                    "node-project-missing-test-script",
                    "No test script detected for Node project.",
                    "Expected package.json scripts.test."
            ));
        }
        return List.copyOf(findings);
    }

    public List<RuleFinding> riskFlags(
            TestAnalysis testAnalysis,
            List<ProjectType> projectTypes,
            List<String> technologies,
            CiAnalysis ciAnalysis
    ) {
        List<RuleFinding> findings = new ArrayList<>();
        if (ciAnalysis.hasCi() && !testAnalysis.hasTests()) {
            findings.add(new RuleFinding(
                    "ci-without-tests",
                    "CI workflow detected but no tests were detected.",
                    "CI workflow count: " + ciAnalysis.workflowCount()
            ));
        }
        if (isBackendOrApiProject(projectTypes, technologies) && !testAnalysis.hasTests()) {
            findings.add(new RuleFinding(
                    "backend-api-without-tests",
                    "Backend/API project has no tests detected.",
                    "Backend/API technology detected."
            ));
        }
        return List.copyOf(findings);
    }

    private boolean isBackendOrApiProject(List<ProjectType> projectTypes, List<String> technologies) {
        return projectTypes.contains(ProjectType.SPRING_BOOT)
                || technologies.contains("Express");
    }
}
