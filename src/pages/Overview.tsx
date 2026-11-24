import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Clock, Coins, FileCheck2, Gauge, Zap } from 'lucide-react';
import { api } from '../api/client';
import { HBarList, StackedBarChart } from '../components/charts';
import {
  Card, Confidence, Kpi, Skeleton, StatusBadge, fmtEur, fmtInt, fmtMs, fmtPct, fmtTime, stageLabel, useAsync,
} from '../components/ui';

const dayFmt = (iso: string) => new Date(iso).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });

export function Overview() {
  const overview = useAsync(() => api.getOverview());
  const docs = useAsync(() => api.listDocuments());
  const nav = useNavigate();
  const o = overview.data;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Overview</h1>
          <p>Invoice processing across the pipeline, last 14 days.</p>
        </div>
        <Link to="/upload" className="btn btn-primary">Upload documents</Link>
      </div>

      <div className="grid kpis" style={{ marginBottom: 16 }}>
        {!o ? (
          Array.from({ length: 5 }, (_, i) => <div className="card kpi" key={i}><Skeleton h={14} w="60%" /><div style={{ height: 10 }} /><Skeleton h={28} w="50%" /></div>)
        ) : (
          <>
            <Kpi icon={<FileCheck2 size={14} />} label="Documents processed (7d)" value={fmtInt(o.processed7d)}
              delta={{ value: o.processed7d - o.processedPrev7d, text: fmtPct(o.processed7d / o.processedPrev7d - 1) }} />
            <Kpi icon={<Zap size={14} />} label="Straight-through rate" value={fmtPct(o.straightThroughRate)}
              delta={{ value: o.straightThroughRate - o.straightThroughPrev, text: `${((o.straightThroughRate - o.straightThroughPrev) * 100).toFixed(1)} pp` }} />
            <Kpi icon={<Gauge size={14} />} label="Avg. field confidence" value={fmtPct(o.avgConfidence)} />
            <Kpi icon={<Clock size={14} />} label="p95 end-to-end latency" value={fmtMs(o.p95LatencyMs)} goodWhen="down"
              delta={{ value: o.p95LatencyMs - o.p95LatencyPrevMs, text: fmtPct(o.p95LatencyMs / o.p95LatencyPrevMs - 1) }} />
            <Kpi icon={<Coins size={14} />} label="LLM cost per document" value={fmtEur(o.costPerDocEur, 4)} goodWhen="down"
              delta={{ value: o.costPerDocEur - o.costPerDocPrevEur, text: fmtPct(o.costPerDocEur / o.costPerDocPrevEur - 1) }} />
          </>
        )}
      </div>

      <div className="grid two" style={{ marginBottom: 16 }}>
        <Card title="Daily volume" sub="Documents per day, by outcome">
          {o ? (
            <StackedBarChart
              data={o.daily}
              xKey="date"
              xFormat={dayFmt}
              series={[
                { key: 'straightThrough', label: 'Straight-through', color: 'var(--series-1)' },
                { key: 'humanReview', label: 'Sent to human review', color: 'var(--series-2)' },
              ]}
              tooltipExtra={(d) => (
                <div className="t-row" style={{ marginTop: 4, borderTop: '1px solid var(--border)', paddingTop: 4 }}>
                  <span>Review share</span>
                  <b className="num">{fmtPct(d.humanReview / (d.humanReview + d.straightThrough))}</b>
                </div>
              )}
            />
          ) : <Skeleton h={250} />}
        </Card>
        <Card title="Where the time goes" sub="p95 latency per pipeline stage">
          {o ? (
            <>
              <HBarList
                labelWidth={110}
                rows={o.stageP95.map((s) => ({ label: stageLabel[s.stage], value: s.ms }))}
                max={Math.max(...o.stageP95.map((s) => s.ms))}
                format={fmtMs}
              />
              <p className="muted" style={{ fontSize: 12.5, margin: '14px 0 0' }}>
                LLM extraction is {fmtPct(o.stageP95.find((s) => s.stage === 'LLM_EXTRACTION')!.ms / o.stageP95.reduce((a, s) => a + s.ms, 0), 0)} of
                total p95. Traces in Grafana Tempo via OpenTelemetry.
              </p>
            </>
          ) : <Skeleton h={200} />}
        </Card>
      </div>

      <Card
        title="Latest documents"
        sub="Most recent 8 across all tenants"
        action={<Link to="/documents" style={{ display: 'inline-flex', gap: 4, alignItems: 'center', fontSize: 13 }}>All documents <ArrowRight size={14} /></Link>}
        pad={false}
      >
        <div className="table-wrap" style={{ marginTop: 10 }}>
          <table className="table">
            <thead>
              <tr><th>Received</th><th>Vendor</th><th>Status</th><th>Confidence</th><th className="right">Gross</th></tr>
            </thead>
            <tbody>
              {(docs.data ?? []).slice(0, 8).map((d) => (
                <tr key={d.id} className="clickable" onClick={() => nav(`/documents/${d.id}`)}>
                  <td className="num secondary">{fmtTime(d.receivedAt)}</td>
                  <td>{d.vendor}</td>
                  <td><StatusBadge status={d.status} /></td>
                  <td><Confidence value={d.avgConfidence} /></td>
                  <td className="right num">{fmtEur(d.grossAmount)}</td>
                </tr>
              ))}
              {docs.loading && !docs.data && (
                <tr><td colSpan={5}><Skeleton h={120} /></td></tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
