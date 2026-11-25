import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlertTriangle, ArrowLeft, Check, CheckCircle2, CircleDashed, Link2, Pencil, RotateCcw, X, XCircle,
} from 'lucide-react';
import { api, usingMock } from '../api/client';
import type { DocumentJob, ExtractedField, PipelineStage } from '../types';
import { Card, Confidence, Empty, Skeleton, StatusBadge, fmtEur, fmtMs, fmtPct, fmtTime, stageLabel } from '../components/ui';

const STAGES: PipelineStage[] = ['INGEST', 'OCR', 'LLM_EXTRACTION', 'VALIDATION', 'PO_MATCHING'];

function boxTone(f: ExtractedField) {
  if (f.corrected) return 'ok';
  if (f.issues.some((i) => i.severity === 'critical')) return 'crit';
  if (f.issues.length || f.confidence < 0.85) return 'warn';
  return 'ok';
}

function InvoicePaper({ doc, fields, focus, onFocus }: {
  doc: DocumentJob; fields: ExtractedField[]; focus?: string; onFocus: (k?: string) => void;
}) {
  const pct = (v: number) => `${v * 100}%`;
  const get = (k: string) => fields.find((f) => f.key === k);
  const text = (k: string) => get(k)?.sourceText ?? get(k)?.value ?? '';
  const right = new Set(['invoice_number', 'invoice_date', 'po_number', 'net_amount', 'vat_amount', 'gross_amount']);
  const printed: Record<string, string> = {
    invoice_number: 'Invoice no.', invoice_date: 'Date', po_number: 'Your order', net_amount: 'Net',
    vat_amount: 'VAT 19%', gross_amount: 'Total due', due_date: 'Payable by', vendor_vat_id: 'VAT ID',
  };
  return (
    <div className="paper" onMouseLeave={() => onFocus(undefined)}>
      <div style={{ position: 'absolute', left: '7%', top: '17%', fontSize: 20, fontWeight: 700, letterSpacing: '.06em' }}>
        {doc.docType === 'CREDIT_NOTE' ? 'CREDIT NOTE' : 'INVOICE'}
      </div>
      <div style={{ position: 'absolute', left: '7%', top: '23%', lineHeight: 1.5, color: '#444' }}>
        Bill to:<br /><b style={{ color: '#1d1d1b' }}>Acme Handels GmbH</b><br />Musterstraße 1, 69190 Walldorf
      </div>
      <table className="ptable">
        <thead><tr><th>Description</th><th className="r">Qty</th><th className="r">Unit</th><th className="r">Amount</th></tr></thead>
        <tbody>
          {doc.lineItems.map((l, i) => (
            <tr key={i}><td>{l.description}</td><td className="r">{l.quantity}</td><td className="r">{l.unitPrice.toFixed(2)}</td><td className="r">{l.total.toFixed(2)}</td></tr>
          ))}
        </tbody>
      </table>
      {fields.map((f) => f.box && (
        <div key={f.key}>
          <div
            className="pfield"
            style={{
              left: pct(f.box.x), top: pct(f.box.y), width: pct(f.box.w), height: pct(f.box.h),
              justifyContent: right.has(f.key) ? 'space-between' : 'flex-start', padding: '0 4px',
              fontSize: f.key === 'vendor_name' ? 17 : f.key === 'gross_amount' ? 13 : 11.5,
              fontWeight: f.key === 'vendor_name' || f.key === 'gross_amount' ? 700 : 400,
            }}
          >
            {printed[f.key] && <span style={{ color: '#666', marginRight: 6 }}>{printed[f.key]}:</span>}
            <span>{text(f.key)}{['net_amount', 'vat_amount', 'gross_amount'].includes(f.key) ? ' EUR' : ''}</span>
          </div>
          <div
            className={`pbox ${boxTone(f)}${focus === f.key ? ' focus' : ''}`}
            style={{ left: `calc(${pct(f.box.x)} - 3px)`, top: `calc(${pct(f.box.y)} - 2px)`, width: `calc(${pct(f.box.w)} + 6px)`, height: `calc(${pct(f.box.h)} + 4px)` }}
            onMouseEnter={() => onFocus(f.key)}
            onClick={() => document.getElementById(`field-${f.key}`)?.focus()}
            title={`${f.label} · ${fmtPct(f.confidence, 0)} confidence`}
          />
        </div>
      ))}
      <div style={{ position: 'absolute', left: '7%', right: '7%', bottom: '4%', borderTop: '1px solid #ddd', paddingTop: 6, color: '#777', fontSize: 9.5 }}>
        {doc.vendor} · Bank details on request · Thank you for your business
      </div>
    </div>
  );
}

/** The real first page of the uploaded PDF, rendered by the backend, with the extracted fields boxed. */
function OriginalPage({ doc, fields, focus, onFocus, onUnavailable }: {
  doc: DocumentJob; fields: ExtractedField[]; focus?: string; onFocus: (k?: string) => void; onUnavailable: () => void;
}) {
  const [src, setSrc] = useState<string | null>();
  useEffect(() => {
    let url: string | null = null;
    api.getPageImage(doc.id, 1).then((u) => {
      url = u;
      if (u) setSrc(u); else onUnavailable();
    }).catch(onUnavailable);
    return () => { if (url) URL.revokeObjectURL(url); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doc.id]);
  const pct = (v: number) => `${v * 100}%`;
  if (!src) return <div className="paper skeleton" />;
  return (
    <div className="paper" style={{ aspectRatio: 'auto' }} onMouseLeave={() => onFocus(undefined)}>
      <img src={src} alt={`Page 1 of ${doc.fileName}`} style={{ width: '100%', display: 'block' }} />
      {fields.map((f) => f.box && (
        <div
          key={f.key}
          className={`pbox ${boxTone(f)}${focus === f.key ? ' focus' : ''}`}
          style={{ left: pct(f.box.x), top: pct(f.box.y), width: pct(f.box.w), height: pct(f.box.h) }}
          onMouseEnter={() => onFocus(f.key)}
          onClick={() => document.getElementById(`field-${f.key}`)?.focus()}
          title={`${f.label} · ${fmtPct(f.confidence, 0)} confidence`}
        />
      ))}
    </div>
  );
}

export function DocumentDetail() {
  const { id = '' } = useParams();
  const nav = useNavigate();
  const [doc, setDoc] = useState<DocumentJob | undefined>();
  const [loading, setLoading] = useState(true);
  const [fields, setFields] = useState<ExtractedField[]>([]);
  const [focus, setFocus] = useState<string>();
  const [saving, setSaving] = useState<'APPROVE' | 'REJECT' | null>(null);
  const [toast, setToast] = useState<string>();
  const [noOriginal, setNoOriginal] = useState(false);

  useEffect(() => {
    setLoading(true);
    api.getDocument(id).then((d) => {
      setDoc(d);
      setFields(d?.fields ?? []);
      setLoading(false);
    });
  }, [id]);

  if (loading) return <div className="stack"><Skeleton h={28} w={320} /><Skeleton h={600} /></div>;
  if (!doc) return <Empty>Document {id} not found. <Link to="/documents">Back to documents</Link></Empty>;

  const editable = doc.status === 'NEEDS_REVIEW';
  const openIssues = fields.filter((f) => !f.corrected && f.issues.length > 0).length;
  const lowConf = fields.filter((f) => !f.corrected && f.confidence < 0.85).length;

  const update = (key: string, value: string) =>
    setFields((fs) => fs.map((f) => (f.key === key ? { ...f, value, corrected: value !== doc.fields.find((o) => o.key === key)?.value } : f)));
  const accept = (key: string) => setFields((fs) => fs.map((f) => (f.key === key ? { ...f, corrected: true } : f)));

  const submit = async (decision: 'APPROVE' | 'REJECT') => {
    setSaving(decision);
    const updated = await api.submitReview(doc.id, fields, decision);
    setDoc(updated);
    setFields(updated.fields);
    setSaving(null);
    setToast(decision === 'APPROVE'
      ? `Approved. ${fields.filter((f) => f.corrected).length} correction(s) saved to the evaluation set.`
      : 'Rejected and returned to the vendor workflow.');
    setTimeout(() => setToast(undefined), 3500);
  };

  const doneStages = new Set(doc.timings.map((t) => t.stage));
  const failed = doc.status === 'FAILED' && !editable;

  return (
    <>
      <div className="page-head" style={{ alignItems: 'center' }}>
        <div>
          <button className="btn btn-ghost" style={{ padding: '4px 0', marginBottom: 6, color: 'var(--text-secondary)' }} onClick={() => nav(-1)}>
            <ArrowLeft size={15} />Back
          </button>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: 10 }}>{doc.vendor} <StatusBadge status={doc.status} /></h1>
          <p className="mono" style={{ fontSize: 12.5 }}>
            {doc.fileName} · {doc.id} · received {fmtTime(doc.receivedAt)} · trace {doc.traceId.slice(0, 12)}…
          </p>
        </div>
        {doc.status === 'FAILED' && !usingMock && (
          <button className="btn" disabled={!!saving} onClick={async () => {
            await api.reprocess(doc.id);
            setToast('Processing again. This page updates when it is done.');
            const poll = async () => {
              for (let i = 0; i < 60; i++) {
                await new Promise((r) => setTimeout(r, 1000));
                const d = await api.getDocument(doc.id);
                if (d && d.status !== 'FAILED' && d.status !== 'EXTRACTING' && d.status !== 'VALIDATING' && d.status !== 'RECEIVED') {
                  setDoc(d); setFields(d.fields); setToast(undefined); return;
                }
                if (d && i > 2 && d.status === 'FAILED') { setDoc(d); setToast(undefined); return; }
              }
            };
            poll();
          }}><RotateCcw size={15} />Process again</button>
        )}
        {editable && (
          <div className="toolbar">
            <button className="btn btn-danger" disabled={!!saving} onClick={() => submit('REJECT')}><X size={15} />Reject</button>
            <button className="btn btn-primary" disabled={!!saving || openIssues > 0} onClick={() => submit('APPROVE')}
              title={openIssues ? 'Resolve or accept all flagged fields first' : 'Approve and post'}>
              <Check size={15} />{saving === 'APPROVE' ? 'Approving…' : 'Approve & post'}
            </button>
          </div>
        )}
      </div>

      {doc.status === 'FAILED' && doc.failureReason && (
        <div className="card" style={{ padding: '12px 16px', marginBottom: 16, display: 'flex', gap: 10, alignItems: 'flex-start', borderColor: 'var(--critical)' }}>
          <XCircle size={18} style={{ color: 'var(--critical)', flex: 'none', marginTop: 1 }} />
          <div><b>Processing failed.</b> <span className="secondary">{doc.failureReason}</span></div>
        </div>
      )}
      <div className="review">
        <div className="paper-wrap">
          {doc.hasOriginal && !noOriginal
            ? <OriginalPage doc={doc} fields={fields} focus={focus} onFocus={setFocus} onUnavailable={() => setNoOriginal(true)} />
            : <InvoicePaper doc={doc} fields={fields} focus={focus} onFocus={setFocus} />}
          <div className="legend" style={{ marginTop: 14, marginBottom: 0, justifyContent: 'center' }}>
            <span><i className="swatch" style={{ background: 'var(--highlight)', border: '1px solid rgba(42,120,214,.5)' }} />Extracted</span>
            <span><i className="swatch" style={{ background: 'var(--highlight-warn)', border: '1px solid rgba(200,140,0,.7)' }} />Low confidence</span>
            <span><i className="swatch" style={{ background: 'var(--highlight-crit)', border: '1px solid rgba(208,59,59,.7)' }} />Failed validation</span>
          </div>
        </div>

        <div className="stack">
          <Card
            title="Extracted fields"
            sub={editable
              ? `${openIssues} flagged · ${lowConf} below 85% confidence · hover a field to find it on the page`
              : `Model ${doc.model} · ${doc.tokens.toLocaleString('en-GB')} tokens · ${fmtEur(doc.costEur, 4)}`}
          >
            {fields.length === 0 && (
              <div className="muted" style={{ fontSize: 13, padding: '4px 2px' }}>
                No fields yet. This document is stored and waiting for extraction.
              </div>
            )}
            {fields.map((f) => (
              <div key={f.key} className={`field-row${focus === f.key ? ' focus' : ''}`}
                onMouseEnter={() => setFocus(f.key)} onMouseLeave={() => setFocus(undefined)}>
                <div>
                  <label htmlFor={`field-${f.key}`}>{f.label}</label>
                  <input
                    id={`field-${f.key}`} className="input mono" style={{ marginTop: 4 }} value={f.value}
                    readOnly={!editable} onFocus={() => setFocus(f.key)} onChange={(e) => update(f.key, e.target.value)}
                  />
                </div>
                <div style={{ alignSelf: 'end', paddingBottom: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
                  {f.corrected ? <span className="badge badge-info"><Pencil size={12} />Reviewed</span> : <Confidence value={f.confidence} />}
                </div>
                {!f.corrected && f.issues.map((i) => (
                  <div key={i.rule} className={`issue ${i.severity}`}>
                    {i.severity === 'critical' ? <XCircle size={14} style={{ marginTop: 2, flex: 'none' }} /> : <AlertTriangle size={14} style={{ marginTop: 2, flex: 'none' }} />}
                    <span>
                      <b className="mono" style={{ fontSize: 11.5 }}>{i.rule}</b> — {i.message}
                      {editable && f.sourceText && f.sourceText !== f.value && (
                        <> Page shows <b className="mono">{f.sourceText}</b>.{' '}
                          <a href="#" onClick={(e) => { e.preventDefault(); update(f.key, f.sourceText!); }}>Use page value</a></>
                      )}
                      {editable && (!f.sourceText || f.sourceText === f.value) && (
                        <> <a href="#" onClick={(e) => { e.preventDefault(); accept(f.key); }}>Mark as correct</a></>
                      )}
                    </span>
                  </div>
                ))}
              </div>
            ))}
          </Card>

          {doc.poMatch && (
            <Card title="Purchase order match" sub="Fuzzy match on vendor, PO number and amount">
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 10 }}>
                <Link2 size={18} style={{ color: 'var(--accent)' }} />
                <div style={{ flex: 1 }}>
                  <div className="mono">{doc.poMatch.poNumber}</div>
                  <div className="muted" style={{ fontSize: 12.5 }}>{doc.poMatch.vendor} · PO amount {fmtEur(doc.poMatch.poAmount)}</div>
                </div>
                <Confidence value={doc.poMatch.score} />
              </div>
              {doc.poMatch.deltas.length > 0 ? doc.poMatch.deltas.map((d) => (
                <div key={d} className="issue warning" style={{ display: 'flex', gap: 6, fontSize: 12.5 }}><AlertTriangle size={14} />{d}</div>
              )) : <div className="issue" style={{ display: 'flex', gap: 6, fontSize: 12.5, color: 'var(--good-ink)' }}><CheckCircle2 size={14} />Vendor, PO number and amount agree.</div>}
            </Card>
          )}

          <Card title="Pipeline" sub={`Trace ${doc.traceId.slice(0, 16)}…`}>
            <div className="timeline">
              {STAGES.map((s, i) => {
                const t = doc.timings.find((x) => x.stage === s);
                const isFailedHere = failed && !doneStages.has(s) && i === doc.timings.length;
                return (
                  <div className="tl-step" key={s}>
                    {t ? <CheckCircle2 size={16} style={{ color: 'var(--good)' }} />
                      : isFailedHere ? <XCircle size={16} style={{ color: 'var(--critical)' }} />
                        : <CircleDashed size={16} style={{ color: 'var(--neutral)' }} />}
                    <span>{stageLabel[s]}{isFailedHere && <span className="issue critical" style={{ display: 'inline', marginLeft: 8, fontSize: 12.5 }}>{doc.failureReason ?? 'Processing failed'}</span>}</span>
                    <span className="num muted">{t ? fmtMs(t.ms) : isFailedHere ? 'failed' : '—'}</span>
                  </div>
                );
              })}
            </div>
          </Card>
        </div>
      </div>
      {toast && <div className="toast"><CheckCircle2 size={16} />{toast}</div>}
    </>
  );
}
