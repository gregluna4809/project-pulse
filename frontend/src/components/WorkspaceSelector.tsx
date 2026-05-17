import React, { useMemo, useState } from 'react';
import type { DiscoveredWorkspace } from '../types/projectPulse';

interface WorkspaceSelectorProps {
  workspaces: DiscoveredWorkspace[];
  selectedPaths: Set<string>;
  onSelectionChange: (selectedPaths: Set<string>) => void;
  onAnalyzeSelected: () => void;
  isScanning: boolean;
}

export const WorkspaceSelector: React.FC<WorkspaceSelectorProps> = ({
  workspaces,
  selectedPaths,
  onSelectionChange,
  onAnalyzeSelected,
  isScanning,
}) => {
  const [query, setQuery] = useState('');

  const filteredWorkspaces = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return workspaces;
    return workspaces.filter(ws =>
      ws.name.toLowerCase().includes(q) || ws.path.toLowerCase().includes(q)
    );
  }, [query, workspaces]);

  const toggleWorkspace = (path: string) => {
    const next = new Set(selectedPaths);
    if (next.has(path)) next.delete(path);
    else next.add(path);
    onSelectionChange(next);
  };

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-gray-800">
            {selectedPaths.size} / {workspaces.length} selected
          </p>
          <p className="text-xs text-gray-400">Choose the immediate workspaces to analyze.</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onSelectionChange(new Set(workspaces.map(ws => ws.path)))}
            className="px-3 py-1.5 text-xs font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
          >
            Select all
          </button>
          <button
            type="button"
            onClick={() => onSelectionChange(new Set())}
            className="px-3 py-1.5 text-xs font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
          >
            Clear all
          </button>
          <button
            type="button"
            onClick={onAnalyzeSelected}
            disabled={isScanning || selectedPaths.size === 0}
            className="px-4 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 rounded-md transition-colors"
          >
            {isScanning ? 'Analyzing...' : 'Analyze Selected'}
          </button>
        </div>
      </div>

      <input
        type="text"
        value={query}
        onChange={e => setQuery(e.target.value)}
        placeholder="Search discovered workspaces"
        className="w-full px-3 py-2 text-sm bg-white border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 placeholder-gray-300"
      />

      <div className="max-h-64 overflow-y-auto border border-gray-100 rounded-lg divide-y divide-gray-100">
        {filteredWorkspaces.length === 0 ? (
          <div className="px-3 py-6 text-center text-sm text-gray-400">No discovered workspaces match.</div>
        ) : (
          filteredWorkspaces.map(ws => (
            <label key={ws.path} className="flex items-start gap-3 px-3 py-2.5 hover:bg-gray-50 cursor-pointer">
              <input
                type="checkbox"
                checked={selectedPaths.has(ws.path)}
                onChange={() => toggleWorkspace(ws.path)}
                className="mt-0.5 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
              />
              <span className="min-w-0">
                <span className="block text-sm font-medium text-gray-800">{ws.name}</span>
                <span className="block text-xs font-mono text-gray-400 truncate">{ws.path}</span>
              </span>
            </label>
          ))
        )}
      </div>
    </div>
  );
};
