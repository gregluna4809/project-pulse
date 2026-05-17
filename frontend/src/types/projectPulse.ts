export interface CiAnalysis {
  hasCi: boolean;
  workflowFiles: string[];
  workflowCount: number;
  hasPushTrigger: boolean;
  hasPullRequestTrigger: boolean;
  hasManualTrigger: boolean;
  hasBuildJob: boolean;
  hasTestJob: boolean;
  hasDeployJob: boolean;
  detectedToolchains: string[];
  notes: string | null;
}

export interface TestAnalysis {
  hasTests: boolean;
  testFileCount: number;
  integrationTestFileCount: number;
  detectedFrameworks: string[];
  hasTestScript: boolean;
  testDirectories: string[];
  notes: string | null;
}

export interface GitignoreAnalysis {
  hasGitignore: boolean;
  ignoredPatterns: string[];
  missingExpectedPatterns: string[];
  hygieneScore: number;
  notes: string | null;
}

export type GitActivityStatus = 'ACTIVE' | 'QUIET' | 'STALE' | 'DORMANT' | 'UNKNOWN' | 'NO_GIT';

export interface GitAnalysis {
  gitRepository: boolean;
  currentBranch: string | null;
  headCommit: string | null;
  lastCommitDate: string | null;
  daysSinceLastCommit: number | null;
  branchCount: number;
  hasRemote: boolean;
  remoteNames: string[];
  detachedHead: boolean;
  activityStatus: GitActivityStatus;
  notes: string | null;
}

export type ProjectType =
  | 'MAVEN'
  | 'SPRING_BOOT'
  | 'NODE'
  | 'REACT'
  | 'VITE'
  | 'PYTHON'
  | 'CPP'
  | 'CMAKE'
  | 'UNKNOWN'
  | 'GENERIC'
  | 'AD_HOC'
  | 'DOCKERIZED'
  | 'DOCKER_COMPOSE'
  | 'GITHUB_ACTIONS'
  | 'GIT_REPOSITORY';

export type HealthStatus =
  | 'CURRENT'
  | 'ACCEPTABLE'
  | 'UPGRADE_CANDIDATE'
  | 'LEGACY'
  | 'CRITICAL'
  | 'UNKNOWN'
  | 'MANAGED';

export type HealthTier = 'EXCELLENT' | 'GOOD' | 'FAIR' | 'AT_RISK' | 'CRITICAL';

export type Severity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface ProjectHealthAssessment {
  score: number;
  tier: HealthTier;
  summary: string;
}

export interface RuleFinding {
  ruleId: string;
  message: string;
  evidence: string;
}

export interface DependencyFinding {
  ecosystem: string;
  name: string;
  version: string;
  sourceFile: string;
  scope: string;
  notes: string;
}

export interface DependencyHealthAssessment {
  dependencyName: string;
  detectedVersion: string;
  recommendedVersion: string;
  healthStatus: HealthStatus;
  severity: Severity;
  notes: string;
}

export interface ProjectModule {
  name: string;
  path: string;
  detectedFiles: string[];
  projectTypes: ProjectType[];
  technologies: string[];
  findings: RuleFinding[];
  strengths: RuleFinding[];
  improvements: RuleFinding[];
  riskFlags: RuleFinding[];
  dependencyFindings: DependencyFinding[];
  dependencyHealthAssessments: DependencyHealthAssessment[];
  healthAssessment: ProjectHealthAssessment;
  gitAnalysis: GitAnalysis;
  gitignoreAnalysis: GitignoreAnalysis;
  ciAnalysis: CiAnalysis;
  testAnalysis: TestAnalysis;
}

export interface ProjectWorkspace {
  name: string;
  path: string;
  detectedFiles: string[];
  projectTypes: ProjectType[];
  technologies: string[];
  findings: RuleFinding[];
  strengths: RuleFinding[];
  improvements: RuleFinding[];
  riskFlags: RuleFinding[];
  dependencyFindings: DependencyFinding[];
  dependencyHealthAssessments: DependencyHealthAssessment[];
  modules: ProjectModule[];
  healthAssessment: ProjectHealthAssessment;
  gitAnalysis: GitAnalysis;
  gitignoreAnalysis: GitignoreAnalysis;
  ciAnalysis: CiAnalysis;
  testAnalysis: TestAnalysis;
}

export interface ScanResponse {
  workspacesFound: number;
  workspaces: ProjectWorkspace[];
}

export interface DiscoveredWorkspace {
  name: string;
  path: string;
}

export interface WorkspaceDiscoveryRequest {
  rootPath: string;
}

export interface WorkspaceDiscoveryResponse {
  rootPath: string;
  workspacesFound: number;
  workspaces: DiscoveredWorkspace[];
}

export interface ScanRequest {
  rootPath: string;
  includeProjectNames?: string[];
  excludeProjectNames?: string[];
  includePaths?: string[];
  excludePaths?: string[];
}

export interface ReportRequest {
  rootPath: string;
  includeProjectNames?: string[];
  excludeProjectNames?: string[];
  includePaths?: string[];
  excludePaths?: string[];
}

export interface ReportResponse {
  reportPath: string;
  generatedAt: string;
  format: string;
}
