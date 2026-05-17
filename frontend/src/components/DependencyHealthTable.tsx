import React, { useState } from 'react';
import type { DependencyHealthAssessment } from '../types/projectPulse';
import { HealthStatusBadge, SeverityBadge } from './StatusBadge';

interface DependencyHealthTableProps {
  assessments: DependencyHealthAssessment[];
  defaultExpanded?: boolean;
}

export const DependencyHealthTable: React.FC<DependencyHealthTableProps> = ({
  assessments,
  defaultExpanded = false,
}) => {
  const [expanded, setExpanded] = useState(defaultExpanded);

  if (assessments.length === 0) return null;

  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden">
      <button
        className="w-full flex items-center justify-between px-4 py-2.5 bg-gray-50 hover:bg-gray-100 transition-colors text-left"
        onClick={() => setExpanded(v => !v)}
      >
        <span className="text-sm font-semibold text-gray-700">
          Dependency Health
          <span className="ml-2 text-xs font-normal text-gray-400">({assessments.length})</span>
        </span>
        <span className="text-gray-400 text-xs select-none">{expanded ? '▲' : '▼'}</span>
      </button>

      {expanded && (
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                {['Dependency', 'Detected', 'Recommended', 'Status', 'Severity', 'Notes'].map(h => (
                  <th
                    key={h}
                    className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider whitespace-nowrap"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-100">
              {assessments.map((a, i) => (
                <tr key={`${a.dependencyName}-${i}`} className="hover:bg-gray-50">
                  <td className="px-4 py-2 font-mono text-gray-900 whitespace-nowrap">{a.dependencyName}</td>
                  <td className="px-4 py-2 font-mono text-gray-600 whitespace-nowrap">{a.detectedVersion || '—'}</td>
                  <td className="px-4 py-2 font-mono text-gray-600 whitespace-nowrap">{a.recommendedVersion || '—'}</td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    <HealthStatusBadge status={a.healthStatus} />
                  </td>
                  <td className="px-4 py-2 whitespace-nowrap">
                    <SeverityBadge severity={a.severity} />
                  </td>
                  <td className="px-4 py-2 text-xs text-gray-500 max-w-xs">{a.notes || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
