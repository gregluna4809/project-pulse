package com.projectpulse.tests;

import com.projectpulse.model.TestAnalysis;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class TestAnalysisReader {

    private static final Pattern NODE_TEST_FILE = Pattern.compile(".*\\.(test|spec)\\.(ts|tsx|js)$");
    private static final Pattern PYTHON_TEST_FILE = Pattern.compile("(test_.*|.*_test)\\.py$");

    public TestAnalysis read(Path projectPath) {
        Set<String> testDirectories = new LinkedHashSet<>();
        Set<String> frameworks = new LinkedHashSet<>();
        List<Path> testFiles = new ArrayList<>();
        List<Path> integrationTestFiles = new ArrayList<>();

        detectTestDirectories(projectPath, testDirectories);
        detectTestFiles(projectPath, testFiles, integrationTestFiles);
        detectFrameworksFromFiles(testFiles, frameworks);
        boolean hasTestScript = detectPackageJson(projectPath, frameworks);
        detectPythonManifestFrameworks(projectPath, frameworks);

        String notes = testFiles.isEmpty()
                ? "No test files detected by supported read-only patterns."
                : null;

        return new TestAnalysis(
                !testFiles.isEmpty(),
                testFiles.size(),
                integrationTestFiles.size(),
                frameworks.stream().sorted().toList(),
                hasTestScript,
                testDirectories.stream().sorted().toList(),
                notes
        );
    }

    private void detectTestDirectories(Path projectPath, Set<String> testDirectories) {
        addDirectoryIfExists(projectPath, "src/test", testDirectories);
        addDirectoryIfExists(projectPath, "tests", testDirectories);
        addDirectoryIfExists(projectPath, "__tests__", testDirectories);
    }

    private void addDirectoryIfExists(Path projectPath, String directory, Set<String> testDirectories) {
        if (Files.isDirectory(projectPath.resolve(directory))) {
            testDirectories.add(directory);
        }
    }

    private void detectTestFiles(Path projectPath, List<Path> testFiles, List<Path> integrationTestFiles) {
        try (Stream<Path> stream = Files.walk(projectPath, 8)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> projectPath.relativize(path).toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String lowerRelativePath = projectPath.relativize(path).toString()
                                .replace('\\', '/')
                                .toLowerCase(Locale.ROOT);

                        if (isTestFile(fileName, lowerRelativePath)) {
                            testFiles.add(path);
                        }
                        if (isIntegrationTestFile(fileName, lowerRelativePath)) {
                            integrationTestFiles.add(path);
                        }
                    });
        } catch (IOException ignored) {
            // Test intelligence is best-effort and read-only.
        }
    }

    private boolean isTestFile(String fileName, String lowerRelativePath) {
        return isJavaTestFile(fileName)
                || NODE_TEST_FILE.matcher(fileName).matches()
                || PYTHON_TEST_FILE.matcher(fileName).matches()
                || isCppTestFile(fileName)
                || lowerRelativePath.startsWith("__tests__/")
                || lowerRelativePath.contains("/__tests__/")
                || lowerRelativePath.startsWith("src/test/");
    }

    private boolean isIntegrationTestFile(String fileName, String lowerRelativePath) {
        return fileName.endsWith("IT.java")
                || fileName.endsWith("IntegrationTest.java")
                || lowerRelativePath.contains("integration");
    }

    private boolean isJavaTestFile(String fileName) {
        return fileName.endsWith("Test.java")
                || fileName.endsWith("Tests.java")
                || fileName.endsWith("IT.java")
                || fileName.endsWith("IntegrationTest.java");
    }

    private boolean isCppTestFile(String fileName) {
        return fileName.endsWith("_test.cpp")
                || fileName.endsWith("Test.cpp")
                || fileName.endsWith("_spec.cpp");
    }

    private void detectFrameworksFromFiles(List<Path> testFiles, Set<String> frameworks) {
        for (Path testFile : testFiles) {
            String fileName = testFile.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fileName.endsWith(".java")) {
                String content = readLower(testFile);
                addIfContains(content, frameworks, "junit", "JUnit");
                addIfContains(content, frameworks, "springboottest", "SpringBootTest");
                addIfContains(content, frameworks, "mockito", "Mockito");
            }
            if (fileName.endsWith(".py")) {
                String content = readLower(testFile);
                addIfContains(content, frameworks, "pytest", "pytest");
                addIfContains(content, frameworks, "unittest", "unittest");
                addIfContains(content, frameworks, "nose", "nose");
            }
            if (fileName.endsWith(".cpp")) {
                String content = readLower(testFile);
                addIfContains(content, frameworks, "gtest", "gtest");
                addIfContains(content, frameworks, "catch2", "catch2");
                addIfContains(content, frameworks, "doctest", "doctest");
            }
        }
    }

    private boolean detectPackageJson(Path projectPath, Set<String> frameworks) {
        Path packageJson = projectPath.resolve("package.json");
        if (!Files.isRegularFile(packageJson)) {
            return false;
        }

        String content = readLower(packageJson);
        addIfContains(content, frameworks, "jest", "jest");
        addIfContains(content, frameworks, "vitest", "vitest");
        addIfContains(content, frameworks, "testing-library", "testing-library");
        addIfContains(content, frameworks, "cypress", "cypress");
        addIfContains(content, frameworks, "playwright", "playwright");

        return content.contains("\"scripts\"") && Pattern.compile("\"test\"\\s*:").matcher(content).find();
    }

    private void detectPythonManifestFrameworks(Path projectPath, Set<String> frameworks) {
        for (String manifest : List.of("requirements.txt", "pyproject.toml")) {
            Path path = projectPath.resolve(manifest);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            String content = readLower(path);
            addIfContains(content, frameworks, "pytest", "pytest");
            addIfContains(content, frameworks, "unittest", "unittest");
            addIfContains(content, frameworks, "nose", "nose");
        }
    }

    private void addIfContains(String content, Set<String> frameworks, String token, String framework) {
        if (content.contains(token)) {
            frameworks.add(framework);
        }
    }

    private String readLower(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            return "";
        }
    }
}
