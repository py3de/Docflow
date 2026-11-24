// Contracts shared with the DocFlow Java backend (Spring Boot).
// Keep these in sync with the OpenAPI spec exposed at /v3/api-docs.

export type DocStatus =
  | 'RECEIVED'
  | 'EXTRACTING'
  | 'VALIDATING'
  | 'NEEDS_REVIEW'
  | 'APPROVED'
  | 'MATCHED'
  | 'FAILED';

export type PipelineStage = 'INGEST' | 'OCR' | 'LLM_EXTRACTION' | 'VALIDATION' | 'PO_MATCHING';

export interface BoundingBox {
  /** Relative coordinates on the page, 0..1 */
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface ValidationIssue {
  rule: string;
  severity: 'warning' | 'critical';
  message: string;
}

export interface ExtractedField {
  key: string;
  label: string;
  value: string;
  /** Text as it appears on the page (OCR layer), shown in the document preview */
  sourceText?: string;
  confidence: number; // 0..1, from the LLM layer
  box?: BoundingBox;
  issues: ValidationIssue[];
  corrected?: boolean;
}

export interface LineItem {
  description: string;
  quantity: number;
  unitPrice: number;
  total: number;
}

export interface PoMatch {
  poNumber: string;
  vendor: string;
  poAmount: number;
  score: number; // fuzzy match score 0..1
  deltas: string[];
}

export interface StageTiming {
  stage: PipelineStage;
  ms: number;
}

export interface DocumentJob {
  id: string;
  tenantId: string;
  fileName: string;
  docType: 'INVOICE' | 'RECEIPT' | 'CREDIT_NOTE';
  status: DocStatus;
  receivedAt: string; // ISO
  vendor: string;
  grossAmount: number;
  currency: string;
  avgConfidence: number;
  model: string;
  tokens: number;
  costEur: number;
  timings: StageTiming[];
  fields: ExtractedField[];
  lineItems: LineItem[];
  poMatch?: PoMatch;
  traceId: string;
}

export interface DailyVolume {
  date: string; // ISO date
  straightThrough: number;
  humanReview: number;
}

export interface OverviewMetrics {
  processed7d: number;
  processedPrev7d: number;
  straightThroughRate: number;
  straightThroughPrev: number;
  avgConfidence: number;
  p95LatencyMs: number;
  p95LatencyPrevMs: number;
  costPerDocEur: number;
  costPerDocPrevEur: number;
  reviewQueue: number;
  daily: DailyVolume[];
  stageP95: StageTiming[];
}

export interface EvalRun {
  id: string;
  commit: string;
  branch: string;
  model: string;
  dataset: string;
  startedAt: string;
  f1: number;
  threshold: number;
  passed: boolean;
  fieldF1: { field: string; f1: number }[];
}

export interface UploadRequest {
  file: File;
  tenantId: string;
  docType: DocumentJob['docType'];
}
