package com.projectpulse.detector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectpulse.model.DependencyFinding;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class DependencyParser {

    private static final String MAVEN = "Maven";
    private static final String NPM = "npm";
    private static final String PYTHON = "Python";

    private static final Set<String> MAVEN_TARGET_ARTIFACT_IDS = Set.of(
            "spring-boot-starter-web",
            "spring-boot-starter-data-jpa",
            "spring-boot-starter-security",
            "postgresql",
            "mysql-connector-j",
            "mysql-connector-java",
            "h2",
            "lombok"
    );

    private static final Set<String> NPM_TARGET_PACKAGES = Set.of(
            "react", "vite", "typescript", "express", "axios", "tailwindcss"
    );

    private static final Set<String> PYTHON_TARGET_PACKAGES = Set.of(
            "streamlit", "pandas", "numpy", "requests", "fastapi", "flask",
            "sqlalchemy", "psycopg", "psycopg2", "psycopg2-binary"
    );

    // Extracts the package name from the start of a requirements.txt line.
    // Group 1: raw name (e.g. "psycopg2", "SQLAlchemy", "psycopg2-binary").
    private static final Pattern PYTHON_NAME_PATTERN =
            Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]*)");

    // Matches an optional extras specifier "[...]" at the start of the remainder after the name.
    private static final Pattern PYTHON_EXTRAS_PATTERN =
            Pattern.compile("^\\s*\\[[^\\]]*\\]\\s*");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<DependencyFinding> parse(Path projectPath, List<String> detectedFiles) {
        List<DependencyFinding> findings = new ArrayList<>();
        if (detectedFiles.contains("pom.xml")) {
            findings.addAll(parseMaven(projectPath));
        }
        if (detectedFiles.contains("package.json")) {
            findings.addAll(parseNode(projectPath));
        }
        if (detectedFiles.contains("requirements.txt")) {
            findings.addAll(parsePython(projectPath));
        }
        return List.copyOf(findings);
    }

    List<DependencyFinding> parseMaven(Path projectPath) {
        Path pomFile = projectPath.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            return List.of();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile.toFile());
            doc.getDocumentElement().normalize();

            List<DependencyFinding> findings = new ArrayList<>();
            findings.addAll(parseMavenParent(doc));
            findings.addAll(parseMavenJavaVersion(doc));
            findings.addAll(parseMavenDependencies(doc));
            return List.copyOf(findings);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<DependencyFinding> parseMavenParent(Document doc) {
        NodeList parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() == 0) {
            return List.of();
        }
        Element parent = (Element) parentNodes.item(0);
        String artifactId = childText(parent, "artifactId");
        String version = childText(parent, "version");
        if (artifactId == null || version == null) {
            return List.of();
        }
        if ("spring-boot-starter-parent".equals(artifactId) || "spring-boot-dependencies".equals(artifactId)) {
            return List.of(new DependencyFinding(MAVEN, artifactId, version, "pom.xml", "parent", ""));
        }
        return List.of();
    }

    private List<DependencyFinding> parseMavenJavaVersion(Document doc) {
        NodeList propertiesNodes = doc.getElementsByTagName("properties");
        for (int i = 0; i < propertiesNodes.getLength(); i++) {
            Element properties = (Element) propertiesNodes.item(i);
            for (String tag : List.of("java.version", "maven.compiler.source", "maven.compiler.release")) {
                String value = childText(properties, tag);
                if (value != null) {
                    return List.of(new DependencyFinding(MAVEN, "java", value, "pom.xml", "property", ""));
                }
            }
        }
        return List.of();
    }

    private List<DependencyFinding> parseMavenDependencies(Document doc) {
        List<DependencyFinding> findings = new ArrayList<>();
        NodeList dependencyNodes = doc.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dep = (Element) dependencyNodes.item(i);
            String artifactId = childText(dep, "artifactId");
            if (artifactId == null || !MAVEN_TARGET_ARTIFACT_IDS.contains(artifactId)) {
                continue;
            }
            String version = childText(dep, "version");
            String scope = childText(dep, "scope");
            String notes = (version == null) ? "version managed by parent" : "";
            findings.add(new DependencyFinding(
                    MAVEN,
                    artifactId,
                    version != null ? version : "",
                    "pom.xml",
                    scope != null ? scope : "compile",
                    notes
            ));
        }
        return findings;
    }

    // Returns the text content of the first direct child element with the given tag name.
    private String childText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getParentNode() == parent) {
                return nodes.item(i).getTextContent().trim();
            }
        }
        return null;
    }

    List<DependencyFinding> parseNode(Path projectPath) {
        Path packageJson = projectPath.resolve("package.json");
        if (!Files.exists(packageJson)) {
            return List.of();
        }
        try {
            String content = Files.readString(packageJson, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(content);
            List<DependencyFinding> findings = new ArrayList<>();
            parseNpmSection(root, "dependencies", findings);
            parseNpmSection(root, "devDependencies", findings);
            return List.copyOf(findings);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void parseNpmSection(JsonNode root, String section, List<DependencyFinding> findings) {
        JsonNode node = root.path(section);
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            if (NPM_TARGET_PACKAGES.contains(name)) {
                findings.add(new DependencyFinding(NPM, name, entry.getValue().asText(""), "package.json", section, ""));
            }
        });
    }

    List<DependencyFinding> parsePython(Path projectPath) {
        Path requirementsFile = projectPath.resolve("requirements.txt");
        if (!Files.exists(requirementsFile)) {
            return List.of();
        }
        try {
            byte[] bytes = Files.readAllBytes(requirementsFile);
            String content = stripDecodedBom(new String(bytes, detectRequirementsCharset(bytes)));
            String[] lines = content.split("\\R", -1);
            List<DependencyFinding> findings = new ArrayList<>();
            for (String rawLine : lines) {
                // Strip inline comments and surrounding whitespace
                String line = stripComment(rawLine).strip();
                if (line.isEmpty() || line.startsWith("-") || line.startsWith(".")) {
                    continue;
                }
                Matcher nameMatcher = PYTHON_NAME_PATTERN.matcher(line);
                if (!nameMatcher.find()) {
                    continue;
                }
                String normalizedName = normalizePythonName(nameMatcher.group(1));
                if (!PYTHON_TARGET_PACKAGES.contains(normalizedName)) {
                    continue;
                }
                // Skip optional extras notation [extra1,extra2] then take the version specifier
                String remainder = line.substring(nameMatcher.end());
                Matcher extrasMatcher = PYTHON_EXTRAS_PATTERN.matcher(remainder);
                if (extrasMatcher.find()) {
                    remainder = remainder.substring(extrasMatcher.end());
                }
                String version = normalizePythonVersion(remainder.strip());
                String notes = version.isEmpty() ? "not pinned" : "";
                findings.add(new DependencyFinding(PYTHON, normalizedName, version, "requirements.txt", "main", notes));
            }
            return List.copyOf(findings);
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private Charset detectRequirementsCharset(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        return StandardCharsets.UTF_8;
    }

    private String stripDecodedBom(String content) {
        if (!content.isEmpty() && content.charAt(0) == 0xFEFF) {
            return content.substring(1);
        }
        return content;
    }

    private String stripComment(String line) {
        int hash = line.indexOf('#');
        int semi = line.indexOf(';');
        int commentStart = (hash < 0) ? semi : (semi < 0 ? hash : Math.min(hash, semi));
        return commentStart < 0 ? line : line.substring(0, commentStart);
    }

    private String normalizePythonVersion(String version) {
        if (version.startsWith("==")) {
            return version.substring(2).strip();
        }
        return version;
    }

    // PEP 508: package names are case-insensitive; [-._]+ sequences are equivalent.
    private String normalizePythonName(String name) {
        return name.toLowerCase().replaceAll("[-._]+", "-");
    }
}
