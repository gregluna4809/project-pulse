package com.projectpulse.model;

import java.util.List;

public record TestAnalysis(
        boolean hasTests,
        int testFileCount,
        int integrationTestFileCount,
        List<String> detectedFrameworks,
        boolean hasTestScript,
        List<String> testDirectories,
        String notes
) {
    public TestAnalysis {
        detectedFrameworks = List.copyOf(detectedFrameworks);
        testDirectories = List.copyOf(testDirectories);
    }

    public static TestAnalysis none() {
        return new TestAnalysis(false, 0, 0, List.of(), false, List.of(), null);
    }
}
