import React, { useState } from 'react';
import type { HealthTier, ProjectModule } from '../types/projectPulse';
import { FindingsList } from './FindingsList';
import { DependencyHealthTable } from './DependencyHealthTable';
import { HealthTierBadge } from './StatusBadge';
import { CopyPathButton } from './CopyPathButton';
import { TestSummary } from './TestSummary';

interface ModuleCardProps {
  module: ProjectModule;
}

const TIER_SCORE_COLOR: Record<HealthTier, string> = {
  EXCELLENT: 'text-emerald-600',
  GOOD:      'text-sky-600',
  FAIR:      'text-amber-600',
  AT_RISK:   'text-orange-600',
  CRITICAL:  'text-red-600',
};

export const ModuleCard: React.FC<ModuleCardProps> = ({ module }) => {
  const [expanded, setExpanded] = useState(false);
  const { healthAssessment } = module;
  const scoreColor = TIER_SCORE_COLOR[healthAssessment.tier];

  return (
    <div className="border border-gray-200 rounded-lg bg-white overflow-hidden">
      <button
        className="w-full flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors text-left"
        onClick={() => setExpanded(v => !v)}
      >
        <div className="flex items-center gap-3 min-w-0">
          <span className="text-gray-300 text-xs flex-shrink-0">▣</span>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-800">{module.name}</p>
            <div className="flex items-center gap-0.5 min-w-0 max-w-xs sm:max-w-md">
              <p className="text-xs font-mono text-gray-400 truncate flex-1 min-w-0">{module.path}</p>
              <CopyPathButton path={module.path} />
            </div>
          </div>
        </div>
        <div className="flex items-center gap-3 ml-2 flex-shrink-0">
          {module.technologies.length > 0 && (
            <div className="hidden sm:flex flex-wrap gap-1">
              {module.technologies.slice(0, 3).map(t => (
                <span key={t} className="px-1.5 py-0.5 bg-slate-100 text-slate-600 text-xs rounded border border-slate-200">
                  {t}
                </span>
              ))}
              {module.technologies.length > 3 && (
                <span className="px-1.5 py-0.5 bg-slate-100 text-slate-500 text-xs rounded">
                  +{module.technologies.length - 3}
                </span>
              )}
            </div>
          )}
          <div className="flex items-center gap-1.5">
            <span className={`text-sm font-bold leading-none ${scoreColor}`}>{healthAssessment.score}</span>
            <span className="text-xs text-gray-300">/100</span>
            <HealthTierBadge tier={healthAssessment.tier} />
          </div>
          <span className="text-gray-400 text-xs select-none">{expanded ? '▲' : '▼'}</span>
        </div>
      </button>

      {expanded && (
        <div className="px-4 pb-4 pt-1 space-y-3 border-t border-gray-100">
          {module.technologies.length > 0 && (
            <div className="pt-2">
              <p className="text-xs font-medium text-gray-400 uppercase tracking-wider mb-2">Technologies</p>
              <div className="flex flex-wrap gap-1.5">
                {module.technologies.map(t => (
                  <span key={t} className="px-2 py-0.5 bg-slate-100 text-slate-700 text-xs rounded border border-slate-200">
                    {t}
                  </span>
                ))}
              </div>
            </div>
          )}
          {module.gitignoreAnalysis.hasGitignore && (
            <div>
              <p className="text-xs font-medium text-gray-400 uppercase tracking-wider mb-1.5">.gitignore Hygiene</p>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                <span className={
                  module.gitignoreAnalysis.hygieneScore >= 80
                    ? 'font-semibold text-emerald-600'
                    : module.gitignoreAnalysis.hygieneScore >= 50
                      ? 'font-semibold text-amber-600'
                      : 'font-semibold text-red-600'
                }>
                  {module.gitignoreAnalysis.hygieneScore}% coverage
                </span>
                {module.gitignoreAnalysis.notes && (
                  <span className="text-gray-400">{module.gitignoreAnalysis.notes}</span>
                )}
              </div>
              {module.gitignoreAnalysis.missingExpectedPatterns.length > 0 && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {module.gitignoreAnalysis.missingExpectedPatterns.map(p => (
                    <span key={p} className="px-1.5 py-0.5 bg-amber-50 text-amber-700 text-xs rounded border border-amber-200 font-mono">
                      {p}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}
          <TestSummary analysis={module.testAnalysis} compact />
          {module.ciAnalysis.hasCi && (
            <div>
              <p className="text-xs font-medium text-gray-400 uppercase tracking-wider mb-1.5">CI / GitHub Actions</p>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                <span className="text-slate-600 font-medium">
                  {module.ciAnalysis.workflowCount} workflow{module.ciAnalysis.workflowCount !== 1 ? 's' : ''}
                </span>
                {module.ciAnalysis.hasPullRequestTrigger
                  ? <span className="text-emerald-600">✓ PR validation</span>
                  : <span className="text-amber-600">✗ no PR trigger</span>
                }
                {module.ciAnalysis.hasBuildJob && <span className="text-sky-600">build</span>}
                {module.ciAnalysis.hasTestJob && <span className="text-sky-600">test</span>}
                {module.ciAnalysis.hasDeployJob && (
                  module.ciAnalysis.hasManualTrigger
                    ? <span className="text-slate-500">deploy (manual gate)</span>
                    : <span className="text-red-600">deploy (no manual gate)</span>
                )}
              </div>
              {module.ciAnalysis.detectedToolchains.length > 0 && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {module.ciAnalysis.detectedToolchains.map(tc => (
                    <span key={tc} className="px-1.5 py-0.5 bg-slate-50 text-slate-600 text-xs rounded border border-slate-200 font-mono">
                      {tc}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}
          <FindingsList title="Strengths" findings={module.strengths} variant="strength" />
          <FindingsList title="Improvements" findings={module.improvements} variant="improvement" />
          <FindingsList title="Risk Flags" findings={module.riskFlags} variant="risk" />
          <DependencyHealthTable assessments={module.dependencyHealthAssessments} />
        </div>
      )}
    </div>
  );
};
