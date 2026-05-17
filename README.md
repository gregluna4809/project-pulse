

# ProjectPulse

**Engineering intelligence platform for analyzing software projects through dependency health, Git activity, CI hygiene, testing posture, repository risk detection, and overall engineering health scoring.**

ProjectPulse is a full-stack codebase analysis platform built to provide fast, read-only engineering intelligence across software repositories.

It scans selected workspaces and evaluates engineering quality signals including dependency freshness, Git activity, CI workflows, test maturity, `.gitignore` hygiene, and architectural risk indicators.

Designed as both a practical developer tool and a production-style portfolio project.

---

## Why I Built This

Engineering teams often lack quick visibility into repository health without manually inspecting every project.

ProjectPulse was built to answer questions like:

- Is this project actively maintained?
- Are dependencies outdated?
- Does CI exist?
- Are tests actually present?
- Is repository hygiene acceptable?
- Are risky engineering patterns visible immediately?

The goal was to build a practical engineering intelligence dashboard—not another CRUD tutorial.

---

## Features

### Workspace Discovery & Scoped Analysis
- Discover available workspaces under a root directory
- Select only projects you care about
- Avoid wasting compute on irrelevant repositories

### Engineering Health Scoring
Deterministic health scoring with tiered assessment:

- EXCELLENT
- GOOD
- FAIR
- AT_RISK
- CRITICAL

Scoring incorporates:

- strengths
- improvements
- risk findings
- dependency health
- Git activity
- CI posture
- test maturity
- repository hygiene

---

### Dependency Intelligence
Analyzes dependency manifests for:

**Java / Spring**
- Maven
- Spring Boot
- Java version
- managed dependencies

**Node / Frontend**
- npm / package.json
- React
- TypeScript
- Vite
- test frameworks

**Python**
- requirements.txt
- pytest ecosystem detection

Dependency health states:

- CURRENT
- UPGRADE_CANDIDATE
- LEGACY
- CRITICAL
- UNKNOWN
- MANAGED

---

### Git Intelligence
Detects:

- Git repository presence
- current branch
- detached HEAD
- remote configuration
- branch count
- commit recency
- repository activity posture

Activity states:

- ACTIVE
- QUIET
- STALE
- DORMANT

---

### CI Workflow Intelligence
Inspects GitHub Actions workflows:

- workflow count
- push triggers
- pull request validation
- manual dispatch support
- build jobs
- test jobs
- deploy jobs
- detected toolchains

Risk detection:

- deployment workflows without manual gating

---

### Test Intelligence
Detects actual testing maturity instead of simple folder presence.

Supports:

**Java**
- JUnit
- SpringBootTest
- Mockito
- integration test detection

**Node**
- Jest
- Vitest
- Testing Library
- Cypress
- Playwright

**Python**
- pytest
- unittest

**C++**
- GoogleTest
- Catch2
- doctest

Signals:

- test file count
- integration test count
- framework detection
- Node test script detection

---

### Repository Hygiene Intelligence
Analyzes `.gitignore` coverage:

Detects missing exclusions for:

- build artifacts
- dependency directories
- virtual environments
- environment secrets
- common IDE junk

Risk examples:

- `.env` not excluded
- `node_modules` not excluded
- compiled binaries committed

---

### Unknown / Ad Hoc Project Support
Even loosely structured projects are analyzed.

Example:

- ad hoc C++ folders
- source-only utility projects
- generic workspaces without manifests

Selected workspaces never silently disappear.

---

### Reporting
Export analysis results as:

- JSON
- Markdown

---

## Architecture

### Backend
**Spring Boot / Java 21**

Responsibilities:

- workspace discovery
- project analysis
- scoring engine
- dependency parsing
- Git inspection
- CI workflow inspection
- test intelligence
- report generation

---

### Frontend
**React + TypeScript + Vite + Tailwind**

Responsibilities:

- workspace selection
- filtering
- dashboard presentation
- report export
- engineering intelligence visualization

---

## Tech Stack

### Backend
- Java 21
- Spring Boot
- Maven

### Frontend
- React
- TypeScript
- Vite
- Tailwind CSS
- Axios

---

## Screenshots

_Add dashboard screenshots here._

Suggested:

- workspace discovery
- filtered dashboard
- dependency intelligence
- CI analysis
- markdown report export

---

## Quick Start

### Backend
```bash
cd backend
mvn spring-boot:run
````

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:5173
```

---

## Current Capabilities

✅ Dependency intelligence
✅ Git intelligence
✅ CI workflow intelligence
✅ Test intelligence
✅ `.gitignore` hygiene analysis
✅ Health scoring
✅ Scoped workspace analysis
✅ JSON / Markdown reporting
✅ Unknown workspace fallback support

---

## Roadmap

Planned enhancements:

* historical scan comparisons
* architecture smell detection
* Docker/container intelligence
* dependency vulnerability intelligence
* richer repository heuristics
* AI-assisted engineering recommendations

See:

```text
ROADMAP.md
```

---

## Design Principles

ProjectPulse intentionally follows strict rules:

* **Read-only analysis**
* **No modification of scanned repositories**
* **No build/test execution against scanned projects**
* **Deterministic scoring**
* **Fast developer feedback**

---

## Author

**Gregory Luna**

Built as a production-style engineering portfolio project.

```



