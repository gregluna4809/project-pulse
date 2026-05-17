package com.projectpulse.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class GitMetadataReader {

    // Matches the new-sha (second field) at the start of a reflog line.
    private static final Pattern REFLOG_NEW_SHA = Pattern.compile("^[0-9a-f]+ ([0-9a-f]+) ");

    // Matches the Unix epoch timestamp and timezone offset inside a reflog line.
    // The epoch is a 9-12 digit integer followed by a space and a +/-HHMM timezone, then a tab.
    private static final Pattern REFLOG_TIMESTAMP = Pattern.compile("(\\d{9,12}) [+-]\\d{4}\\t");

    // Matches [remote "name"] sections in a git config file.
    private static final Pattern REMOTE_SECTION = Pattern.compile("\\[remote\\s+\"([^\"]+)\"\\]");

    public GitAnalysis read(Path projectPath) {
        Path gitDir = projectPath.resolve(".git");
        if (!Files.isDirectory(gitDir)) {
            return GitAnalysis.noGit();
        }

        HeadInfo head = readHead(gitDir);
        List<String> remoteNames = readRemoteNames(gitDir);
        int branchCount = countBranches(gitDir);
        Optional<CommitInfo> lastCommit = readLastCommitInfo(gitDir);

        boolean hasRemote = !remoteNames.isEmpty();
        String headCommit = resolveHeadCommit(head, lastCommit);

        Optional<LocalDate> lastCommitDate = lastCommit.map(CommitInfo::date);
        Integer daysSinceLastCommit = lastCommitDate
                .map(date -> (int) ChronoUnit.DAYS.between(date, LocalDate.now(ZoneOffset.UTC)))
                .orElse(null);

        GitActivityStatus activityStatus = computeActivityStatus(daysSinceLastCommit);
        String lastCommitDateStr = lastCommitDate.map(LocalDate::toString).orElse(null);

        return new GitAnalysis(
                true,
                head.branchName(),
                headCommit,
                lastCommitDateStr,
                daysSinceLastCommit,
                branchCount,
                hasRemote,
                remoteNames,
                head.detached(),
                activityStatus,
                null
        );
    }

    HeadInfo readHead(Path gitDir) {
        Path headFile = gitDir.resolve("HEAD");
        try {
            String content = Files.readString(headFile, StandardCharsets.UTF_8).strip();
            if (content.startsWith("ref: refs/heads/")) {
                return HeadInfo.onBranch(content.substring("ref: refs/heads/".length()));
            }
            // Detached HEAD — content is a full commit SHA.
            String shortSha = content.length() >= 8 ? content.substring(0, 8) : content;
            return HeadInfo.detached(shortSha);
        } catch (IOException ignored) {
            return HeadInfo.unknown();
        }
    }

    List<String> readRemoteNames(Path gitDir) {
        Path configFile = gitDir.resolve("config");
        if (!Files.exists(configFile)) {
            return List.of();
        }
        try {
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            List<String> names = new ArrayList<>();
            Matcher matcher = REMOTE_SECTION.matcher(content);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            return List.copyOf(names);
        } catch (IOException ignored) {
            return List.of();
        }
    }

    int countBranches(Path gitDir) {
        Set<String> branches = new HashSet<>();
        collectLooseBranches(gitDir, branches);
        collectPackedBranches(gitDir, branches);
        return branches.size();
    }

    private void collectLooseBranches(Path gitDir, Set<String> branches) {
        Path headsDir = gitDir.resolve("refs").resolve("heads");
        if (!Files.isDirectory(headsDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(headsDir)) {
            walk.filter(Files::isRegularFile)
                    .map(p -> headsDir.relativize(p).toString().replace("\\", "/"))
                    .forEach(branches::add);
        } catch (IOException ignored) {
        }
    }

    private void collectPackedBranches(Path gitDir, Set<String> branches) {
        Path packedRefsFile = gitDir.resolve("packed-refs");
        if (!Files.exists(packedRefsFile)) {
            return;
        }
        try (Stream<String> lines = Files.lines(packedRefsFile, StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.startsWith("#") && !line.startsWith("^"))
                    .filter(line -> line.contains(" refs/heads/"))
                    .map(line -> line.substring(line.indexOf(" refs/heads/") + " refs/heads/".length()).strip())
                    .forEach(branches::add);
        } catch (IOException ignored) {
        }
    }

    Optional<CommitInfo> readLastCommitInfo(Path gitDir) {
        Path logsHead = gitDir.resolve("logs").resolve("HEAD");
        if (!Files.exists(logsHead)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(logsHead, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i).strip();
                if (!line.isEmpty()) {
                    return parseReflogLine(line);
                }
            }
        } catch (IOException ignored) {
        }
        return Optional.empty();
    }

    private Optional<CommitInfo> parseReflogLine(String line) {
        Matcher shaMatcher = REFLOG_NEW_SHA.matcher(line);
        String shortSha = null;
        if (shaMatcher.find()) {
            String fullSha = shaMatcher.group(1);
            shortSha = fullSha.length() >= 8 ? fullSha.substring(0, 8) : fullSha;
        }

        Matcher tsMatcher = REFLOG_TIMESTAMP.matcher(line);
        if (!tsMatcher.find()) {
            return Optional.empty();
        }

        long epochSeconds = Long.parseLong(tsMatcher.group(1));
        LocalDate date = Instant.ofEpochSecond(epochSeconds)
                .atOffset(ZoneOffset.UTC)
                .toLocalDate();

        return Optional.of(new CommitInfo(shortSha != null ? shortSha : "", date));
    }

    @Nullable
    private String resolveHeadCommit(HeadInfo head, Optional<CommitInfo> lastCommit) {
        if (head.detached() && head.commitSha() != null) {
            return head.commitSha();
        }
        return lastCommit.map(CommitInfo::shortSha).filter(s -> !s.isEmpty()).orElse(null);
    }

    private GitActivityStatus computeActivityStatus(@Nullable Integer days) {
        if (days == null) {
            return GitActivityStatus.UNKNOWN;
        }
        if (days <= 30) return GitActivityStatus.ACTIVE;
        if (days <= 90) return GitActivityStatus.QUIET;
        if (days <= 180) return GitActivityStatus.STALE;
        return GitActivityStatus.DORMANT;
    }

    record HeadInfo(boolean detached, @Nullable String branchName, @Nullable String commitSha) {
        static HeadInfo onBranch(String branch) {
            return new HeadInfo(false, branch, null);
        }

        static HeadInfo detached(String sha) {
            return new HeadInfo(true, null, sha);
        }

        static HeadInfo unknown() {
            return new HeadInfo(false, null, null);
        }
    }

    record CommitInfo(String shortSha, LocalDate date) {}
}
