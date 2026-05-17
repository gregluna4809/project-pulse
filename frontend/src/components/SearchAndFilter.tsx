import React from 'react';

export const FILTER_DEFS = [
  { key: 'has-risks',           label: 'Has Risks' },
  { key: 'at-risk-or-critical', label: 'AT Risk / Critical' },
  { key: 'legacy-deps',         label: 'Legacy Deps' },
  { key: 'spring-boot',         label: 'Spring Boot' },
  { key: 'react',               label: 'React' },
  { key: 'python',              label: 'Python' },
  { key: 'docker',              label: 'Docker' },
  { key: 'monorepos-only',      label: 'Monorepos' },
  { key: 'dormant-repos',       label: 'Dormant' },
  { key: 'no-remote',           label: 'No Remote' },
  { key: 'detached-head',       label: 'Detached HEAD' },
  { key: 'gitignore-risks',     label: 'Gitignore Risks' },
  { key: 'missing-env-ignore',  label: 'Missing .env Ignore' },
  { key: 'weak-hygiene',        label: 'Weak Hygiene' },
  { key: 'no-ci',               label: 'No CI' },
  { key: 'ci-missing-tests',    label: 'CI Missing Tests' },
  { key: 'no-tests',            label: 'No Tests' },
  { key: 'weak-tests',          label: 'Weak Tests' },
  { key: 'integration-tests',   label: 'Integration Tests' },
  { key: 'deploy-risk',         label: 'Deploy Risk' },
] as const;

export type FilterKey = (typeof FILTER_DEFS)[number]['key'];

interface SearchAndFilterProps {
  query: string;
  onQueryChange: (q: string) => void;
  activeFilters: Set<FilterKey>;
  onToggleFilter: (f: FilterKey) => void;
  onClearFilters: () => void;
  totalCount: number;
  filteredCount: number;
}

export const SearchAndFilter: React.FC<SearchAndFilterProps> = ({
  query,
  onQueryChange,
  activeFilters,
  onToggleFilter,
  onClearFilters,
  totalCount,
  filteredCount,
}) => {
  return (
    <div className="space-y-2">
      {/* Search row */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1">
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm select-none pointer-events-none">
            ⌕
          </span>
          <input
            type="text"
            value={query}
            onChange={e => onQueryChange(e.target.value)}
            placeholder="Search workspaces, modules, technologies, dependencies..."
            className="w-full pl-8 pr-3 py-1.5 text-sm bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 placeholder-gray-300"
            spellCheck={false}
            autoComplete="off"
          />
        </div>
        <span className="text-xs text-gray-400 whitespace-nowrap flex-shrink-0">
          {filteredCount === totalCount
            ? `${totalCount} workspace${totalCount !== 1 ? 's' : ''}`
            : `${filteredCount} / ${totalCount} workspaces`}
        </span>
      </div>

      {/* Filter chips */}
      <div className="flex flex-wrap gap-1.5">
        {FILTER_DEFS.map(f => {
          const active = activeFilters.has(f.key);
          return (
            <button
              key={f.key}
              onClick={() => onToggleFilter(f.key)}
              className={
                active
                  ? 'px-2.5 py-1 text-xs font-medium rounded-full border transition-colors bg-blue-600 text-white border-blue-600'
                  : 'px-2.5 py-1 text-xs font-medium rounded-full border transition-colors bg-white text-gray-600 border-gray-200 hover:border-blue-300 hover:text-blue-600'
              }
            >
              {f.label}
            </button>
          );
        })}
        {activeFilters.size > 0 && (
          <button
            onClick={onClearFilters}
            className="px-2.5 py-1 text-xs font-medium rounded-full border border-gray-200 text-gray-400 hover:text-gray-600 hover:border-gray-300 transition-colors"
          >
            Clear
          </button>
        )}
      </div>
    </div>
  );
};
