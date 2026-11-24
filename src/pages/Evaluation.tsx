import { useState } from 'react';
import { GitCommit } from 'lucide-react';
import { api } from '../api/client';
import type { EvalRun } from '../types';
import { HBarList, LineChart } from '../components/charts';
import { Card, PassBadge, Skeleton, fmtTime, useAsync } from '../components/ui';

export function Evaluation() {
  const { data } = useAsync(() => api.listEvalRuns());
  const [selected, setSelected] = useState<string>();
  const runs = data ?? [];
  const chronological = [...runs].reverse();
  const current: EvalRun | undefined = runs.find((r) => r.id === selected) ?? runs[0];

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Model evaluation</h1>
          <p>Every pull request runs the extraction pipeline on a golden dataset. CI blocks the merge if F1 drops below the gate.</p>
        </div>
      </div>

      <div className="grid two" style={{ marginBottom: 16 }}>
        <Card title="Field-level F1 per CI run" sub={current ? `Gate at ${current.threshold.toFixed(2)} · dataset ${current.dataset}` : ''}>
          {data ? (
            <LineChart
              data={chronological}
              value={(r) => r.f1}
              label={(r) => new Date(r.startedAt).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })}
              yDomain={[0.84, 1.0]}
              threshold={current?.threshold}
              thresholdLabel={`Quality gate ${current?.threshold.toFixed(2)}`}
              pointTone={(r) => (r.passed ? 'var(--series-1)' : 'var(--critical)')}
              tooltip={(r) => (
                <>
                  <div className="t-title mono">{r.commit} · {r.branch}</div>
                  <div className="t-row"><span>F1</span><b className="num">{r.f1.toFixed(3)}</b></div>
                  <div className="t-row"><span>Model</span><span className="mono">{r.model}</span></div>
                  <div style={{ marginTop: 6 }}><PassBadge passed={r.passed} /></div>
                </>
              )}
            />
          ) : <Skeleton h={220} />}
        </Card>
        <Card title="F1 by field" sub={current ? `Run ${current.id} · commit ${current.commit}` : ''}>
          {current ? (
            <HBarList
              rows={[...current.fieldF1].sort((a, b) => a.f1 - b.f1).map((f) => ({ label: f.field, value: f.f1 }))}
              max={1}
              format={(v) => v.toFixed(2)}
            />
          ) : <Skeleton h={220} />}
        </Card>
      </div>

      <Card title="CI runs" sub="Click a run to see its field breakdown" pad={false}>
        <div className="table-wrap" style={{ marginTop: 10 }}>
          <table className="table">
            <thead>
              <tr><th>Started</th><th>Commit</th><th>Branch</th><th>Model</th><th className="right">F1</th><th>Gate</th></tr>
            </thead>
            <tbody>
              {runs.map((r) => (
                <tr key={r.id} className="clickable" onClick={() => setSelected(r.id)}
                  style={r.id === current?.id ? { background: 'var(--accent-soft)' } : undefined}>
                  <td className="num secondary">{fmtTime(r.startedAt)}</td>
                  <td className="mono"><GitCommit size={13} style={{ verticalAlign: -2, marginRight: 4 }} className="muted" />{r.commit}</td>
                  <td className="mono secondary">{r.branch}</td>
                  <td className="mono secondary">{r.model}</td>
                  <td className="right num">{r.f1.toFixed(3)}</td>
                  <td><PassBadge passed={r.passed} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
