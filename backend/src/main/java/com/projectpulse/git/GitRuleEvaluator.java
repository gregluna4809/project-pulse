package com.projectpulse.git;

import com.projectpulse.model.RuleFinding;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GitRuleEvaluator {

    public List<RuleFinding> strengths(GitAnalysis git) {
        if (!git.gitRepository()) {
            return List.of();
        }
        List<RuleFinding> findings = new ArrayList<>();

        findings.add(new RuleFinding(
                "git-repository-detected",
                "Git repository detected.",
                ".git"
        ));

        if (git.hasRemote()) {
            String remotes = String.join(", ", git.remoteNames());
            findings.add(new RuleFinding(
                    "git-remote-configured",
                    "Remote repository configured: " + remotes + ".",
                    ".git/config"
            ));
        }

        if (git.activityStatus() == GitActivityStatus.ACTIVE) {
            int days = git.daysSinceLastCommit() != null ? git.daysSinceLastCommit() : 0;
            String when = days == 0 ? "today" : days + " day" + (days == 1 ? "" : "s") + " ago";
            findings.add(new RuleFinding(
                    "git-recent-activity",
                    "Active commit history detected (last commit " + when + ").",
                    ".git/logs/HEAD"
            ));
        }

        return List.copyOf(findings);
    }

    public List<RuleFinding> improvements(GitAnalysis git) {
        if (!git.gitRepository()) {
            return List.of();
        }
        List<RuleFinding> findings = new ArrayList<>();

        if (!git.hasRemote()) {
            findings.add(new RuleFinding(
                    "git-no-remote",
                    "No remote repository configured.",
                    ".git/config"
            ));
        }

        if (git.activityStatus() == GitActivityStatus.STALE) {
            int days = git.daysSinceLastCommit() != null ? git.daysSinceLastCommit() : 0;
            findings.add(new RuleFinding(
                    "git-stale-repository",
                    "Repository stale — no commits detected in " + days + " days.",
                    ".git/logs/HEAD"
            ));
        }

        return List.copyOf(findings);
    }

    public List<RuleFinding> riskFlags(GitAnalysis git) {
        if (!git.gitRepository()) {
            return List.of();
        }
        List<RuleFinding> findings = new ArrayList<>();

        if (git.detachedHead()) {
            findings.add(new RuleFinding(
                    "git-detached-head",
                    "Repository is in detached HEAD state — not on a branch.",
                    ".git/HEAD"
            ));
        }

        if (git.activityStatus() == GitActivityStatus.DORMANT) {
            int days = git.daysSinceLastCommit() != null ? git.daysSinceLastCommit() : 0;
            findings.add(new RuleFinding(
                    "git-dormant-repository",
                    "Repository dormant — no commits detected in " + days + " days.",
                    ".git/logs/HEAD"
            ));
        }

        return List.copyOf(findings);
    }
}
