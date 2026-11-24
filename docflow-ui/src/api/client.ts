import type { DocumentJob, DocumentSummary, EvalRun, ExtractedField, OverviewMetrics, UploadRequest } from '../types';
import { MOCK_DOCS, MOCK_EVALS, MOCK_OVERVIEW } from './mock';

/**
 * One interface, two implementations. The UI never knows which one it talks to.
 * Set VITE_API_URL=/api/v1 to use the Spring Boot backend (the Vite dev server proxies /api to :8080);
 * leave it empty to run on mock data.
 */
export interface DocFlowApi {
  getOverview(): Promise<OverviewMetrics>;
  listDocuments(): Promise<DocumentSummary[]>;
  getDocument(id: string): Promise<DocumentJob | undefined>;
  submitReview(id: string, fields: ExtractedField[], decision: 'APPROVE' | 'REJECT'): Promise<DocumentJob>;
  upload(req: UploadRequest): Promise<DocumentJob>;
  listEvalRuns(): Promise<EvalRun[]>;
}

const API_URL: string = import.meta.env.VITE_API_URL ?? '';

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: { Accept: 'application/json', 'X-Tenant-Id': 'acme-de', ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    // Backend returns RFC 7807 problem+json
    const problem = await res.json().catch(() => ({}));
    throw new Error(problem.detail ?? `${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<T>;
}

const httpApi: DocFlowApi = {
  getOverview: () => http('/metrics/overview'),
  listDocuments: () => http('/documents?size=100&sort=receivedAt,desc'),
  getDocument: (id) => http(`/documents/${id}`),
  submitReview: (id, fields, decision) =>
    http(`/documents/${id}/review`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
      body: JSON.stringify({ decision, fields: fields.map(({ key, value }) => ({ key, value })) }),
    }),
  upload: ({ file, tenantId, docType }) => {
    const body = new FormData();
    body.append('file', file);
    body.append('docType', docType);
    return http('/documents', { method: 'POST', body, headers: { 'X-Tenant-Id': tenantId } });
  },
  listEvalRuns: () => http('/evals?limit=20'),
};

// ---- Mock implementation: in-memory, with small latency so loading states are visible.
const docs = structuredClone(MOCK_DOCS);
const wait = (ms = 250) => new Promise((r) => setTimeout(r, ms));

const mockApi: DocFlowApi = {
  async getOverview() {
    await wait();
    return { ...MOCK_OVERVIEW, reviewQueue: docs.filter((d) => d.status === 'NEEDS_REVIEW').length };
  },
  async listDocuments() {
    await wait();
    return [...docs];
  },
  async getDocument(id) {
    await wait(150);
    return docs.find((d) => d.id === id);
  },
  async submitReview(id, fields, decision) {
    await wait(400);
    const doc = docs.find((d) => d.id === id);
    if (!doc) throw new Error('Document not found');
    doc.fields = fields.map((f) => ({ ...f, issues: f.corrected ? [] : f.issues }));
    doc.status = decision === 'APPROVE' ? 'APPROVED' : 'REJECTED';
    return { ...doc };
  },
  async upload({ file, tenantId, docType }) {
    await wait(600);
    // Demo: the upload lands in the review queue so the full flow can be tried.
    const template = docs.find((d) => d.status === 'NEEDS_REVIEW') ?? docs[0];
    const job: DocumentJob = {
      ...structuredClone(template),
      id: `doc_${Math.random().toString(16).slice(2, 8)}`,
      fileName: file.name,
      tenantId,
      docType,
      status: 'NEEDS_REVIEW',
      receivedAt: new Date().toISOString(),
    };
    docs.unshift(job);
    return job;
  },
  async listEvalRuns() {
    await wait();
    return [...MOCK_EVALS].reverse();
  },
};

export const api: DocFlowApi = API_URL ? httpApi : mockApi;
export const usingMock = !API_URL;
