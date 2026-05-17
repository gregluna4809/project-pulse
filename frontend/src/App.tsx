import React, { useState, useCallback, useMemo } from 'react';
import { AppLayout } from './components/AppLayout';
import { ScanForm } from './components/ScanForm';
import { ExportPanel } from './components/ExportPanel';
import { SearchAndFilter } from './components/SearchAndFilter';
import type { FilterKey } from './components/SearchAndFilter';
import { WorkspaceCard } from './components/WorkspaceCard';
import { WorkspaceSelector } from './components/WorkspaceSelector';
import { discoverWorkspaces, scanProjects, exportJsonReport, exportMarkdownReport } from './api/projectPulseApi';
import type { ScanResponse, ProjectWorkspace, WorkspaceDiscoveryResponse } from './types/projectPulse';
import { isAxiosError } from 'axios';

type AppState =
  | { phase: 'idle' }
  | { phase: 'discovering' }
  | { phase: 'discovered'; data: WorkspaceDiscoveryResponse; rootPath: string }
  | { phase: 'scanning' }
  | { phase: 'results'; data: ScanResponse; rootPath: string; includePaths: string[] }
  | { phase: 'error'; message: string };

const DEFAULT_PATH = 'C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects';
const selectionStorageKey = (rootPath: string) => `projectpulse:selectedWorkspaces:${rootPath.trim().toLowerCase()}`;

function matchesSearch(ws: ProjectWorkspace, query: string): boolean {
  if (!query.trim()) return true;
  const q = query.toLowerCase();
  return (
    ws.name.toLowerCase().includes(q) ||
    ws.path.toLowerCase().includes(q) ||
    ws.technologies.some(t => t.toLowerCase().includes(q)) ||
    ws.modules.some(m => m.name.toLowerCase().includes(q)) ||
    ws.testAnalysis.detectedFrameworks.some(f => f.toLowerCase().includes(q)) ||
    ws.modules.some(m => m.testAnalysis.detectedFrameworks.some(f => f.toLowerCase().includes(q))) ||
    ws.dependencyHealthAssessments.some(d => d.dependencyName.toLowerCase().includes(q))
  );
}

function matchesFilter(ws: ProjectWorkspace, filter: FilterKey): boolean {
  switch (filter) {
    case 'has-risks':
      return ws.riskFlags.length > 0 || ws.modules.some(m => m.riskFlags.length > 0);
    case 'at-risk-or-critical':
      return ws.healthAssessment.tier === 'AT_RISK' || ws.healthAssessment.tier === 'CRITICAL';
    case 'legacy-deps':
      return (
        ws.dependencyHealthAssessments.some(d => d.healthStatus === 'LEGACY' || d.healthStatus === 'CRITICAL') ||
        ws.modules.some(m => m.dependencyHealthAssessments.some(d => d.healthStatus === 'LEGACY' || d.healthStatus === 'CRITICAL'))
      );
    case 'spring-boot':
      return (
        ws.projectTypes.includes('SPRING_BOOT') ||
        ws.technologies.some(t => t.toLowerCase().includes('spring')) ||
        ws.modules.some(m => m.projectTypes.includes('SPRING_BOOT'))
      );
    case 'react':
      return (
        ws.projectTypes.includes('REACT') ||
        ws.technologies.some(t => t.toLowerCase().includes('react')) ||
        ws.modules.some(m => m.projectTypes.includes('REACT'))
      );
    case 'python':
      return (
        ws.projectTypes.includes('PYTHON') ||
        ws.technologies.some(t => t.toLowerCase().includes('python')) ||
        ws.modules.some(m => m.projectTypes.includes('PYTHON'))
      );
    case 'docker':
      return (
        ws.projectTypes.includes('DOCKERIZED') ||
        ws.projectTypes.includes('DOCKER_COMPOSE') ||
        ws.modules.some(m => m.projectTypes.includes('DOCKERIZED') || m.projectTypes.includes('DOCKER_COMPOSE'))
      );
    case 'monorepos-only':
      return ws.modules.length > 0;
    case 'dormant-repos':
      return ws.gitAnalysis.activityStatus === 'DORMANT';
    case 'no-remote':
      return ws.gitAnalysis.gitRepository && !ws.gitAnalysis.hasRemote;
    case 'detached-head':
      return ws.gitAnalysis.detachedHead;
    case 'gitignore-risks':
      return (
        ws.riskFlags.some(r => r.ruleId.startsWith('gitignore-')) ||
        ws.modules.some(m => m.riskFlags.some(r => r.ruleId.startsWith('gitignore-')))
      );
    case 'missing-env-ignore':
      return (
        ws.riskFlags.some(r => r.ruleId === 'gitignore-env-not-ignored') ||
        ws.modules.some(m => m.riskFlags.some(r => r.ruleId === 'gitignore-env-not-ignored'))
      );
    case 'weak-hygiene':
      return (
        (ws.gitignoreAnalysis.hasGitignore && ws.gitignoreAnalysis.hygieneScore < 50) ||
        ws.modules.some(m => m.gitignoreAnalysis.hasGitignore && m.gitignoreAnalysis.hygieneScore < 50)
      );
    case 'no-ci':
      return !ws.ciAnalysis.hasCi && ws.modules.every(m => !m.ciAnalysis.hasCi);
    case 'ci-missing-tests':
      return (
        (ws.ciAnalysis.hasCi && !ws.ciAnalysis.hasTestJob) ||
        (ws.ciAnalysis.hasCi && !ws.testAnalysis.hasTests) ||
        ws.modules.some(m => (m.ciAnalysis.hasCi && !m.ciAnalysis.hasTestJob) || (m.ciAnalysis.hasCi && !m.testAnalysis.hasTests))
      );
    case 'no-tests':
      return !ws.testAnalysis.hasTests && ws.modules.every(m => !m.testAnalysis.hasTests);
    case 'weak-tests':
      return (
        (ws.testAnalysis.hasTests && ws.testAnalysis.testFileCount < 3) ||
        ws.modules.some(m => m.testAnalysis.hasTests && m.testAnalysis.testFileCount < 3)
      );
    case 'integration-tests':
      return ws.testAnalysis.integrationTestFileCount > 0 || ws.modules.some(m => m.testAnalysis.integrationTestFileCount > 0);
    case 'deploy-risk':
      return (
        ws.riskFlags.some(r => r.ruleId === 'ci-deploy-without-manual-trigger') ||
        ws.modules.some(m => m.riskFlags.some(r => r.ruleId === 'ci-deploy-without-manual-trigger'))
      );
  }
}

const App: React.FC = () => {
  const [appState, setAppState] = useState<AppState>({ phase: 'idle' });
  const [rootPath, setRootPath] = useState(DEFAULT_PATH);
  const [selectedWorkspacePaths, setSelectedWorkspacePaths] = useState<Set<string>>(new Set());
  const [searchQuery, setSearchQuery] = useState('');
  const [activeFilters, setActiveFilters] = useState<Set<FilterKey>>(new Set());

  const readPersistedSelection = useCallback((path: string, availablePaths: string[]) => {
    const available = new Set(availablePaths);
    const stored = window.localStorage.getItem(selectionStorageKey(path));
    if (!stored) return new Set(availablePaths);

    try {
      const parsed = JSON.parse(stored);
      if (!Array.isArray(parsed)) return new Set(availablePaths);
      const selected = parsed.filter((item): item is string => typeof item === 'string' && available.has(item));
      return new Set(selected);
    } catch {
      return new Set(availablePaths);
    }
  }, []);

  const persistSelection = useCallback((path: string, selectedPaths: Set<string>) => {
    window.localStorage.setItem(selectionStorageKey(path), JSON.stringify([...selectedPaths]));
  }, []);

  const handleDiscover = useCallback(async (path: string) => {
    setAppState({ phase: 'discovering' });
    setSearchQuery('');
    setActiveFilters(new Set());
    try {
      const data = await discoverWorkspaces({ rootPath: path });
      const selected = readPersistedSelection(path, data.workspaces.map(ws => ws.path));
      setSelectedWorkspacePaths(selected);
      setAppState({ phase: 'discovered', data, rootPath: path });
    } catch (err: unknown) {
      let message = 'Unable to connect to backend. Is ProjectPulse running on port 8080?';
      if (isAxiosError(err)) {
        message = err.response?.data?.message ?? err.message ?? message;
      } else if (err instanceof Error) {
        message = err.message;
      }
      setAppState({ phase: 'error', message });
    }
  }, [readPersistedSelection]);

  const handleSelectionChange = useCallback((selectedPaths: Set<string>) => {
    setSelectedWorkspacePaths(selectedPaths);
    if (appState.phase === 'discovered') {
      persistSelection(appState.rootPath, selectedPaths);
    }
  }, [appState, persistSelection]);

  const handleScan = useCallback(async (scanRootPath: string, includePaths: string[]) => {
    setAppState({ phase: 'scanning' });
    setSearchQuery('');
    setActiveFilters(new Set());
    try {
      const data = await scanProjects({ rootPath: scanRootPath, includePaths });
      setAppState({ phase: 'results', data, rootPath: scanRootPath, includePaths });
    } catch (err: unknown) {
      let message = 'Unable to connect to backend. Is ProjectPulse running on port 8080?';
      if (isAxiosError(err)) {
        message = err.response?.data?.message ?? err.message ?? message;
      } else if (err instanceof Error) {
        message = err.message;
      }
      setAppState({ phase: 'error', message });
    }
  }, []);

  const handleAnalyzeSelected = useCallback(() => {
    if (appState.phase !== 'discovered') return;
    handleScan(appState.rootPath, [...selectedWorkspacePaths]);
  }, [appState, handleScan, selectedWorkspacePaths]);

  const handleExportJson = useCallback(async (rootPath: string, includePaths?: string[]) => {
    const res = await exportJsonReport({ rootPath, includePaths });
    return res.reportPath;
  }, []);

  const handleExportMarkdown = useCallback(async (rootPath: string, includePaths?: string[]) => {
    const res = await exportMarkdownReport({ rootPath, includePaths });
    return res.reportPath;
  }, []);

  const handleToggleFilter = useCallback((filter: FilterKey) => {
    setActiveFilters(prev => {
      const next = new Set(prev);
      if (next.has(filter)) next.delete(filter);
      else next.add(filter);
      return next;
    });
  }, []);

  const handleClearFilters = useCallback(() => setActiveFilters(new Set()), []);

  const filteredWorkspaces = useMemo(() => {
    if (appState.phase !== 'results') return [];
    return appState.data.workspaces.filter(ws => {
      if (!matchesSearch(ws, searchQuery)) return false;
      if (activeFilters.size === 0) return true;
      return [...activeFilters].some(f => matchesFilter(ws, f));
    });
  }, [appState, searchQuery, activeFilters]);

  return (
    <AppLayout>
      {/* Sticky toolbar — sits immediately below the h-14 header */}
      <div className="sticky top-14 z-10 bg-white border-b border-gray-200 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 space-y-3">
          <div className="flex items-end gap-4 flex-wrap">
            <div className="flex-1 min-w-0">
              <ScanForm
                rootPath={rootPath}
                onRootPathChange={setRootPath}
                onDiscover={handleDiscover}
                isLoading={appState.phase === 'discovering' || appState.phase === 'scanning'}
              />
            </div>
            {appState.phase === 'results' && (
              <div className="flex-shrink-0 pb-0.5">
                <ExportPanel
                  rootPath={appState.rootPath}
                  includePaths={appState.includePaths}
                  onExportJson={handleExportJson}
                  onExportMarkdown={handleExportMarkdown}
                />
              </div>
            )}
          </div>
          {appState.phase === 'results' && (
            <SearchAndFilter
              query={searchQuery}
              onQueryChange={setSearchQuery}
              activeFilters={activeFilters}
              onToggleFilter={handleToggleFilter}
              onClearFilters={handleClearFilters}
              totalCount={appState.data.workspaces.length}
              filteredCount={filteredWorkspaces.length}
            />
          )}
        </div>
      </div>

      {/* Content area */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {appState.phase === 'scanning' && (
          <div className="flex items-center justify-center py-20 bg-white border border-gray-200 rounded-xl shadow-sm">
            <div className="text-center">
              <div className="inline-block w-9 h-9 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mb-4" />
              <p className="text-gray-700 font-semibold">Scanning projects...</p>
              <p className="text-gray-400 text-sm mt-1">This may take a moment for large workspaces</p>
            </div>
          </div>
        )}

        {appState.phase === 'discovering' && (
          <div className="flex items-center justify-center py-20 bg-white border border-gray-200 rounded-xl shadow-sm">
            <div className="text-center">
              <div className="inline-block w-9 h-9 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mb-4" />
              <p className="text-gray-700 font-semibold">Discovering workspaces...</p>
              <p className="text-gray-400 text-sm mt-1">Listing immediate child directories only</p>
            </div>
          </div>
        )}

        {appState.phase === 'error' && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-6">
            <p className="text-sm font-semibold text-red-800 mb-1">Scan Failed</p>
            <p className="text-sm text-red-700">{appState.message}</p>
          </div>
        )}

        {appState.phase === 'idle' && (
          <div className="flex items-center justify-center py-24 border-2 border-dashed border-gray-200 rounded-xl">
            <div className="text-center">
              <p className="text-gray-400 font-medium">No scan results yet</p>
              <p className="text-gray-300 text-sm mt-1">
                Enter a root directory path above and discover available workspaces
              </p>
            </div>
          </div>
        )}

        {appState.phase === 'discovered' && (
          <WorkspaceSelector
            workspaces={appState.data.workspaces}
            selectedPaths={selectedWorkspacePaths}
            onSelectionChange={handleSelectionChange}
            onAnalyzeSelected={handleAnalyzeSelected}
            isScanning={false}
          />
        )}

        {appState.phase === 'results' && (
          <>
            <div className="flex items-center justify-between mb-4">
              <p className="text-sm text-gray-500">
                Found{' '}
                <span className="font-semibold text-gray-800">{appState.data.workspacesFound}</span>{' '}
                workspace{appState.data.workspacesFound !== 1 ? 's' : ''}
                {filteredWorkspaces.length !== appState.data.workspaces.length && (
                  <span className="text-gray-400"> · showing {filteredWorkspaces.length}</span>
                )}
              </p>
              <button
                onClick={() => setAppState({ phase: 'idle' })}
                className="text-xs text-gray-400 hover:text-gray-600 transition-colors"
              >
                Clear
              </button>
            </div>

            {filteredWorkspaces.length === 0 ? (
              <div className="flex items-center justify-center py-16 border-2 border-dashed border-gray-200 rounded-xl">
                <div className="text-center">
                  <p className="text-gray-400 font-medium">No workspaces match</p>
                  <p className="text-gray-300 text-sm mt-1">
                    Try adjusting your search or clearing the filters
                  </p>
                </div>
              </div>
            ) : (
              <div className="space-y-4">
                {filteredWorkspaces.map(ws => (
                  <WorkspaceCard key={ws.path} workspace={ws} />
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </AppLayout>
  );
};

export default App;
