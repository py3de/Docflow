import type {
  DocumentJob,
  DocStatus,
  EvalRun,
  ExtractedField,
  LineItem,
  OverviewMetrics,
  StageTiming,
} from '../types';

// Deterministic PRNG so the demo looks the same on every load.
function mulberry32(seed: number) {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = mulberry32(42);
const pick = <T,>(arr: T[]) => arr[Math.floor(rand() * arr.length)];
const between = (a: number, b: number) => a + rand() * (b - a);
const round2 = (n: number) => Math.round(n * 100) / 100;

// Fictional vendors only.
const VENDORS = [
  { name: 'Nordwerk Logistik GmbH', vat: 'DE287451903' },
  { name: 'Alpenblick Papier AG', vat: 'DE311902846' },
  { name: 'Rheintal Office Supply', vat: 'DE194477310' },
  { name: 'Kessler Maschinenbau KG', vat: 'DE265018837' },
  { name: 'Brightline Cloud Services', vat: 'DE330571264' },
  { name: 'Schwarzwald Druck GmbH', vat: 'DE148830952' },
  { name: 'Mainfeld Facility Services', vat: 'DE276604418' },
];

const ITEMS = [
  ['Pallet transport Walldorf–Mannheim', 180],
  ['Copy paper A4, 80 g (box of 5)', 24.9],
  ['Toner cartridge, black', 89],
  ['Hydraulic seal kit', 46.5],
  ['Cloud storage, 1 TB / month', 19.99],
  ['Brochure print run, 500 pcs', 312],
  ['Office cleaning, monthly', 640],
  ['Forklift maintenance', 275],
  ['Shipping labels (roll of 1,000)', 32.4],
] as const;

const MODELS = ['gpt-4.1-mini', 'claude-haiku-4-5', 'mistral-small'];

// Where each field sits on the rendered invoice page (relative coords).
export const FIELD_LAYOUT: Record<string, { label: string; box: { x: number; y: number; w: number; h: number } }> = {
  vendor_name: { label: 'Vendor name', box: { x: 0.07, y: 0.06, w: 0.46, h: 0.045 } },
  vendor_vat_id: { label: 'Vendor VAT ID', box: { x: 0.07, y: 0.115, w: 0.3, h: 0.03 } },
  invoice_number: { label: 'Invoice number', box: { x: 0.58, y: 0.2, w: 0.35, h: 0.03 } },
  invoice_date: { label: 'Invoice date', box: { x: 0.58, y: 0.235, w: 0.35, h: 0.03 } },
  po_number: { label: 'PO number', box: { x: 0.58, y: 0.27, w: 0.35, h: 0.03 } },
  net_amount: { label: 'Net amount', box: { x: 0.58, y: 0.7, w: 0.35, h: 0.03 } },
  vat_amount: { label: 'VAT (19%)', box: { x: 0.58, y: 0.735, w: 0.35, h: 0.03 } },
  gross_amount: { label: 'Gross amount', box: { x: 0.58, y: 0.775, w: 0.35, h: 0.035 } },
  due_date: { label: 'Due date', box: { x: 0.07, y: 0.86, w: 0.4, h: 0.03 } },
};

const STATUSES: DocStatus[] = [
  'MATCHED', 'MATCHED', 'MATCHED', 'MATCHED', 'MATCHED', 'APPROVED', 'APPROVED',
  'NEEDS_REVIEW', 'NEEDS_REVIEW', 'NEEDS_REVIEW', 'EXTRACTING', 'VALIDATING', 'FAILED',
];

const fmtDate = (d: Date) => d.toISOString().slice(0, 10);

function makeTimings(failedAt?: number): StageTiming[] {
  const stages: StageTiming[] = [
    { stage: 'INGEST', ms: Math.round(between(40, 120)) },
    { stage: 'OCR', ms: Math.round(between(300, 1400)) },
    { stage: 'LLM_EXTRACTION', ms: Math.round(between(1200, 3800)) },
    { stage: 'VALIDATION', ms: Math.round(between(20, 90)) },
    { stage: 'PO_MATCHING', ms: Math.round(between(60, 260)) },
  ];
  return failedAt === undefined ? stages : stages.slice(0, failedAt);
}

function makeDoc(i: number, now: Date): DocumentJob {
  const vendor = pick(VENDORS);
  const status = i < 3 ? (['NEEDS_REVIEW', 'MATCHED', 'NEEDS_REVIEW'] as DocStatus[])[i] : pick(STATUSES);
  const received = new Date(now.getTime() - (i * 55 + between(0, 40)) * 60_000);
  const nLines = 1 + Math.floor(rand() * 4);
  const lineItems: LineItem[] = Array.from({ length: nLines }, () => {
    const [description, price] = pick([...ITEMS]);
    const quantity = 1 + Math.floor(rand() * 6);
    return { description, quantity, unitPrice: price, total: round2(price * quantity) };
  });
  const net = round2(lineItems.reduce((s, l) => s + l.total, 0));
  const vat = round2(net * 0.19);
  const gross = round2(net + vat);
  const invNo = `INV-${2026}-${String(10400 + i * 7).padStart(6, '0')}`;
  const po = `PO-4500${String(81200 + i * 3)}`;
  const due = new Date(received.getTime() + 30 * 86_400_000);

  const lowConfidence = status === 'NEEDS_REVIEW';
  const conf = (base: number) => round2(Math.min(0.995, Math.max(0.31, base + between(-0.04, 0.04))));

  const raw: Record<string, string> = {
    vendor_name: vendor.name,
    vendor_vat_id: vendor.vat,
    invoice_number: invNo,
    invoice_date: fmtDate(received),
    po_number: po,
    net_amount: net.toFixed(2),
    vat_amount: vat.toFixed(2),
    gross_amount: gross.toFixed(2),
    due_date: fmtDate(due),
  };

  // Pick which fields go wrong on review documents.
  const weakKeys = lowConfidence ? (i % 2 === 0 ? ['po_number', 'vat_amount'] : ['invoice_date', 'vendor_vat_id']) : [];

  const fields: ExtractedField[] = Object.entries(FIELD_LAYOUT).map(([key, meta]) => {
    const weak = weakKeys.includes(key);
    let value = raw[key];
    const issues: ExtractedField['issues'] = [];
    if (weak && key === 'vat_amount') {
      value = round2(vat + 10).toFixed(2);
      issues.push({ rule: 'TOTALS_CONSISTENT', severity: 'critical', message: 'Net + VAT does not equal gross (off by 10.00).' });
    }
    if (weak && key === 'po_number') {
      value = po.replace('4500', '45O0');
      issues.push({ rule: 'PO_FORMAT', severity: 'warning', message: 'Letter "O" found where a digit is expected — likely OCR confusion.' });
    }
    if (weak && key === 'invoice_date') {
      value = fmtDate(received).split('-').reverse().join('.');
      issues.push({ rule: 'DATE_ISO', severity: 'warning', message: 'Date returned as DD.MM.YYYY; normalised value needs confirmation.' });
    }
    if (weak && key === 'vendor_vat_id') {
      value = vendor.vat.slice(0, -1);
      issues.push({ rule: 'VAT_ID_FORMAT', severity: 'critical', message: 'German VAT IDs have 9 digits after "DE"; found 8.' });
    }
    return {
      key,
      label: meta.label,
      value,
      sourceText: raw[key],
      confidence: weak ? conf(0.58) : conf(lowConfidence ? 0.9 : 0.955),
      box: meta.box,
      issues,
    };
  });

  const avgConfidence = round2(fields.reduce((s, f) => s + f.confidence, 0) / fields.length);
  const model = pick(MODELS);
  const tokens = Math.round(between(1800, 5200));
  const failed = status === 'FAILED';
  const inFlight = status === 'EXTRACTING' || status === 'VALIDATING';

  return {
    id: `doc_${(0x7a3f10 + i * 977).toString(16)}`,
    tenantId: pick(['acme-de', 'acme-de', 'globex-eu']),
    fileName: `${vendor.name.split(' ')[0].toLowerCase()}_${invNo.toLowerCase()}.pdf`,
    docType: rand() > 0.85 ? 'CREDIT_NOTE' : 'INVOICE',
    status,
    receivedAt: received.toISOString(),
    vendor: vendor.name,
    grossAmount: gross,
    currency: 'EUR',
    avgConfidence,
    model,
    tokens,
    costEur: Math.round((tokens * 0.0000009 + 0.0012) * 10000) / 10000,
    timings: failed ? makeTimings(2) : inFlight ? makeTimings(status === 'EXTRACTING' ? 2 : 3) : makeTimings(),
    fields,
    lineItems,
    poMatch:
      status === 'MATCHED' || status === 'NEEDS_REVIEW' || status === 'APPROVED'
        ? {
            poNumber: po,
            vendor: vendor.name,
            poAmount: status === 'NEEDS_REVIEW' && i % 2 === 0 ? round2(gross - 10) : gross,
            score: status === 'NEEDS_REVIEW' ? round2(between(0.71, 0.84)) : round2(between(0.93, 0.995)),
            deltas: status === 'NEEDS_REVIEW' && i % 2 === 0 ? ['Gross amount differs from PO by 10.00 EUR'] : [],
          }
        : undefined,
    traceId: Array.from({ length: 32 }, () => Math.floor(rand() * 16).toString(16)).join(''),
  };
}

const NOW = new Date('2026-11-23T08:00:00Z');
export const MOCK_DOCS: DocumentJob[] = Array.from({ length: 48 }, (_, i) => makeDoc(i, NOW)).sort(
  (a, b) => b.receivedAt.localeCompare(a.receivedAt),
);

export const MOCK_OVERVIEW: OverviewMetrics = (() => {
  const daily = Array.from({ length: 14 }, (_, k) => {
    const d = new Date(NOW.getTime() - (13 - k) * 86_400_000);
    const weekend = d.getUTCDay() === 0 || d.getUTCDay() === 6;
    const total = Math.round((weekend ? between(40, 70) : between(260, 380)) * (1 + k * 0.012));
    const humanReview = Math.round(total * between(0.11, 0.17) * (1 - k * 0.012));
    return { date: fmtDate(d), straightThrough: total - humanReview, humanReview };
  });
  const last7 = daily.slice(7);
  const prev7 = daily.slice(0, 7);
  const sum = (a: typeof daily) => a.reduce((s, d) => s + d.straightThrough + d.humanReview, 0);
  const st = (a: typeof daily) => a.reduce((s, d) => s + d.straightThrough, 0) / sum(a);
  return {
    processed7d: sum(last7),
    processedPrev7d: sum(prev7),
    straightThroughRate: st(last7),
    straightThroughPrev: st(prev7),
    avgConfidence: 0.932,
    p95LatencyMs: 4810,
    p95LatencyPrevMs: 5390,
    costPerDocEur: 0.0041,
    costPerDocPrevEur: 0.0047,
    reviewQueue: MOCK_DOCS.filter((d) => d.status === 'NEEDS_REVIEW').length,
    daily,
    stageP95: [
      { stage: 'INGEST', ms: 118 },
      { stage: 'OCR', ms: 1320 },
      { stage: 'LLM_EXTRACTION', ms: 3540 },
      { stage: 'VALIDATION', ms: 84 },
      { stage: 'PO_MATCHING', ms: 241 },
    ],
  };
})();

const FIELDS = Object.values(FIELD_LAYOUT).map((f) => f.label);
export const MOCK_EVALS: EvalRun[] = Array.from({ length: 16 }, (_, k) => {
  const base = 0.86 + k * 0.0055 + between(-0.008, 0.008);
  // One regression the gate caught.
  const f1 = k === 11 ? 0.874 : Math.round(Math.min(0.962, base) * 1000) / 1000;
  const started = new Date(NOW.getTime() - (15 - k) * 1.6 * 86_400_000);
  return {
    id: `eval_${1200 + k}`,
    commit: Array.from({ length: 7 }, () => Math.floor(rand() * 16).toString(16)).join(''),
    branch: k === 11 ? 'feat/prompt-v4' : k % 3 === 0 ? 'main' : pick(['feat/ocr-tuning', 'feat/schema-v2', 'fix/vat-rule', 'main']),
    model: k < 6 ? 'gpt-4.1-mini' : 'claude-haiku-4-5',
    dataset: 'SROIE + CORD (golden, n=420)',
    startedAt: started.toISOString(),
    f1,
    threshold: 0.88,
    passed: f1 >= 0.88,
    fieldF1: FIELDS.map((field, j) => ({
      field,
      f1: round2(Math.min(0.995, f1 + [0.05, -0.06, 0.03, 0.01, -0.09, 0.02, -0.04, 0.04, 0.0][j] + between(-0.01, 0.01))),
    })),
  };
});
