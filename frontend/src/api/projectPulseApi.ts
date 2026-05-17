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
  baseURL: 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 120_000,
});

export const scanProjects = (request: ScanRequest): Promise<ScanResponse> =>
  client.post<ScanResponse>('/api/scan', request).then(r => r.data);

export const discoverWorkspaces = (request: WorkspaceDiscoveryRequest): Promise<WorkspaceDiscoveryResponse> =>
  client.post<WorkspaceDiscoveryResponse>('/api/workspaces/discover', request).then(r => r.data);

export const exportJsonReport = (request: ReportRequest): Promise<ReportResponse> =>
  client.post<ReportResponse>('/api/scan/report/json', request).then(r => r.data);

export const exportMarkdownReport = (request: ReportRequest): Promise<ReportResponse> =>
  client.post<ReportResponse>('/api/scan/report/markdown', request).then(r => r.data);
