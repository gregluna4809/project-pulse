package com.projectpulse.gitignore;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectpulse.model.ProjectType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitignoreReaderTest {

    @TempDir
    Path tempDir;

    private final GitignoreReader reader = new GitignoreReader();

    // --- No .gitignore ---

    @Test
    void noGitignoreFileReturnsNoGitignoreAnalysis() {
        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.MAVEN));

        assertThat(result.hasGitignore()).isFalse();
        assertThat(result.ignoredPatterns()).isEmpty();
        assertThat(result.missingExpectedPatterns()).isEmpty();
        assertThat(result.hygieneScore()).isEqualTo(0);
        assertThat(result.notes()).isNull();
    }

    // --- Pattern parsing ---

    @Test
    void parsesPatternLinesSkippingCommentsAndBlankLines() throws IOException {
        writeGitignore(
                "# This is a comment",
                "",
                "target/",
                "   ",
                "# another comment",
                "*.log"
        );

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.MAVEN));

        assertThat(result.ignoredPatterns()).containsExactly("target/", "*.log");
    }

    @Test
    void emptyGitignoreYieldsZeroHygieneScore() throws IOException {
        writeGitignore("# nothing here");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.MAVEN));

        assertThat(result.hasGitignore()).isTrue();
        assertThat(result.ignoredPatterns()).isEmpty();
        assertThat(result.hygieneScore()).isEqualTo(0);
    }

    // --- Java / Maven expected patterns ---

    @Test
    void javaProjectWithFullCoverageScores100() throws IOException {
        writeGitignore("target/", "*.log", ".env", ".idea/", ".vscode/", "Thumbs.db", ".DS_Store");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT));

        assertThat(result.hasGitignore()).isTrue();
        assertThat(result.missingExpectedPatterns()).isEmpty();
        assertThat(result.hygieneScore()).isEqualTo(100);
        assertThat(result.notes()).isEqualTo("All expected patterns covered.");
    }

    @Test
    void javaProjectMissingTargetHasItInMissingList() throws IOException {
        writeGitignore("*.log", ".env", ".idea/", ".vscode/");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.MAVEN));

        assertThat(result.missingExpectedPatterns()).contains("target/");
        assertThat(result.hygieneScore()).isLessThan(100);
    }

    @Test
    void normalizeHandlesVariantFormatsForSamePattern() throws IOException {
        // target (no slash), /target, and target/ all normalize to "target"
        writeGitignore("target", "*.log", ".env", ".idea/", ".vscode/", "Thumbs.db", ".DS_Store");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT));

        assertThat(result.missingExpectedPatterns()).doesNotContain("target/");
    }

    @Test
    void dotEnvVariantMatchesDotEnvExpectedPattern() throws IOException {
        // /.env and .env* both normalize to match the expected ".env" pattern
        writeGitignore("target/", "*.log", "/.env", ".idea/", ".vscode/", "Thumbs.db", ".DS_Store");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT));

        assertThat(result.missingExpectedPatterns()).doesNotContain(".env");
    }

    // --- Node / React expected patterns ---

    @Test
    void nodeProjectMissingNodeModulesHasItInMissingList() throws IOException {
        writeGitignore("dist/", "build/", ".env", "coverage/", ".vscode/", ".idea/", "Thumbs.db", ".DS_Store");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.NODE, ProjectType.REACT));

        assertThat(result.missingExpectedPatterns()).contains("node_modules/");
        assertThat(result.hygieneScore()).isLessThan(100);
    }

    @Test
    void nodeProjectWithAllPatternsScores100() throws IOException {
        writeGitignore("node_modules/", "dist/", "build/", ".env", "coverage/",
                ".vscode/", ".idea/", "Thumbs.db", ".DS_Store");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.NODE, ProjectType.REACT));

        assertThat(result.missingExpectedPatterns()).isEmpty();
        assertThat(result.hygieneScore()).isEqualTo(100);
    }

    // --- Python expected patterns ---

    @Test
    void pythonProjectMissingVenvHasItInMissingList() throws IOException {
        writeGitignore("__pycache__/", "*.pyc", ".env", ".pytest_cache/", ".vscode/", ".idea/");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.PYTHON));

        assertThat(result.missingExpectedPatterns()).contains(".venv/");
    }

    @Test
    void pythonProjectMissingDotEnvHasItInMissingList() throws IOException {
        writeGitignore("__pycache__/", "*.pyc", ".venv/", ".pytest_cache/", ".vscode/", ".idea/");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.PYTHON));

        assertThat(result.missingExpectedPatterns()).contains(".env");
    }

    // --- Multi-tech (monorepo) ---

    @Test
    void multiTechProjectExpectsUnionOfPatterns() throws IOException {
        writeGitignore(
                "target/", "*.log", ".env", ".idea/",
                "node_modules/", "dist/", "build/", "coverage/",
                ".vscode/", "Thumbs.db", ".DS_Store"
        );

        GitignoreAnalysis result = reader.read(tempDir,
                List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT, ProjectType.NODE, ProjectType.REACT));

        assertThat(result.missingExpectedPatterns()).isEmpty();
        assertThat(result.hygieneScore()).isEqualTo(100);
    }

    @Test
    void partialCoverageProducesProportionalHygieneScore() throws IOException {
        // Java project expects: target/, *.log, .env, .idea/, .vscode/, Thumbs.db, .DS_Store (7 patterns)
        // Covering 4 of 7 = ~57%
        writeGitignore("target/", "*.log", ".env", ".idea/");

        GitignoreAnalysis result = reader.read(tempDir, List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT));

        assertThat(result.hygieneScore()).isGreaterThan(0).isLessThan(100);
        assertThat(result.notes()).contains("of");
    }

    // --- Empty projectTypes (general patterns only) ---

    @Test
    void noProjectTypesOnlyExpectsGeneralPatterns() throws IOException {
        writeGitignore(".vscode/", ".idea/", "Thumbs.db", ".DS_Store", ".env");

        GitignoreAnalysis result = reader.read(tempDir, List.of());

        assertThat(result.missingExpectedPatterns()).isEmpty();
        assertThat(result.hygieneScore()).isEqualTo(100);
    }

    // --- Helpers ---

    private void writeGitignore(String... lines) throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), String.join("\n", lines));
    }
}
