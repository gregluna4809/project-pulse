import React from 'react';
import type { GitActivityStatus, HealthStatus, HealthTier, Severity } from '../types/projectPulse';

type BadgeConfig = { label: string; className: string };

const healthStatusConfig: Record<HealthStatus, BadgeConfig> = {
  CURRENT:           { label: 'Current',           className: 'bg-emerald-100 text-emerald-800 border-emerald-200' },
  ACCEPTABLE:        { label: 'Acceptable',         className: 'bg-sky-100 text-sky-800 border-sky-200' },
  UPGRADE_CANDIDATE: { label: 'Upgrade Candidate',  className: 'bg-amber-100 text-amber-800 border-amber-200' },
  LEGACY:            { label: 'Legacy',             className: 'bg-orange-100 text-orange-800 border-orange-200' },
  CRITICAL:          { label: 'Critical',           className: 'bg-red-100 text-red-800 border-red-200' },
  UNKNOWN:           { label: 'Unknown',            className: 'bg-gray-100 text-gray-600 border-gray-200' },
  MANAGED:           { label: 'Managed',            className: 'bg-violet-100 text-violet-800 border-violet-200' },
};

const healthTierConfig: Record<HealthTier, BadgeConfig> = {
  EXCELLENT: { label: 'Excellent', className: 'bg-emerald-100 text-emerald-800 border-emerald-200' },
  GOOD:      { label: 'Good',      className: 'bg-sky-100 text-sky-800 border-sky-200' },
  FAIR:      { label: 'Fair',      className: 'bg-amber-100 text-amber-800 border-amber-200' },
  AT_RISK:   { label: 'At Risk',   className: 'bg-orange-100 text-orange-800 border-orange-200' },
  CRITICAL:  { label: 'Critical',  className: 'bg-red-100 text-red-800 border-red-200' },
};

const severityConfig: Record<Severity, BadgeConfig> = {
  INFO:     { label: 'Info',     className: 'bg-gray-100 text-gray-600 border-gray-200' },
  LOW:      { label: 'Low',      className: 'bg-sky-100 text-sky-700 border-sky-200' },
  MEDIUM:   { label: 'Medium',   className: 'bg-amber-100 text-amber-800 border-amber-200' },
  HIGH:     { label: 'High',     className: 'bg-orange-100 text-orange-800 border-orange-200' },
  CRITICAL: { label: 'Critical', className: 'bg-red-100 text-red-800 border-red-200' },
};

const gitActivityConfig: Record<GitActivityStatus, BadgeConfig> = {
  ACTIVE:  { label: 'Active',   className: 'bg-emerald-100 text-emerald-800 border-emerald-200' },
  QUIET:   { label: 'Quiet',    className: 'bg-sky-100 text-sky-800 border-sky-200' },
  STALE:   { label: 'Stale',    className: 'bg-amber-100 text-amber-800 border-amber-200' },
  DORMANT: { label: 'Dormant',  className: 'bg-red-100 text-red-800 border-red-200' },
  UNKNOWN: { label: 'Unknown',  className: 'bg-gray-100 text-gray-600 border-gray-200' },
  NO_GIT:  { label: 'No Git',   className: 'bg-gray-100 text-gray-500 border-gray-200' },
};

const BASE = 'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border';

export const HealthStatusBadge: React.FC<{ status: HealthStatus }> = ({ status }) => {
  const cfg = healthStatusConfig[status] ?? { label: status, className: 'bg-gray-100 text-gray-600 border-gray-200' };
  return <span className={`${BASE} ${cfg.className}`}>{cfg.label}</span>;
};

export const HealthTierBadge: React.FC<{ tier: HealthTier }> = ({ tier }) => {
  const cfg = healthTierConfig[tier] ?? { label: tier, className: 'bg-gray-100 text-gray-600 border-gray-200' };
  return <span className={`${BASE} ${cfg.className}`}>{cfg.label}</span>;
};

export const SeverityBadge: React.FC<{ severity: Severity }> = ({ severity }) => {
  const cfg = severityConfig[severity] ?? { label: severity, className: 'bg-gray-100 text-gray-600 border-gray-200' };
  return <span className={`${BASE} ${cfg.className}`}>{cfg.label}</span>;
};

export const GitActivityBadge: React.FC<{ status: GitActivityStatus }> = ({ status }) => {
  const cfg = gitActivityConfig[status] ?? { label: status, className: 'bg-gray-100 text-gray-600 border-gray-200' };
  return <span className={`${BASE} ${cfg.className}`}>{cfg.label}</span>;
};
