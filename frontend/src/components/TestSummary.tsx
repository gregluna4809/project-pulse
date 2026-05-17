import React from 'react';
import type { TestAnalysis } from '../types/projectPulse';

interface TestSummaryProps {
  analysis: TestAnalysis;
  compact?: boolean;
}

export const TestSummary: React.FC<TestSummaryProps> = ({ analysis, compact = false }) => {
  const scriptClass = analysis.hasTestScript ? 'text-emerald-600' : 'text-amber-600';

  return (
    <div>
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
        Test Intelligence
      </p>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        <span className={analysis.hasTests ? 'text-emerald-600 font-medium' : 'text-red-600 font-medium'}>
          {analysis.testFileCount} test{analysis.testFileCount !== 1 ? 's' : ''}
        </span>
        <span className={analysis.integrationTestFileCount > 0 ? 'text-sky-600 font-medium' : 'text-gray-400'}>
          {analysis.integrationTestFileCount} integration
        </span>
        <span className={scriptClass}>
          test script {analysis.hasTestScript ? 'yes' : 'no'}
        </span>
        {analysis.detectedFrameworks.length > 0 && (
          <span className="text-slate-600">
            {analysis.detectedFrameworks.join(', ')}
          </span>
        )}
      </div>
      {!compact && analysis.testDirectories.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1">
          {analysis.testDirectories.map(dir => (
            <span key={dir} className="px-1.5 py-0.5 bg-slate-50 text-slate-600 text-xs rounded border border-slate-200 font-mono">
              {dir}
            </span>
          ))}
        </div>
      )}
    </div>
  );
};
