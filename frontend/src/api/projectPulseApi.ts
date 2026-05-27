import axios from 'axios';
import type {
  ReportRequest,
  ReportResponse,
  ScanRequest,
  ScanResponse,
  WorkspaceDiscoveryRequest,
  WorkspaceDiscoveryResponse,
} from '../types/projectPulse';

const client = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 120_000,
});

export const scanProjects = (request: ScanRequest): Promise<ScanResponse> =>
  client.post<ScanResponse>('/scan', request).then(r => r.data);

export const discoverWorkspaces = (request: WorkspaceDiscoveryRequest): Promise<WorkspaceDiscoveryResponse> =>
  client.post<WorkspaceDiscoveryResponse>('/workspaces/discover', request).then(r => r.data);

export const exportJsonReport = (request: ReportRequest): Promise<ReportResponse> =>
  client.post<ReportResponse>('/scan/report/json', request).then(r => r.data);

export const exportMarkdownReport = (request: ReportRequest): Promise<ReportResponse> =>
  client.post<ReportResponse>('/scan/report/markdown', request).then(r => r.data);
