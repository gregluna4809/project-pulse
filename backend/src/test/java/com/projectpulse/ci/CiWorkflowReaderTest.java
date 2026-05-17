package com.projectpulse.ci;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CiWorkflowReaderTest {

    @TempDir
    Path tempDir;

    private final CiWorkflowReader reader = new CiWorkflowReader();

    @Test
    void returnsNoCiWhenWorkflowsDirAbsent() {
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasCi()).isFalse();
        assertThat(result.workflowFiles()).isEmpty();
        assertThat(result.workflowCount()).isZero();
    }

    @Test
    void returnsNoCiWhenWorkflowsDirExistsButIsEmpty() throws IOException {
        Files.createDirectories(tempDir.resolve(".github/workflows"));
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasCi()).isFalse();
    }

    @Test
    void returnsNoCiWhenWorkflowsDirContainsOnlyNonYamlFiles() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve(".github/workflows"));
        Files.writeString(dir.resolve("notes.txt"), "not a workflow");
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasCi()).isFalse();
    }

    @Test
    void detectsPushTriggerBlockForm() throws IOException {
        writeWorkflow("ci.yml", """
                on:
                  push:
                    branches: [main]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo hello
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasCi()).isTrue();
        assertThat(result.hasPushTrigger()).isTrue();
        assertThat(result.hasPullRequestTrigger()).isFalse();
    }

    @Test
    void detectsPullRequestTriggerBlockForm() throws IOException {
        writeWorkflow("ci.yml", """
                on:
                  pull_request:
                    branches: [main]
                jobs:
                  check:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasPullRequestTrigger()).isTrue();
        assertThat(result.hasPushTrigger()).isFalse();
    }

    @Test
    void detectsWorkflowDispatchTriggerBlockForm() throws IOException {
        writeWorkflow("ci.yml", """
                on:
                  workflow_dispatch:
                jobs:
                  release:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo release
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasManualTrigger()).isTrue();
    }

    @Test
    void detectsTriggersInlineArrayForm() throws IOException {
        writeWorkflow("ci.yml", """
                on: [push, pull_request, workflow_dispatch]
                jobs:
                  ci:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo ok
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasPushTrigger()).isTrue();
        assertThat(result.hasPullRequestTrigger()).isTrue();
        assertThat(result.hasManualTrigger()).isTrue();
    }

    @Test
    void detectsBuildJobByJobId() throws IOException {
        writeWorkflow("ci.yml", """
                on: [push]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo building
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasBuildJob()).isTrue();
        assertThat(result.hasTestJob()).isFalse();
    }

    @Test
    void detectsTestJobByJobId() throws IOException {
        writeWorkflow("ci.yml", """
                on: [push]
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo testing
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasTestJob()).isTrue();
        assertThat(result.hasBuildJob()).isFalse();
    }

    @Test
    void detectsDeployJobByJobId() throws IOException {
        writeWorkflow("deploy.yml", """
                on: [push]
                jobs:
                  deploy:
                    runs-on: ubuntu-latest
                    steps:
                      - run: echo deploying
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasDeployJob()).isTrue();
    }

    @Test
    void detectsBuildJobByRunCommand() throws IOException {
        writeWorkflow("ci.yml", """
                on: [push]
                jobs:
                  ci:
                    runs-on: ubuntu-latest
                    steps:
                      - run: mvn clean install
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasBuildJob()).isTrue();
    }

    @Test
    void detectsTestJobByRunCommand() throws IOException {
        writeWorkflow("ci.yml", """
                on: [push]
                jobs:
                  ci:
                    runs-on: ubuntu-latest
                    steps:
                      - run: npm test
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasTestJob()).isTrue();
    }

    @Test
    void detectsDeployJobByRunCommand() throws IOException {
        writeWorkflow("ci.yml", """
                on: [push]
                jobs:
                  release:
                    runs-on: ubuntu-latest
                    steps:
                      - run: docker push myimage:latest
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasDeployJob()).isTrue();
    }

    @Test
    void detectsToolchainsFromSetupActions() throws IOException {
        writeWorkflow("ci.yml", """
                on: [push]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/setup-java@v3
                      - uses: actions/setup-node@v3
                      - run: npm run build
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.detectedToolchains()).contains("actions/setup-java", "actions/setup-node", "npm");
    }

    @Test
    void aggregatesDataAcrossMultipleWorkflowFiles() throws IOException {
        writeWorkflow("build.yml", """
                on:
                  push:
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - run: mvn clean install
                """);
        writeWorkflow("deploy.yml", """
                on:
                  workflow_dispatch:
                jobs:
                  deploy:
                    runs-on: ubuntu-latest
                    steps:
                      - run: docker push myapp:latest
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasCi()).isTrue();
        assertThat(result.workflowCount()).isEqualTo(2);
        assertThat(result.workflowFiles()).containsExactlyInAnyOrder("build.yml", "deploy.yml");
        assertThat(result.hasPushTrigger()).isTrue();
        assertThat(result.hasManualTrigger()).isTrue();
        assertThat(result.hasBuildJob()).isTrue();
        assertThat(result.hasDeployJob()).isTrue();
    }

    @Test
    void deployWithoutManualTriggerSetsCorrectFlags() throws IOException {
        writeWorkflow("deploy.yml", """
                on:
                  push:
                jobs:
                  deploy:
                    runs-on: ubuntu-latest
                    steps:
                      - run: kubectl apply -f k8s/
                """);
        CiAnalysis result = reader.read(tempDir);
        assertThat(result.hasDeployJob()).isTrue();
        assertThat(result.hasManualTrigger()).isFalse();
    }

    private void writeWorkflow(String fileName, String content) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve(".github/workflows"));
        Files.writeString(dir.resolve(fileName), content);
    }
}
