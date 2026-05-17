import React from 'react';

interface AppLayoutProps {
  children: React.ReactNode;
}

export const AppLayout: React.FC<AppLayoutProps> = ({ children }) => (
  <div className="min-h-screen bg-gray-50">
    <header className="bg-slate-900 border-b border-slate-700 sticky top-0 z-20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-14 flex items-center gap-4">
        <div className="w-7 h-7 bg-blue-500 rounded-md flex items-center justify-center flex-shrink-0">
          <span className="text-white text-xs font-black tracking-tight">PP</span>
        </div>
        <div className="flex items-baseline gap-3">
          <span className="text-white text-base font-bold tracking-tight">ProjectPulse</span>
          <span className="hidden sm:inline text-slate-500 text-xs">Engineering Analysis Platform</span>
        </div>
      </div>
    </header>
    {children}
  </div>
);
