package com.projectpulse.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitMetadataReaderTest {

    @TempDir
    Path tempDir;

    private final GitMetadataReader reader = new GitMetadataReader();

    // --- Not a git repository ---

    @Test
    void nonGitDirectoryReturnsNoGitAnalysis() {
        GitAnalysis result = reader.read(tempDir);

        assertThat(result.gitRepository()).isFalse();
        assertThat(result.activityStatus()).isEqualTo(GitActivityStatus.NO_GIT);
        assertThat(result.remoteNames()).isEmpty();
        assertThat(result.branchCount()).isEqualTo(0);
        assertThat(result.hasRemote()).isFalse();
        assertThat(result.detachedHead()).isFalse();
        assertThat(result.currentBranch()).isNull();
        assertThat(result.headCommit()).isNull();
        assertThat(result.daysSinceLastCommit()).isNull();
    }

    // --- HEAD parsing ---

    @Test
    void parsesCurrentBranchFromHeadFile() throws IOException {
        createMinimalGitRepo(tempDir, "main");

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.gitRepository()).isTrue();
        assertThat(result.currentBranch()).isEqualTo("main");
        assertThat(result.detachedHead()).isFalse();
    }

    @Test
    void parsesNestedBranchNameWithSlash() throws IOException {
        createMinimalGitRepo(tempDir, "feature/my-feature");

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.currentBranch()).isEqualTo("feature/my-feature");
        assertThat(result.detachedHead()).isFalse();
    }

    @Test
    void detectsDetachedHeadState() throws IOException {
        Path gitDir = Files.createDirectory(tempDir.resolve(".git"));
        Files.writeString(gitDir.resolve("HEAD"),
                "abc123def456abc123def456abc123def456abc1\n");

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.gitRepository()).isTrue();
        assertThat(result.currentBranch()).isNull();
        assertThat(result.detachedHead()).isTrue();
        assertThat(result.headCommit()).isEqualTo("abc123de");
    }

    // --- Remote detection ---

    @Test
    void parsesRemoteNamesFromGitConfig() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        Files.writeString(gitDir.resolve("config"), """
                [core]
                    repositoryformatversion = 0
                [remote "origin"]
                    url = https://github.com/foo/bar.git
                    fetch = +refs/heads/*:refs/remotes/origin/*
                [remote "upstream"]
                    url = https://github.com/baz/bar.git
                """);

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.hasRemote()).isTrue();
        assertThat(result.remoteNames()).containsExactlyInAnyOrder("origin", "upstream");
    }

    @Test
    void noRemoteWhenConfigHasNoRemoteSections() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        Files.writeString(gitDir.resolve("config"), "[core]\n    repositoryformatversion = 0\n");

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.hasRemote()).isFalse();
        assertThat(result.remoteNames()).isEmpty();
    }

    @Test
    void noRemoteWhenConfigFileIsMissing() throws IOException {
        createMinimalGitRepo(tempDir, "main"); // no config file written

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.hasRemote()).isFalse();
    }

    // --- Branch counting ---

    @Test
    void countsLooseBranches() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        Path headsDir = Files.createDirectories(gitDir.resolve("refs").resolve("heads"));
        Files.writeString(headsDir.resolve("main"), "abc123\n");
        Files.writeString(headsDir.resolve("feature-x"), "def456\n");

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.branchCount()).isEqualTo(2);
    }

    @Test
    void countsPackedBranches() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        Files.writeString(gitDir.resolve("packed-refs"), """
                # pack-refs with: peeled fully-peeled sorted
                abc123def456abc123def456abc123def456abc1 refs/heads/main
                def456abc123def456abc123def456abc123def4 refs/heads/release-1.0
                """);

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.branchCount()).isEqualTo(2);
    }

    @Test
    void deduplicatesLooseAndPackedBranches() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        Path headsDir = Files.createDirectories(gitDir.resolve("refs").resolve("heads"));
        Files.writeString(headsDir.resolve("main"), "abc123\n");
        Files.writeString(gitDir.resolve("packed-refs"),
                "abc123def456abc123def456abc123def456abc1 refs/heads/main\n"
                        + "def456abc123def456abc123def456abc123def4 refs/heads/feature\n");

        GitAnalysis result = reader.read(tempDir);

        // "main" appears in both loose and packed; should be counted once.
        assertThat(result.branchCount()).isEqualTo(2);
    }

    // --- Commit date and activity status ---

    @Test
    void unknownActivityWhenNoReflog() throws IOException {
        createMinimalGitRepo(tempDir, "main");

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.activityStatus()).isEqualTo(GitActivityStatus.UNKNOWN);
        assertThat(result.daysSinceLastCommit()).isNull();
        assertThat(result.lastCommitDate()).isNull();
    }

    @Test
    void parsesLastCommitDateAndHeadCommitFromReflog() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        long recentEpoch = Instant.now().minus(5, ChronoUnit.DAYS).getEpochSecond();
        writeReflog(gitDir, "abc123def456abc123def456abc123def456abc1", recentEpoch);

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.lastCommitDate()).isNotNull();
        assertThat(result.daysSinceLastCommit()).isNotNull().isLessThanOrEqualTo(6);
        assertThat(result.headCommit()).isEqualTo("abc123de");
    }

    @Test
    void activeSummaryForRecentCommit() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        long nowEpoch = Instant.now().getEpochSecond();
        writeReflog(gitDir, "abc123def456abc123def456abc123def456abc1", nowEpoch);

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.activityStatus()).isEqualTo(GitActivityStatus.ACTIVE);
        assertThat(result.daysSinceLastCommit()).isLessThanOrEqualTo(1);
    }

    @Test
    void quietSummaryForCommitBetween31And90Days() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        long epoch = Instant.now().minus(60, ChronoUnit.DAYS).getEpochSecond();
        writeReflog(gitDir, "abc123def456abc123def456abc123def456abc1", epoch);

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.activityStatus()).isEqualTo(GitActivityStatus.QUIET);
    }

    @Test
    void staleSummaryForCommitBetween91And180Days() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        long epoch = Instant.now().minus(120, ChronoUnit.DAYS).getEpochSecond();
        writeReflog(gitDir, "abc123def456abc123def456abc123def456abc1", epoch);

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.activityStatus()).isEqualTo(GitActivityStatus.STALE);
        assertThat(result.daysSinceLastCommit()).isGreaterThan(90);
    }

    @Test
    void dormantSummaryForCommitOlderThan180Days() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        long oldEpoch = Instant.now().minus(200, ChronoUnit.DAYS).getEpochSecond();
        writeReflog(gitDir, "abc123def456abc123def456abc123def456abc1", oldEpoch);

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.activityStatus()).isEqualTo(GitActivityStatus.DORMANT);
        assertThat(result.daysSinceLastCommit()).isGreaterThan(180);
    }

    @Test
    void usesLastLineOfReflogNotFirst() throws IOException {
        Path gitDir = createMinimalGitRepo(tempDir, "main");
        long oldEpoch = Instant.now().minus(200, ChronoUnit.DAYS).getEpochSecond();
        long recentEpoch = Instant.now().minus(2, ChronoUnit.DAYS).getEpochSecond();
        Path logsDir = Files.createDirectories(gitDir.resolve("logs"));
        Files.writeString(logsDir.resolve("HEAD"),
                "0000000000000000000000000000000000000000 aaaa0000 Dev <d@e.com> " + oldEpoch + " +0000\tcommit: old\n"
                        + "aaaa0000 bbbb1111 Dev <d@e.com> " + recentEpoch + " +0000\tcommit: recent\n");

        GitAnalysis result = reader.read(tempDir);

        assertThat(result.activityStatus()).isEqualTo(GitActivityStatus.ACTIVE);
    }

    // --- Helpers ---

    private Path createMinimalGitRepo(Path projectDir, String branch) throws IOException {
        Path gitDir = Files.createDirectory(projectDir.resolve(".git"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/" + branch + "\n");
        Files.createDirectories(gitDir.resolve("refs").resolve("heads"));
        Files.createDirectories(gitDir.resolve("refs").resolve("remotes"));
        return gitDir;
    }

    private void writeReflog(Path gitDir, String newSha, long epochSeconds) throws IOException {
        Path logsDir = Files.createDirectories(gitDir.resolve("logs"));
        Files.writeString(logsDir.resolve("HEAD"),
                "0000000000000000000000000000000000000000 " + newSha
                        + " Dev User <dev@example.com> " + epochSeconds + " +0000\tcommit (initial): init\n");
    }
}
