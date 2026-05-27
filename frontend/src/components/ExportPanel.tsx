import React, { useState } from 'react';

interface ExportPanelProps {
  rootPath: string;
  includePaths?: string[];
  onExportJson: (rootPath: string, includePaths?: string[]) => Promise<string>;
  onExportMarkdown: (rootPath: string, includePaths?: string[]) => Promise<string>;
}

type ExportState =
  | { tag: 'idle' }
  | { tag: 'loading'; format: 'JSON' | 'Markdown' }
  | { tag: 'done'; path: string; format: string }
  | { tag: 'error'; message: string };

function reportFilename(reportPath: string): string | null {
  const filename = reportPath.split(/[\\/]/).filter(Boolean).pop();
  if (!filename || filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
    return null;
  }
  return filename;
}

function reportDownloadUrl(reportPath: string): string | null {
  const filename = reportFilename(reportPath);
  return filename ? `/api/reports/${encodeURIComponent(filename)}` : null;
}

function reportPublicUrl(reportPath: string, inline = false): string | null {
  const downloadUrl = reportDownloadUrl(reportPath);
  if (!downloadUrl) {
    return null;
  }

  const url = new URL(downloadUrl, window.location.origin);
  if (inline) {
    url.searchParams.set('inline', 'true');
  }
  return url.toString();
}

function downloadReport(reportPath: string) {
  const filename = reportFilename(reportPath);
  const url = reportDownloadUrl(reportPath);
  if (!filename || !url) {
    throw new Error('Backend returned an invalid report path.');
  }

  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
}

export const ExportPanel: React.FC<ExportPanelProps> = ({
  rootPath,
  includePaths,
  onExportJson,
  onExportMarkdown,
}) => {
  const [state, setState] = useState<ExportState>({ tag: 'idle' });
  const [copied, setCopied] = useState(false);

  const handleExport = async (format: 'JSON' | 'Markdown') => {
    setState({ tag: 'loading', format });
    setCopied(false);
    try {
      const path =
        format === 'JSON'
          ? await onExportJson(rootPath, includePaths)
          : await onExportMarkdown(rootPath, includePaths);
      downloadReport(path);
      setState({ tag: 'done', path, format });
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : 'Export failed. Check that the backend is running.';
      setState({ tag: 'error', message });
    }
  };

  const handleCopyPath = async () => {
    if (state.tag !== 'done') return;
    const publicUrl = reportPublicUrl(state.path);
    if (!publicUrl) {
      setState({ tag: 'error', message: 'Backend returned an invalid report path.' });
      return;
    }

    try {
      await navigator.clipboard.writeText(publicUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API unavailable in some contexts
    }
  };

  const handleOpenReport = () => {
    if (state.tag !== 'done') return;
    const url = reportPublicUrl(state.path, true);
    if (!url) {
      setState({ tag: 'error', message: 'Backend returned an invalid report path.' });
      return;
    }

    const opened = window.open(url, '_blank', 'noopener,noreferrer');
    if (!opened) {
      window.location.assign(url);
    }
  };

  const busy = state.tag === 'loading';

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider mr-1">
          Export
        </span>
        <button
          onClick={() => handleExport('JSON')}
          disabled={busy}
          className="px-3 py-1.5 bg-slate-700 hover:bg-slate-800 active:bg-slate-900 disabled:bg-slate-400 text-white text-xs font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-slate-500 focus:ring-offset-1"
        >
          {busy && state.format === 'JSON' ? (
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-2.5 h-2.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              Exporting...
            </span>
          ) : (
            'JSON Report'
          )}
        </button>
        <button
          onClick={() => handleExport('Markdown')}
          disabled={busy}
          className="px-3 py-1.5 bg-slate-700 hover:bg-slate-800 active:bg-slate-900 disabled:bg-slate-400 text-white text-xs font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-slate-500 focus:ring-offset-1"
        >
          {busy && state.format === 'Markdown' ? (
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-2.5 h-2.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              Exporting...
            </span>
          ) : (
            'Markdown Report'
          )}
        </button>
      </div>

      {state.tag === 'done' && (
        <div className="mt-2 flex items-start gap-2 p-2.5 bg-emerald-50 border border-emerald-200 rounded-lg">
          <span className="text-emerald-500 text-sm flex-shrink-0 mt-0.5">OK</span>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold text-emerald-700 mb-0.5">{state.format} report saved</p>
            <p className="text-xs font-mono text-emerald-800 break-all">{state.path}</p>
          </div>
          <div className="flex flex-col gap-1 flex-shrink-0">
            <button
              onClick={handleCopyPath}
              className="px-2 py-1 text-xs font-medium text-emerald-700 hover:text-emerald-900 bg-emerald-100 hover:bg-emerald-200 rounded transition-colors"
            >
              {copied ? 'Copied' : 'Copy'}
            </button>
            <button
              onClick={handleOpenReport}
              className="px-2 py-1 text-xs font-medium text-emerald-700 hover:text-emerald-900 bg-emerald-100 hover:bg-emerald-200 rounded transition-colors"
            >
              Open
            </button>
          </div>
        </div>
      )}

      {state.tag === 'error' && (
        <div className="mt-2 p-2.5 bg-red-50 border border-red-200 rounded-lg">
          <p className="text-xs text-red-700">{state.message}</p>
        </div>
      )}
    </div>
  );
};
