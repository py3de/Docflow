import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, CloudUpload, FileText, Loader2 } from 'lucide-react';
import { api, usingMock } from '../api/client';
import type { DocumentJob } from '../types';
import { Card, stageLabel, statusLabel } from '../components/ui';

const ACCEPT = ['application/pdf', 'image/png', 'image/jpeg', 'image/tiff'];
const MAX_MB = 20;
const STEPS = ['INGEST', 'OCR', 'LLM_EXTRACTION', 'VALIDATION', 'PO_MATCHING'] as const;

interface Item { file: File; job?: DocumentJob; step: number; error?: string; real?: boolean }

export function Upload() {
  const [over, setOver] = useState(false);
  const [items, setItems] = useState<Item[]>([]);
  const [tenant, setTenant] = useState('acme-de');
  const [docType, setDocType] = useState<DocumentJob['docType']>('INVOICE');
  const input = useRef<HTMLInputElement>(null);

  const start = (files: FileList | null) => {
    if (!files) return;
    const list = Array.from(files);
    const fresh: Item[] = list.map((file) => ({
      file, step: 0,
      error: !ACCEPT.includes(file.type) ? 'Unsupported type — PDF, PNG, JPEG or TIFF only'
        : file.size > MAX_MB * 1024 * 1024 ? `Larger than ${MAX_MB} MB` : undefined,
    }));
    setItems((prev) => [...fresh, ...prev]);
    fresh.forEach((it) => {
      if (it.error) return;
      api.upload({ file: it.file, tenantId: tenant, docType }).then((job) => {
        if (!usingMock) {
          // Real backend: show what the API returned (step 1 stores the file; extraction is step 2).
          setItems((prev) => prev.map((p) => (p.file === it.file ? { ...p, job, step: STEPS.length, real: true } : p)));
          return;
        }
        setItems((prev) => prev.map((p) => (p.file === it.file ? { ...p, job, step: 1 } : p)));
        // In the real app this is a Server-Sent Events stream from /documents/{id}/events.
        let s = 1;
        const timer = setInterval(() => {
          s += 1;
          setItems((prev) => prev.map((p) => (p.file === it.file ? { ...p, step: s } : p)));
          if (s >= STEPS.length) clearInterval(timer);
        }, 700);
      }).catch((e: Error) => {
        setItems((prev) => prev.map((p) => (p.file === it.file ? { ...p, error: e.message } : p)));
      });
    });
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Upload</h1>
          <p>Files are stored in object storage and queued; extraction runs asynchronously.</p>
        </div>
      </div>
      <div className="grid two">
        <div className="stack">
          <div
            className={`drop${over ? ' over' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setOver(true); }}
            onDragLeave={() => setOver(false)}
            onDrop={(e) => { e.preventDefault(); setOver(false); start(e.dataTransfer.files); }}
            onClick={() => input.current?.click()}
            role="button" tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && input.current?.click()}
          >
            <CloudUpload size={30} style={{ color: 'var(--accent)' }} />
            <h3>Drop invoices here or click to browse</h3>
            <div className="muted">PDF, PNG, JPEG or TIFF · up to {MAX_MB} MB each · many files at once</div>
            <input ref={input} type="file" multiple hidden accept={ACCEPT.join(',')} onChange={(e) => start(e.target.files)} />
          </div>

          {items.length > 0 && (
            <Card title="This session" pad>
              <div className="stack" style={{ gap: 14 }}>
                {items.map((it, i) => {
                  const done = it.step >= STEPS.length;
                  return (
                    <div key={i} style={{ display: 'grid', gridTemplateColumns: '22px 1fr auto', gap: 10, alignItems: 'center' }}>
                      <FileText size={18} className="muted" />
                      <div style={{ minWidth: 0 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{it.file.name}</span>
                          <span className="muted" style={{ fontSize: 12.5, whiteSpace: 'nowrap' }}>
                            {it.error ? '' : it.real && it.job ? statusLabel(it.job.status) : done ? 'Ready for review' : it.step === 0 ? 'Uploading…' : `${stageLabel[STEPS[it.step - 1]]}…`}
                          </span>
                        </div>
                        {it.error ? (
                          <div className="issue critical" style={{ fontSize: 12.5 }}>{it.error}</div>
                        ) : (
                          <div className="progress" style={{ marginTop: 6 }}><div style={{ width: `${(it.step / STEPS.length) * 100}%` }} /></div>
                        )}
                      </div>
                      {done && it.job ? <Link to={`/documents/${it.job.id}`} className="btn" style={{ padding: '4px 10px' }}>Open</Link>
                        : it.error ? <span /> : <Loader2 size={16} className="spin muted" />}
                    </div>
                  );
                })}
              </div>
            </Card>
          )}
        </div>

        <Card title="Settings for this batch">
          <div className="stack" style={{ gap: 14 }}>
            <label>
              <div className="secondary" style={{ fontSize: 12.5, marginBottom: 4 }}>Tenant</div>
              <select className="select" value={tenant} onChange={(e) => setTenant(e.target.value)}>
                <option value="acme-de">acme-de</option>
                <option value="globex-eu">globex-eu</option>
              </select>
            </label>
            <label>
              <div className="secondary" style={{ fontSize: 12.5, marginBottom: 4 }}>Document type</div>
              <select className="select" value={docType} onChange={(e) => setDocType(e.target.value as DocumentJob['docType'])}>
                <option value="INVOICE">Invoice</option>
                <option value="CREDIT_NOTE">Credit note</option>
                <option value="RECEIPT">Receipt</option>
              </select>
            </label>
            <div style={{ fontSize: 12.5, lineHeight: 1.6 }} className="secondary">
              <div style={{ display: 'flex', gap: 6 }}><CheckCircle2 size={14} style={{ color: 'var(--good)', marginTop: 3 }} />Personal data is redacted before any LLM call.</div>
              <div style={{ display: 'flex', gap: 6 }}><CheckCircle2 size={14} style={{ color: 'var(--good)', marginTop: 3 }} />Re-uploading the same file is idempotent (SHA-256 dedupe).</div>
              <div style={{ display: 'flex', gap: 6 }}><CheckCircle2 size={14} style={{ color: 'var(--good)', marginTop: 3 }} />Fields below 85% confidence go to the review queue.</div>
            </div>
          </div>
        </Card>
      </div>
    </>
  );
}
