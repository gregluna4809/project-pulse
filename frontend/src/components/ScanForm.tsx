import React from 'react';

interface ScanFormProps {
  rootPath: string;
  onRootPathChange: (rootPath: string) => void;
  onDiscover: (rootPath: string) => void;
  isLoading: boolean;
}

export const ScanForm: React.FC<ScanFormProps> = ({ rootPath, onRootPathChange, onDiscover, isLoading }) => {
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = rootPath.trim();
    if (trimmed) onDiscover(trimmed);
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col sm:flex-row gap-3">
      <div className="flex-1">
        <label htmlFor="rootPath" className="block text-xs font-medium text-gray-500 uppercase tracking-wider mb-1.5">
          Root Directory Path
        </label>
        <input
          id="rootPath"
          type="text"
          value={rootPath}
          onChange={e => onRootPathChange(e.target.value)}
          placeholder="C:\Users\you\Projects"
          className="w-full px-3 py-2 text-sm font-mono bg-white border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 placeholder-gray-300 disabled:bg-gray-50 disabled:text-gray-400"
          disabled={isLoading}
          spellCheck={false}
          autoComplete="off"
        />
      </div>
      <div className="flex items-end">
        <button
          type="submit"
          disabled={isLoading || !rootPath.trim()}
          className="px-5 py-2 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:bg-blue-300 text-white text-sm font-semibold rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 whitespace-nowrap"
        >
          {isLoading ? (
            <span className="flex items-center gap-2">
              <span className="inline-block w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              Discovering...
            </span>
          ) : (
            'Discover Workspaces'
          )}
        </button>
      </div>
    </form>
  );
};
