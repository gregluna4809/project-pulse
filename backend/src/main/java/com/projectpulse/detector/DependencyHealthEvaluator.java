package com.projectpulse.detector;

import com.projectpulse.model.DependencyFinding;
import com.projectpulse.model.DependencyHealthAssessment;
import com.projectpulse.model.HealthStatus;
import com.projectpulse.model.Severity;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DependencyHealthEvaluator {

    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?");

    public List<DependencyHealthAssessment> evaluate(List<DependencyFinding> findings) {
        return findings.stream()
                .map(this::evaluate)
                .toList();
    }

    private static final String NOTES_VERSION_MANAGED = "version managed by parent";

    public DependencyHealthAssessment evaluate(DependencyFinding finding) {
        if (finding.notes() != null && finding.notes().contains(NOTES_VERSION_MANAGED)) {
            return managed(finding);
        }

        String dependencyName = normalizeDependencyName(finding.name());
        Optional<ParsedVersion> parsedVersion = parseVersion(finding.version());

        if (parsedVersion.isEmpty()) {
            return unknown(finding, "No concrete numeric version was detected.");
        }

        return switch (dependencyName) {
            case "java" -> assessJava(finding, parsedVersion.get());
            case "spring-boot" -> assessSpringBoot(finding, parsedVersion.get());
            case "react" -> assessByMajor(finding, parsedVersion.get(), "19.x", List.of(
                    rule(19, HealthStatus.CURRENT),
                    rule(18, HealthStatus.CURRENT),
                    rule(17, HealthStatus.ACCEPTABLE),
                    rule(16, HealthStatus.LEGACY)
            ));
            case "typescript" -> assessByMajor(finding, parsedVersion.get(), "5.x", List.of(
                    rule(5, HealthStatus.CURRENT),
                    rule(4, HealthStatus.ACCEPTABLE),
                    rule(3, HealthStatus.LEGACY)
            ));
            case "vite" -> assessByMajor(finding, parsedVersion.get(), "7.x", List.of(
                    rule(7, HealthStatus.CURRENT),
                    rule(6, HealthStatus.ACCEPTABLE),
                    rule(5, HealthStatus.UPGRADE_CANDIDATE),
                    rule(4, HealthStatus.LEGACY)
            ));
            case "streamlit" -> assessStreamlit(finding, parsedVersion.get());
            case "fastapi" -> assessFastApi(finding, parsedVersion.get());
            case "pandas" -> assessByMajor(finding, parsedVersion.get(), "3.x", List.of(
                    rule(3, HealthStatus.CURRENT),
                    rule(2, HealthStatus.ACCEPTABLE),
                    rule(1, HealthStatus.LEGACY)
            ));
            case "sqlalchemy" -> assessByMajor(finding, parsedVersion.get(), "2.x", List.of(
                    rule(2, HealthStatus.CURRENT),
                    rule(1, HealthStatus.LEGACY)
            ));
            case "express" -> assessExpress(finding, parsedVersion.get());
            default -> unknown(finding, "No curated local version intelligence available.");
        };
    }

    Optional<ParsedVersion> parseVersion(String rawVersion) {
        if (rawVersion == null || rawVersion.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = VERSION_NUMBER_PATTERN.matcher(rawVersion);
        if (!matcher.find()) {
            return Optional.empty();
        }

        int major = Integer.parseInt(matcher.group(1));
        Integer minor = matcher.group(2) == null ? null : Integer.parseInt(matcher.group(2));
        return Optional.of(new ParsedVersion(major, minor));
    }

    private DependencyHealthAssessment assessJava(DependencyFinding finding, ParsedVersion version) {
        HealthStatus status = switch (version.major()) {
            case 21 -> HealthStatus.CURRENT;
            case 17 -> HealthStatus.ACCEPTABLE;
            case 11 -> HealthStatus.LEGACY;
            case 8 -> HealthStatus.CRITICAL;
            default -> HealthStatus.UNKNOWN;
        };
        return assessment(finding, "21", status);
    }

    private DependencyHealthAssessment assessSpringBoot(DependencyFinding finding, ParsedVersion version) {
        if (version.major() == 3 && version.minor() != null) {
            HealthStatus status = switch (version.minor()) {
                case 5, 4 -> HealthStatus.CURRENT;
                case 3 -> HealthStatus.ACCEPTABLE;
                case 2 -> HealthStatus.UPGRADE_CANDIDATE;
                default -> HealthStatus.UNKNOWN;
            };
            return assessment(finding, "3.5.x", status);
        }
        if (version.major() == 2) {
            return assessment(finding, "3.5.x", HealthStatus.LEGACY);
        }
        if (version.major() == 1) {
            return assessment(finding, "3.5.x", HealthStatus.CRITICAL);
        }
        return unknown(finding, "Version is outside the curated Spring Boot catalog.");
    }

    private DependencyHealthAssessment assessStreamlit(DependencyFinding finding, ParsedVersion version) {
        if (version.major() == 1 && version.minor() != null) {
            if (version.minor() >= 50 && version.minor() <= 59) {
                return assessment(finding, "1.5x", HealthStatus.CURRENT);
            }
            if (version.minor() >= 40 && version.minor() <= 49) {
                return assessment(finding, "1.5x", HealthStatus.ACCEPTABLE);
            }
            return assessment(finding, "1.5x", HealthStatus.UPGRADE_CANDIDATE);
        }
        return unknown(finding, "Version is outside the curated Streamlit catalog.");
    }

    private DependencyHealthAssessment assessFastApi(DependencyFinding finding, ParsedVersion version) {
        if (version.major() == 0 && version.minor() != null) {
            if (version.minor() >= 110 && version.minor() <= 119) {
                return assessment(finding, "0.11x", HealthStatus.CURRENT);
            }
            if (version.minor() >= 100 && version.minor() <= 109) {
                return assessment(finding, "0.11x", HealthStatus.ACCEPTABLE);
            }
            return assessment(finding, "0.11x", HealthStatus.UPGRADE_CANDIDATE);
        }
        return unknown(finding, "Version is outside the curated FastAPI catalog.");
    }

    private DependencyHealthAssessment assessExpress(DependencyFinding finding, ParsedVersion version) {
        if (version.major() == 5) {
            return assessment(finding, "5.x", HealthStatus.CURRENT);
        }
        if (version.major() == 4) {
            return assessment(finding, "5.x", HealthStatus.ACCEPTABLE);
        }
        return assessment(finding, "5.x", HealthStatus.LEGACY);
    }

    private DependencyHealthAssessment assessByMajor(
            DependencyFinding finding,
            ParsedVersion version,
            String recommendedVersion,
            List<MajorRule> rules
    ) {
        return rules.stream()
                .filter(rule -> rule.major() == version.major())
                .findFirst()
                .map(rule -> assessment(finding, recommendedVersion, rule.status()))
                .orElseGet(() -> unknown(finding, "Version is outside the curated catalog."));
    }

    private DependencyHealthAssessment assessment(
            DependencyFinding finding,
            String recommendedVersion,
            HealthStatus status
    ) {
        if (status == HealthStatus.UNKNOWN) {
            return unknown(finding, "Version is outside the curated catalog.");
        }

        return new DependencyHealthAssessment(
                finding.name(),
                finding.version(),
                recommendedVersion,
                status,
                severityFor(status),
                notesFor(status)
        );
    }

    private DependencyHealthAssessment unknown(DependencyFinding finding, String notes) {
        return new DependencyHealthAssessment(
                finding.name(),
                finding.version(),
                "",
                HealthStatus.UNKNOWN,
                Severity.INFO,
                notes
        );
    }

    private DependencyHealthAssessment managed(DependencyFinding finding) {
        return new DependencyHealthAssessment(
                finding.name(),
                finding.version(),
                "",
                HealthStatus.MANAGED,
                Severity.INFO,
                "Version managed by parent POM or BOM."
        );
    }

    private String normalizeDependencyName(String dependencyName) {
        String normalized = dependencyName.toLowerCase(Locale.ROOT);
        if (normalized.equals("spring-boot-starter-parent") || normalized.equals("spring-boot-dependencies")) {
            return "spring-boot";
        }
        return normalized;
    }

    private Severity severityFor(HealthStatus status) {
        return switch (status) {
            case CURRENT -> Severity.INFO;
            case ACCEPTABLE -> Severity.LOW;
            case UPGRADE_CANDIDATE -> Severity.MEDIUM;
            case LEGACY -> Severity.HIGH;
            case CRITICAL -> Severity.CRITICAL;
            case UNKNOWN -> Severity.INFO;
            case MANAGED -> Severity.INFO;
        };
    }

    private String notesFor(HealthStatus status) {
        return switch (status) {
            case CURRENT -> "Version is in the current curated range.";
            case ACCEPTABLE -> "Version is acceptable but not the newest curated range.";
            case UPGRADE_CANDIDATE -> "Version is a deterministic upgrade candidate.";
            case LEGACY -> "Version is legacy according to the curated local catalog.";
            case CRITICAL -> "Version is critically outdated according to the curated local catalog.";
            case UNKNOWN -> "No curated local version intelligence available.";
            case MANAGED -> "Version managed by parent POM or BOM.";
        };
    }

    private MajorRule rule(int major, HealthStatus status) {
        return new MajorRule(major, status);
    }

    record ParsedVersion(int major, Integer minor) {}

    private record MajorRule(int major, HealthStatus status) {}
}
