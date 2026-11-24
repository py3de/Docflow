import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search } from 'lucide-react';
import { api } from '../api/client';
import type { DocStatus } from '../types';
import { Card, Confidence, Empty, Skeleton, StatusBadge, fmtEur, fmtTime, useAsync } from '../components/ui';

const FILTERS: { key: string; label: string; match: (s: DocStatus) => boolean }[] = [
  { key: 'all', label: 'All', match: () => true },
  { key: 'review', label: 'Needs review', match: (s) => s === 'NEEDS_REVIEW' },
  { key: 'done', label: 'Completed', match: (s) => s === 'MATCHED' || s === 'APPROVED' },
  { key: 'flight', label: 'In progress', match: (s) => s === 'RECEIVED' || s === 'EXTRACTING' || s === 'VALIDATING' },
  { key: 'failed', label: 'Failed / rejected', match: (s) => s === 'FAILED' || s === 'REJECTED' },
];

export function Documents({ reviewOnly = false }: { reviewOnly?: boolean }) {
  const { data, loading } = useAsync(() => api.listDocuments());
  const [filter, setFilter] = useState(reviewOnly ? 'review' : 'all');
  const [q, setQ] = useState('');
  const nav = useNavigate();

  const rows = useMemo(() => {
    const f = FILTERS.find((x) => x.key === filter)!;
    const needle = q.trim().toLowerCase();
    return (data ?? [])
      .filter((d) => f.match(d.status))
      .filter((d) => !needle || `${d.vendor} ${d.fileName} ${d.id}`.toLowerCase().includes(needle))
      .sort((a, b) => (reviewOnly ? a.avgConfidence - b.avgConfidence : 0));
  }, [data, filter, q, reviewOnly]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{reviewOnly ? 'Review queue' : 'Documents'}</h1>
          <p>
            {reviewOnly
              ? 'Documents with low-confidence fields or failed validation rules, lowest confidence first.'
              : 'Every document the pipeline has received, with its extraction result.'}
          </p>
        </div>
      </div>

      <div className="toolbar" style={{ marginBottom: 12 }}>
        {!reviewOnly && (
          <div className="seg" role="tablist">
            {FILTERS.map((f) => (
              <button key={f.key} className={filter === f.key ? 'on' : ''} onClick={() => setFilter(f.key)}>
                {f.label}
                <span className="muted num" style={{ marginLeft: 6 }}>{(data ?? []).filter((d) => f.match(d.status)).length}</span>
              </button>
            ))}
          </div>
        )}
        <div style={{ position: 'relative', flex: '0 1 300px', marginLeft: 'auto' }}>
          <Search size={15} style={{ position: 'absolute', left: 10, top: 10, color: 'var(--text-muted)' }} />
          <input className="input" style={{ paddingLeft: 32 }} placeholder="Search vendor, file or ID" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
      </div>

      <Card pad={false}>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Received</th><th>Vendor</th><th>File</th><th>Status</th><th>Confidence</th>
                <th>Model</th><th className="right">Tokens</th><th className="right">Gross</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((d) => (
                <tr key={d.id} className="clickable" onClick={() => nav(`/documents/${d.id}`)}>
                  <td className="num secondary">{fmtTime(d.receivedAt)}</td>
                  <td>{d.vendor}<div className="muted" style={{ fontSize: 12 }}>{d.tenantId}</div></td>
                  <td className="mono secondary">{d.fileName}</td>
                  <td><StatusBadge status={d.status} /></td>
                  <td><Confidence value={d.avgConfidence} /></td>
                  <td className="mono secondary">{d.model}</td>
                  <td className="right num secondary">{d.tokens.toLocaleString('en-GB')}</td>
                  <td className="right num">{fmtEur(d.grossAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {loading && !data && <div style={{ padding: 16 }}><Skeleton h={240} /></div>}
          {data && rows.length === 0 && <Empty>{reviewOnly ? 'Review queue is empty. Nice work.' : 'No documents match.'}</Empty>}
        </div>
      </Card>
    </>
  );
}
