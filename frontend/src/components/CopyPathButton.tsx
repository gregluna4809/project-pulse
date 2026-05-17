import React, { useState } from 'react';

const CopyIcon: React.FC = () => (
  <svg width="11" height="11" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
    <path d="M4 2a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zm2-1a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V2a1 1 0 0 0-1-1zM2 5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1v-1h1v1a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h1v1z"/>
  </svg>
);

const CheckIcon: React.FC = () => (
  <svg width="11" height="11" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
    <path d="M13.854 3.646a.5.5 0 0 1 0 .708l-7 7a.5.5 0 0 1-.708 0l-3.5-3.5a.5.5 0 1 1 .708-.708L6.5 10.293l6.646-6.647a.5.5 0 0 1 .708 0"/>
  </svg>
);

interface CopyPathButtonProps {
  path: string;
}

export const CopyPathButton: React.FC<CopyPathButtonProps> = ({ path }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await navigator.clipboard.writeText(path);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API unavailable
    }
  };

  return (
    <button
      onClick={handleCopy}
      title={copied ? 'Copied!' : 'Copy path'}
      className={`flex-shrink-0 p-1 rounded transition-colors ${
        copied
          ? 'text-emerald-500 bg-emerald-50'
          : 'text-gray-300 hover:text-gray-500 hover:bg-gray-100'
      }`}
    >
      {copied ? <CheckIcon /> : <CopyIcon />}
    </button>
  );
};
