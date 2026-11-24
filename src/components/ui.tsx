import { useEffect, useState, type ReactNode } from 'react';
import {
  AlertTriangle, ArrowDownRight, ArrowUpRight, CheckCircle2, CircleDashed, Inbox, Loader2, XCircle,
} from 'lucide-react';
import type { DocStatus } from '../types';

// ---------- formatting ----------
export const fmtInt = (n: number) => n.toLocaleString('en-GB');
export const fmtPct = (n: number, d = 1) => `${(n * 100).toFixed(d)}%`;
export const fmtEur = (n: number, d = 2) =>
  n.toLocaleString('en-GB', { style: 'currency', currency: 'EUR', minimumFractionDigits: d, maximumFractionDigits: d });
export const fmtMs = (ms: number) => (ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${ms} ms`);
export const fmtTime = (iso: string) =>
  new Date(iso).toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
export const stageLabel: Record<string, string> = {
  INGEST: 'Ingest', OCR: 'OCR', LLM_EXTRACTION: 'LLM extraction', VALIDATION: 'Validation', PO_MATCHING: 'PO matching',
};

// ---------- data hook ----------
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | undefined>();
  const [error, setError] = useState<string | undefined>();
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let live = true;
    setLoading(true);
    fn()
      .then((d) => live && (setData(d), setError(undefined)))
      .catch((e: Error) => live && setError(e.message))
      .finally(() => live && setLoading(false));
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  return { data, error, loading, reload: () => setTick((t) => t + 1), setData };
}

// ---------- status badge (status colour always paired with icon + label) ----------
const STATUS: Record<DocStatus, { label: string; tone: string; icon: ReactNode }> = {
  RECEIVED: { label: 'Received', tone: '', icon: <CircleDashed size={13} /> },
  EXTRACTING: { label: 'Extracting', tone: 'badge-info', icon: <Loader2 size={13} className="spin" /> },
  VALIDATING: { label: 'Validating', tone: 'badge-info', icon: <Loader2 size={13} className="spin" /> },
  NEEDS_REVIEW: { label: 'Needs review', tone: 'badge-warning', icon: <AlertTriangle size={13} /> },
  APPROVED: { label: 'Approved', tone: 'badge-good', icon: <CheckCircle2 size={13} /> },
  MATCHED: { label: 'Matched to PO', tone: 'badge-good', icon: <CheckCircle2 size={13} /> },
  FAILED: { label: 'Failed', tone: 'badge-critical', icon: <XCircle size={13} /> },
};
export function StatusBadge({ status }: { status: DocStatus }) {
  const s = STATUS[status];
  return <span className={`badge ${s.tone}`}>{s.icon}{s.label}</span>;
}
export const statusLabel = (s: DocStatus) => STATUS[s].label;

export function PassBadge({ passed }: { passed: boolean }) {
  return passed ? (
    <span className="badge badge-good"><CheckCircle2 size={13} />Passed</span>
  ) : (
    <span className="badge badge-critical"><XCircle size={13} />Blocked</span>
  );
}

// ---------- confidence meter ----------
export function confidenceTone(c: number) {
  return c >= 0.85 ? 'var(--good)' : c >= 0.55 ? 'var(--warning)' : 'var(--critical)';
}
export function Confidence({ value }: { value: number }) {
  return (
    <span className="meter" title={`Model confidence ${fmtPct(value, 0)}`}>
      <span className="meter-track">
        <span className="meter-fill" style={{ width: `${value * 100}%`, background: confidenceTone(value), display: 'block' }} />
      </span>
      <span className="num secondary" style={{ fontSize: 12.5, minWidth: 30 }}>{fmtPct(value, 0)}</span>
    </span>
  );
}

// ---------- KPI tile ----------
export function Kpi({
  label, value, delta, goodWhen = 'up', icon,
}: {
  label: string; value: string; delta?: { value: number; text: string }; goodWhen?: 'up' | 'down'; icon?: ReactNode;
}) {
  const good = delta ? (goodWhen === 'up' ? delta.value >= 0 : delta.value <= 0) : true;
  return (
    <div className="card kpi">
      <div className="kpi-label">{icon}{label}</div>
      <div className="kpi-value num">{value}</div>
      {delta && (
        <span className={`kpi-delta ${good ? 'delta-good' : 'delta-bad'}`}>
          {delta.value >= 0 ? <ArrowUpRight size={13} /> : <ArrowDownRight size={13} />}
          {delta.text} <span className="muted">&nbsp;vs prev. 7 days</span>
        </span>
      )}
    </div>
  );
}

export function Card({ title, sub, action, children, pad = true }: {
  title?: string; sub?: string; action?: ReactNode; children: ReactNode; pad?: boolean;
}) {
  return (
    <section className="card">
      {title && (
        <div className="card-head">
          <div><h2>{title}</h2>{sub && <div className="sub">{sub}</div>}</div>
          {action}
        </div>
      )}
      <div className={pad ? 'card-body' : ''}>{children}</div>
    </section>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty"><Inbox size={22} /><div style={{ marginTop: 6 }}>{children}</div></div>;
}

export function Skeleton({ h = 18, w = '100%' }: { h?: number; w?: number | string }) {
  return <div className="skeleton" style={{ height: h, width: w }} />;
}
