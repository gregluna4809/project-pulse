package com.projectpulse.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectpulse.model.DependencyFinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyParserTest {

    @TempDir
    Path tempDir;

    private final DependencyParser parser = new DependencyParser();

    @Test
    void parseMavenExtractsSpringBootParentVersionJavaVersionAndKeyDependencies() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("spring-api"));
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.3.5</version>
                        <relativePath/>
                    </parent>
                    <properties>
                        <java.version>21</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <scope>provided</scope>
                        </dependency>
                        <dependency>
                            <groupId>com.h2database</groupId>
                            <artifactId>h2</artifactId>
                            <version>2.2.224</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);

        List<DependencyFinding> findings = parser.parseMaven(project);

        assertThat(findings).extracting(DependencyFinding::name)
                .contains("spring-boot-starter-parent", "java", "spring-boot-starter-web",
                        "spring-boot-starter-data-jpa", "postgresql", "lombok", "h2");

        DependencyFinding parent = findByName(findings, "spring-boot-starter-parent");
        assertThat(parent.version()).isEqualTo("3.3.5");
        assertThat(parent.scope()).isEqualTo("parent");
        assertThat(parent.ecosystem()).isEqualTo("Maven");
        assertThat(parent.sourceFile()).isEqualTo("pom.xml");
        assertThat(parent.notes()).isEmpty();

        DependencyFinding java = findByName(findings, "java");
        assertThat(java.version()).isEqualTo("21");
        assertThat(java.scope()).isEqualTo("property");

        DependencyFinding web = findByName(findings, "spring-boot-starter-web");
        assertThat(web.version()).isEmpty();
        assertThat(web.scope()).isEqualTo("compile");
        assertThat(web.notes()).isEqualTo("version managed by parent");

        DependencyFinding pg = findByName(findings, "postgresql");
        assertThat(pg.scope()).isEqualTo("runtime");
        assertThat(pg.notes()).isEqualTo("version managed by parent");

        DependencyFinding h2 = findByName(findings, "h2");
        assertThat(h2.version()).isEqualTo("2.2.224");
        assertThat(h2.scope()).isEqualTo("test");
        assertThat(h2.notes()).isEmpty();
    }

    @Test
    void parseMavenIgnoresNonTargetDependencies() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("spring-api"));
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>custom-library</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        List<DependencyFinding> findings = parser.parseMaven(project);

        assertThat(findings).extracting(DependencyFinding::name)
                .contains("spring-boot-starter-web")
                .doesNotContain("custom-library");
    }

    @Test
    void parseMavenReturnEmptyListForMalformedXml() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("broken-api"));
        Files.writeString(project.resolve("pom.xml"), "this is not xml at all");

        List<DependencyFinding> findings = parser.parseMaven(project);

        assertThat(findings).isEmpty();
    }

    @Test
    void parseNodeExtractsKeyDependenciesFromDependenciesAndDevDependencies() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("react-app"));
        Files.writeString(project.resolve("package.json"), """
                {
                    "name": "react-app",
                    "dependencies": {
                        "react": "^18.2.0",
                        "axios": "^1.6.0",
                        "lodash": "^4.17.21"
                    },
                    "devDependencies": {
                        "typescript": "^5.3.0",
                        "vite": "^5.0.0",
                        "tailwindcss": "^3.4.0",
                        "eslint": "^8.0.0"
                    }
                }
                """);

        List<DependencyFinding> findings = parser.parseNode(project);

        assertThat(findings).extracting(DependencyFinding::name)
                .contains("react", "axios", "typescript", "vite", "tailwindcss")
                .doesNotContain("lodash", "eslint");

        DependencyFinding react = findByName(findings, "react");
        assertThat(react.version()).isEqualTo("^18.2.0");
        assertThat(react.scope()).isEqualTo("dependencies");
        assertThat(react.ecosystem()).isEqualTo("npm");
        assertThat(react.sourceFile()).isEqualTo("package.json");

        DependencyFinding ts = findByName(findings, "typescript");
        assertThat(ts.scope()).isEqualTo("devDependencies");

        DependencyFinding tw = findByName(findings, "tailwindcss");
        assertThat(tw.scope()).isEqualTo("devDependencies");
    }

    @Test
    void parseNodeReturnsEmptyListForMissingPackageJson() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("empty-project"));

        List<DependencyFinding> findings = parser.parseNode(project);

        assertThat(findings).isEmpty();
    }

    @Test
    void parsePythonExtractsPinnedRangedAndUnpinnedDependencies() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("flask-service"));
        Files.writeString(project.resolve("requirements.txt"), """
                # Web framework
                flask==2.3.0
                requests>=2.28.0,<3.0.0
                numpy
                pandas==2.1.0
                fastapi==0.100.0
                sqlalchemy>=2.0.0
                some-other-package==1.0.0
                psycopg2==2.9.9
                """);

        List<DependencyFinding> findings = parser.parsePython(project);

        assertThat(findings).extracting(DependencyFinding::name)
                .contains("flask", "requests", "numpy", "pandas", "fastapi", "sqlalchemy", "psycopg2")
                .doesNotContain("some-other-package");

        DependencyFinding flask = findByName(findings, "flask");
        assertThat(flask.version()).isEqualTo("2.3.0");
        assertThat(flask.scope()).isEqualTo("main");
        assertThat(flask.ecosystem()).isEqualTo("Python");
        assertThat(flask.sourceFile()).isEqualTo("requirements.txt");
        assertThat(flask.notes()).isEmpty();

        DependencyFinding requests = findByName(findings, "requests");
        assertThat(requests.version()).isEqualTo(">=2.28.0,<3.0.0");
        assertThat(requests.notes()).isEmpty();

        DependencyFinding numpy = findByName(findings, "numpy");
        assertThat(numpy.version()).isEmpty();
        assertThat(numpy.notes()).isEqualTo("not pinned");

        DependencyFinding psycopg2 = findByName(findings, "psycopg2");
        assertThat(psycopg2.version()).isEqualTo("2.9.9");
    }

    @Test
    void parsePythonSkipsCommentsAndBlankLines() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("data-service"));
        Files.writeString(project.resolve("requirements.txt"), """
                # Data stack
                pandas==2.1.0
                # end of file
                """);

        List<DependencyFinding> findings = parser.parsePython(project);

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().name()).isEqualTo("pandas");
    }

    @Test
    void parsePythonHandlesPackageExtrasNotation() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("service"));
        Files.writeString(project.resolve("requirements.txt"), "psycopg2[binary]==2.9.9\n");

        List<DependencyFinding> findings = parser.parsePython(project);

        assertThat(findings).hasSize(1);
        DependencyFinding finding = findings.getFirst();
        assertThat(finding.name()).isEqualTo("psycopg2");
        assertThat(finding.version()).isEqualTo("2.9.9");
    }

    @Test
    void parsePythonExtractsTargetPackagesWithMixedCaseAndBinaryVariants() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("nyc-analysis"));
        Files.writeString(project.resolve("requirements.txt"), """
                altair==6.0.0
                numpy==2.4.4
                pandas==3.0.2
                plotly==6.7.0
                psycopg2-binary==2.9.11
                requests==2.33.1
                SQLAlchemy==2.0.49
                streamlit==1.56.0
                """);

        List<DependencyFinding> findings = parser.parsePython(project);

        assertThat(findings).extracting(DependencyFinding::name)
                .containsExactlyInAnyOrder("numpy", "pandas", "psycopg2-binary", "requests", "sqlalchemy", "streamlit")
                .doesNotContain("altair", "plotly");

        DependencyFinding numpy = findByName(findings, "numpy");
        assertThat(numpy.version()).isEqualTo("2.4.4");
        assertThat(numpy.notes()).isEmpty();

        DependencyFinding sqlalchemy = findByName(findings, "sqlalchemy");
        assertThat(sqlalchemy.version()).isEqualTo("2.0.49");
        assertThat(sqlalchemy.ecosystem()).isEqualTo("Python");

        DependencyFinding psycopg2Binary = findByName(findings, "psycopg2-binary");
        assertThat(psycopg2Binary.version()).isEqualTo("2.9.11");
        assertThat(psycopg2Binary.scope()).isEqualTo("main");

        DependencyFinding streamlit = findByName(findings, "streamlit");
        assertThat(streamlit.version()).isEqualTo("1.56.0");
    }

    @Test
    void parsePythonHandlesUtf8BomAtFileStart() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("bom-project"));
        // Simulate a file saved with a UTF-8 BOM (e.g. by Windows Notepad)
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "numpy==2.4.4\npandas==3.0.2\nstreamlit==1.56.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] combined = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(body, 0, combined, bom.length, body.length);
        java.nio.file.Files.write(project.resolve("requirements.txt"), combined);

        List<DependencyFinding> findings = parser.parsePython(project);

        assertThat(findings).extracting(DependencyFinding::name)
                .containsExactlyInAnyOrder("numpy", "pandas", "streamlit");

        DependencyFinding numpy = findByName(findings, "numpy");
        assertThat(numpy.version()).isEqualTo("2.4.4");
    }

    @Test
    void parsePythonHandlesUtf16LeBomRequirementsTxt() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("utf16le-requirements"));
        String requirements = """
                altair==6.0.0
                numpy==2.4.4
                pandas==3.0.2
                plotly==6.7.0
                psycopg2-binary==2.9.11
                requests==2.33.1
                SQLAlchemy==2.0.49
                streamlit==1.56.0
                """;
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] body = requirements.getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        byte[] combined = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, combined, 0, bom.length);
        System.arraycopy(body, 0, combined, bom.length, body.length);
        Files.write(project.resolve("requirements.txt"), combined);

        List<DependencyFinding> findings = parser.parsePython(project);

        assertThat(findings).extracting(DependencyFinding::name)
                .containsExactlyInAnyOrder("numpy", "pandas", "psycopg2-binary", "requests", "sqlalchemy", "streamlit")
                .doesNotContain("altair", "plotly");

        assertThat(findByName(findings, "numpy").version()).isEqualTo("2.4.4");
        assertThat(findByName(findings, "pandas").version()).isEqualTo("3.0.2");
        assertThat(findByName(findings, "requests").version()).isEqualTo("2.33.1");
        assertThat(findByName(findings, "sqlalchemy").version()).isEqualTo("2.0.49");
        assertThat(findByName(findings, "psycopg2-binary").version()).isEqualTo("2.9.11");
        assertThat(findByName(findings, "streamlit").version()).isEqualTo("1.56.0");
    }

    @Test
    void parsePythonHandlesAllVersionSpecifierOperators() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("operators"));
        Files.writeString(project.resolve("requirements.txt"), """
                flask==2.3.0
                requests>=2.28.0
                pandas<=3.0.0
                numpy~=2.0.0
                sqlalchemy>2.0
                fastapi<1.0
                streamlit
                """);

        List<DependencyFinding> findings = parser.parsePython(project);

        assertThat(findings).extracting(DependencyFinding::name)
                .containsExactlyInAnyOrder("flask", "requests", "pandas", "numpy", "sqlalchemy", "fastapi", "streamlit");

        assertThat(findByName(findings, "flask").version()).isEqualTo("2.3.0");
        assertThat(findByName(findings, "requests").version()).isEqualTo(">=2.28.0");
        assertThat(findByName(findings, "pandas").version()).isEqualTo("<=3.0.0");
        assertThat(findByName(findings, "numpy").version()).isEqualTo("~=2.0.0");
        assertThat(findByName(findings, "sqlalchemy").version()).isEqualTo(">2.0");
        assertThat(findByName(findings, "fastapi").version()).isEqualTo("<1.0");
        assertThat(findByName(findings, "streamlit").version()).isEmpty();
        assertThat(findByName(findings, "streamlit").notes()).isEqualTo("not pinned");
    }

    private DependencyFinding findByName(List<DependencyFinding> findings, String name) {
        return findings.stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No finding with name '" + name + "'. Found: "
                                + findings.stream().map(DependencyFinding::name).toList()));
    }
}
