import React, { useState } from 'react';
import type { RuleFinding } from '../types/projectPulse';

type Variant = 'strength' | 'improvement' | 'risk' | 'general';

interface FindingsListProps {
  title: string;
  findings: RuleFinding[];
  variant: Variant;
  defaultExpanded?: boolean;
}

const variantConfig: Record<Variant, { header: string; icon: string; iconColor: string }> = {
  strength:    { header: 'text-emerald-700', icon: '✓', iconColor: 'text-emerald-500' },
  improvement: { header: 'text-amber-700',   icon: '↑', iconColor: 'text-amber-500'   },
  risk:        { header: 'text-red-700',     icon: '⚠', iconColor: 'text-red-500'     },
  general:     { header: 'text-gray-700',    icon: '·', iconColor: 'text-gray-400'    },
};

export const FindingsList: React.FC<FindingsListProps> = ({
  title,
  findings,
  variant,
  defaultExpanded = false,
}) => {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const cfg = variantConfig[variant];

  if (findings.length === 0) return null;

  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden">
      <button
        className="w-full flex items-center justify-between px-4 py-2.5 bg-gray-50 hover:bg-gray-100 transition-colors text-left"
        onClick={() => setExpanded(v => !v)}
      >
        <span className={`text-sm font-semibold ${cfg.header}`}>
          {title}
          <span className="ml-2 text-xs font-normal text-gray-400">({findings.length})</span>
        </span>
        <span className="text-gray-400 text-xs select-none">{expanded ? '▲' : '▼'}</span>
      </button>

      {expanded && (
        <ul className="divide-y divide-gray-100 bg-white">
          {findings.map((f, i) => (
            <li key={`${f.ruleId}-${i}`} className="px-4 py-3">
              <div className="flex items-start gap-2">
                <span className={`mt-0.5 flex-shrink-0 text-sm leading-none ${cfg.iconColor}`}>{cfg.icon}</span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm text-gray-800">{f.message}</p>
                  {f.evidence && (
                    <p className="mt-1 text-xs font-mono text-gray-400 break-all">{f.evidence}</p>
                  )}
                  <p className="mt-0.5 text-xs text-gray-300">{f.ruleId}</p>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
