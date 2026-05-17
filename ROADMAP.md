# ROADMAP.md

## Vision

ProjectPulse becomes a local-first engineering intelligence platform for analyzing software projects.

---

# Phase 1 — MVP Foundation

Goal:
deterministic project scanning and analysis

Deliverables:
- folder scanning
- project discovery
- project classification
- tech stack identification
- key file detection
- rules engine
- JSON API output

Examples:
- detect Spring Boot
- detect React
- detect Node
- detect Python
- detect C++
- detect Docker
- detect Git repo presence

Rules examples:
- missing README
- missing tests
- missing .gitignore
- missing env template
- missing CI config

Success criteria:
working backend API returning project analysis

---

# Phase 2 — Engineering Analysis

Goal:
deeper deterministic analysis

Deliverables:
- dependency auditing
- version health analysis
- security heuristics
- Git repository health
- documentation scoring
- configuration quality checks

Examples:
- outdated Spring Boot
- old npm packages
- secrets in config
- missing test coverage signals

Success criteria:
meaningful engineering feedback

---

# Phase 3 — AI Augmentation

Goal:
local AI explanation layer

Deliverables:
- Ollama integration
- AI recommendation summaries
- narrative engineering feedback

Rules:
AI does not invent findings.
AI explains deterministic findings.

Success criteria:
credible engineering recommendations

---

# Phase 4 — Frontend Platform

Goal:
interactive user experience

Deliverables:
- dashboard UI
- project cards
- issue drilldowns
- comparisons
- filtering
- scan history

Success criteria:
usable local application

---

# Phase 5 — Advanced Reporting

Goal:
professional reporting platform

Deliverables:
- PDF exports
- architecture reports
- portfolio readiness scoring
- modernization recommendations
- technical debt reporting

Success criteria:
resume-worthy engineering platform