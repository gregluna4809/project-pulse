import React, { useState } from 'react';
import type { HealthTier, ProjectWorkspace } from '../types/projectPulse';
import { FindingsList } from './FindingsList';
import { DependencyHealthTable } from './DependencyHealthTable';
import { ModuleCard } from './ModuleCard';
import { GitActivityBadge, HealthTierBadge } from './StatusBadge';
import { CopyPathButton } from './CopyPathButton';
import { TestSummary } from './TestSummary';

interface WorkspaceCardProps {
  workspace: ProjectWorkspace;
}

const TIER_SCORE_COLOR: Record<HealthTier, string> = {
  EXCELLENT: 'text-emerald-600',
  GOOD:      'text-sky-600',
  FAIR:      'text-amber-600',
  AT_RISK:   'text-orange-600',
  CRITICAL:  'text-red-600',
};

const projectTypeBadgeClass = (projectType: string) =>
  projectType === 'UNKNOWN' || projectType === 'GENERIC' || projectType === 'AD_HOC'
    ? 'px-2 py-0.5 bg-gray-50 text-gray-700 text-xs rounded border border-gray-200 font-medium'
    : 'px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded border border-blue-100 font-medium';

export const WorkspaceCard: React.FC<WorkspaceCardProps> = ({ workspace }) => {
  const [expanded, setExpanded] = useState(false);
  const { healthAssessment } = workspace;
  const scoreColor = TIER_SCORE_COLOR[healthAssessment.tier];

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
      {/* Header — always visible, click to expand */}
      <button
        className="w-full px-6 py-4 hover:bg-gray-50 transition-colors text-left"
        onClick={() => setExpanded(v => !v)}
      >
        <div className="flex items-start gap-3">
          {/* Avatar */}
          <div className="w-9 h-9 rounded-lg bg-slate-800 flex items-center justify-center flex-shrink-0 mt-0.5">
            <span className="text-white text-sm font-bold">
              {workspace.name.charAt(0).toUpperCase()}
            </span>
          </div>

          {/* Name / path / project-type badges */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center flex-wrap gap-x-2 gap-y-1">
              <h3 className="text-base font-bold text-gray-900 leading-tight">{workspace.name}</h3>
              {workspace.technologies.slice(0, 5).map(t => (
                <span
                  key={t}
                  className="px-1.5 py-0.5 bg-slate-100 text-slate-600 text-xs rounded border border-slate-200"
                >
                  {t}
                </span>
              ))}
              {workspace.technologies.length > 5 && (
                <span className="text-xs text-gray-400">
                  +{workspace.technologies.length - 5}
                </span>
              )}
            </div>
            <div className="flex items-center gap-0.5 mt-0.5 min-w-0">
              <p className="text-xs font-mono text-gray-400 truncate flex-1 min-w-0">{workspace.path}</p>
              <CopyPathButton path={workspace.path} />
            </div>
            {workspace.projectTypes.length > 0 && (
              <div className="flex flex-wrap gap-1 mt-1.5">
                {workspace.projectTypes.map(pt => (
                  <span
                    key={pt}
                    className={projectTypeBadgeClass(pt)}
                  >
                    {pt.replace(/_/g, ' ')}
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Health score + tier */}
          <div className="flex-shrink-0 ml-2 text-right">
            <div className="flex items-baseline gap-1 justify-end">
              <span className={`text-2xl font-bold leading-none ${scoreColor}`}>
                {healthAssessment.score}
              </span>
              <span className="text-xs text-gray-300">/100</span>
            </div>
            <div className="mt-1 flex justify-end">
              <HealthTierBadge tier={healthAssessment.tier} />
            </div>
            <p className="text-xs text-gray-400 mt-1 max-w-[180px] text-right leading-snug">
              {healthAssessment.summary}
            </p>
          </div>

          {/* Chevron */}
          <span className="text-gray-400 text-xs select-none ml-1 mt-1 flex-shrink-0">
            {expanded ? '▲' : '▼'}
          </span>
        </div>

        {/* Counts row — always visible in header */}
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-2.5 pl-12 text-xs">
          {workspace.strengths.length > 0 && (
            <span className="text-emerald-600 font-medium">
              ✓ {workspace.strengths.length} strength{workspace.strengths.length !== 1 ? 's' : ''}
            </span>
          )}
          {workspace.improvements.length > 0 && (
            <span className="text-amber-600 font-medium">
              ↑ {workspace.improvements.length} improvement{workspace.improvements.length !== 1 ? 's' : ''}
            </span>
          )}
          {workspace.riskFlags.length > 0 && (
            <span className="text-red-600 font-medium">
              ⚠ {workspace.riskFlags.length} risk{workspace.riskFlags.length !== 1 ? 's' : ''}
            </span>
          )}
          {workspace.dependencyHealthAssessments.length > 0 && (
            <span className="text-slate-500">
              ◈ {workspace.dependencyHealthAssessments.length} dep assessment{workspace.dependencyHealthAssessments.length !== 1 ? 's' : ''}
            </span>
          )}
          <span className={workspace.testAnalysis.hasTests ? 'text-emerald-600 font-medium' : 'text-red-600 font-medium'}>
            tests {workspace.testAnalysis.testFileCount}
          </span>
          {workspace.modules.length > 0 && (
            <span className="text-blue-600 font-medium">
              ▣ {workspace.modules.length} module{workspace.modules.length !== 1 ? 's' : ''}
            </span>
          )}
          {workspace.gitignoreAnalysis.hasGitignore && (
            <span className={
              workspace.gitignoreAnalysis.hygieneScore >= 80
                ? 'text-emerald-600 font-medium'
                : workspace.gitignoreAnalysis.hygieneScore >= 50
                  ? 'text-amber-600 font-medium'
                  : 'text-red-600 font-medium'
            }>
              .gitignore {workspace.gitignoreAnalysis.hygieneScore}%
            </span>
          )}
        </div>

        {/* Git info row — visible only when workspace is a git repository */}
        {workspace.gitAnalysis.gitRepository && (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1.5 pl-12 text-xs">
            <span className="text-slate-500 font-mono">
              ⎇ {workspace.gitAnalysis.currentBranch ?? 'detached HEAD'}
            </span>
            <GitActivityBadge status={workspace.gitAnalysis.activityStatus} />
            {workspace.gitAnalysis.hasRemote ? (
              <span className="text-emerald-600">
                ↑ {workspace.gitAnalysis.remoteNames.join(', ')}
              </span>
            ) : (
              <span className="text-orange-500">no remote</span>
            )}
            {workspace.gitAnalysis.daysSinceLastCommit != null && (
              <span className="text-gray-400">
                {workspace.gitAnalysis.daysSinceLastCommit === 0
                  ? 'committed today'
                  : `${workspace.gitAnalysis.daysSinceLastCommit}d ago`}
              </span>
            )}
            {workspace.gitAnalysis.branchCount > 0 && (
              <span className="text-gray-400">
                {workspace.gitAnalysis.branchCount} branch{workspace.gitAnalysis.branchCount !== 1 ? 'es' : ''}
              </span>
            )}
          </div>
        )}
      </button>

      {/* Expanded detail */}
      {expanded && (
        <div className="border-t border-gray-100 px-6 py-5 space-y-4">
          {workspace.technologies.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                Technologies
              </p>
              <div className="flex flex-wrap gap-1.5">
                {workspace.technologies.map(t => (
                  <span
                    key={t}
                    className="px-2 py-1 bg-slate-100 text-slate-700 text-xs rounded border border-slate-200"
                  >
                    {t}
                  </span>
                ))}
              </div>
            </div>
          )}

          <FindingsList title="Strengths" findings={workspace.strengths} variant="strength" defaultExpanded />
          <FindingsList title="Improvements" findings={workspace.improvements} variant="improvement" defaultExpanded />
          <FindingsList title="Risk Flags" findings={workspace.riskFlags} variant="risk" defaultExpanded />
          <TestSummary analysis={workspace.testAnalysis} />
          <DependencyHealthTable assessments={workspace.dependencyHealthAssessments} defaultExpanded />

          {workspace.gitignoreAnalysis.hasGitignore && (
            <div>
              <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                .gitignore Hygiene
              </p>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                <span className={
                  workspace.gitignoreAnalysis.hygieneScore >= 80
                    ? 'font-semibold text-emerald-600'
                    : workspace.gitignoreAnalysis.hygieneScore >= 50
                      ? 'font-semibold text-amber-600'
                      : 'font-semibold text-red-600'
                }>
                  {workspace.gitignoreAnalysis.hygieneScore}% coverage
                </span>
                {workspace.gitignoreAnalysis.notes && (
                  <span className="text-gray-400">{workspace.gitignoreAnalysis.notes}</span>
                )}
              </div>
              {workspace.gitignoreAnalysis.missingExpectedPatterns.length > 0 && (
                <div className="mt-1.5 flex flex-wrap gap-1">
                  {workspace.gitignoreAnalysis.missingExpectedPatterns.map(p => (
                    <span key={p} className="px-1.5 py-0.5 bg-amber-50 text-amber-700 text-xs rounded border border-amber-200 font-mono">
                      {p}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}

          {workspace.ciAnalysis.hasCi && (
            <div>
              <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                CI / GitHub Actions
              </p>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                <span className="text-slate-600 font-medium">
                  {workspace.ciAnalysis.workflowCount} workflow{workspace.ciAnalysis.workflowCount !== 1 ? 's' : ''}
                </span>
                {workspace.ciAnalysis.hasPullRequestTrigger
                  ? <span className="text-emerald-600">✓ PR validation</span>
                  : <span className="text-amber-600">✗ no PR trigger</span>
                }
                {workspace.ciAnalysis.hasBuildJob && <span className="text-sky-600">build</span>}
                {workspace.ciAnalysis.hasTestJob && <span className="text-sky-600">test</span>}
                {workspace.ciAnalysis.hasDeployJob && (
                  workspace.ciAnalysis.hasManualTrigger
                    ? <span className="text-slate-500">deploy (manual gate)</span>
                    : <span className="text-red-600">deploy (no manual gate)</span>
                )}
              </div>
              {workspace.ciAnalysis.detectedToolchains.length > 0 && (
                <div className="mt-1.5 flex flex-wrap gap-1">
                  {workspace.ciAnalysis.detectedToolchains.map(tc => (
                    <span key={tc} className="px-1.5 py-0.5 bg-slate-50 text-slate-600 text-xs rounded border border-slate-200 font-mono">
                      {tc}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}

          {workspace.modules.length > 0 && (
            <div>
              <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                Modules ({workspace.modules.length})
              </p>
              <div className="space-y-2">
                {workspace.modules.map(m => (
                  <ModuleCard key={m.path} module={m} />
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
