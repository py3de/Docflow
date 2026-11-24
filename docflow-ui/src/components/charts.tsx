import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [w, setW] = useState(600);
  useLayoutEffect(() => {
    if (!ref.current) return;
    const ro = new ResizeObserver(([e]) => setW(Math.max(260, e.contentRect.width)));
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, []);
  return [ref, w] as const;
}

function niceMax(v: number) {
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * p;
}

/** Path for a bar whose top corners are rounded (data end) and bottom is square (baseline). */
function topRoundedBar(x: number, y: number, w: number, h: number, r: number) {
  if (h <= 0) return '';
  const rr = Math.min(r, h, w / 2);
  return `M${x},${y + h}V${y + rr}Q${x},${y} ${x + rr},${y}H${x + w - rr}Q${x + w},${y} ${x + w},${y + rr}V${y + h}Z`;
}

export interface StackSeries { key: string; label: string; color: string }

export function StackedBarChart<T extends object>({
  data, series, xKey, xFormat, height = 240, tooltipExtra,
}: {
  data: T[];
  series: StackSeries[];
  xKey: keyof T;
  xFormat: (v: string) => string;
  height?: number;
  tooltipExtra?: (d: T) => ReactNode;
}) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const m = { t: 8, r: 8, b: 24, l: 40 };
  const iw = width - m.l - m.r;
  const ih = height - m.t - m.b;
  const totals = data.map((d) => series.reduce((s, se) => s + Number(d[se.key as keyof T]), 0));
  const max = niceMax(Math.max(...totals));
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * max);
  const band = iw / data.length;
  const bw = Math.min(28, band * 0.62);
  const y = (v: number) => ih - (v / max) * ih;
  const gap = 2;

  return (
    <div className="chart" ref={ref}>
      <div className="legend">
        {series.map((s) => (
          <span key={s.key}><i className="swatch" style={{ background: s.color }} />{s.label}</span>
        ))}
      </div>
      <svg width={width} height={height} role="img" aria-label="Stacked bar chart">
        <g transform={`translate(${m.l},${m.t})`}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={0} x2={iw} y1={y(t)} y2={y(t)} stroke="var(--grid)" />
              <text x={-8} y={y(t)} dy="0.32em" textAnchor="end" className="axis-label">{t >= 1000 ? `${t / 1000}k` : t}</text>
            </g>
          ))}
          {data.map((d, i) => {
            const cx = i * band + band / 2;
            let acc = 0;
            const segs = series.map((s, si) => {
              const v = Number(d[s.key as keyof T]);
              const y0 = y(acc);
              acc += v;
              const y1 = y(acc);
              const isTop = si === series.length - 1;
              const h = y0 - y1 - (si > 0 ? gap : 0);
              const top = y1;
              return isTop ? (
                <path key={s.key} d={topRoundedBar(cx - bw / 2, top, bw, h, 4)} fill={s.color} />
              ) : (
                <rect key={s.key} x={cx - bw / 2} y={top} width={bw} height={Math.max(0, h)} fill={s.color} />
              );
            });
            return (
              <g key={String(d[xKey])} opacity={hover === null || hover === i ? 1 : 0.45}>
                {segs}
                {(i % 2 === data.length % 2 || data.length <= 8) && (
                  <text x={cx} y={ih + 16} textAnchor="middle" className="axis-label">{xFormat(String(d[xKey]))}</text>
                )}
                <rect
                  x={i * band} y={0} width={band} height={ih} fill="transparent"
                  onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}
                />
              </g>
            );
          })}
          <line x1={0} x2={iw} y1={ih} y2={ih} stroke="var(--border-strong)" />
        </g>
      </svg>
      {hover !== null && (
        <div className="tooltip" style={{ left: m.l + hover * band + band / 2, top: m.t + 28 + y(totals[hover]) }}>
          <div className="t-title">{xFormat(String(data[hover][xKey]))}</div>
          {[...series].reverse().map((s) => (
            <div className="t-row" key={s.key}>
              <span><i className="swatch" style={{ background: s.color }} />{s.label}</span>
              <b className="num">{Number(data[hover][s.key as keyof T]).toLocaleString('en-GB')}</b>
            </div>
          ))}
          {tooltipExtra?.(data[hover])}
        </div>
      )}
    </div>
  );
}

export function LineChart<T>({
  data, value, label, height = 220, threshold, thresholdLabel, yDomain, pointTone, tooltip,
}: {
  data: T[];
  value: (d: T) => number;
  label: (d: T) => string;
  height?: number;
  threshold?: number;
  thresholdLabel?: string;
  yDomain: [number, number];
  pointTone?: (d: T) => string;
  tooltip: (d: T) => ReactNode;
}) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const m = { t: 10, r: 14, b: 24, l: 40 };
  const iw = width - m.l - m.r;
  const ih = height - m.t - m.b;
  const x = (i: number) => (data.length === 1 ? iw / 2 : (i / (data.length - 1)) * iw);
  const y = (v: number) => ih - ((v - yDomain[0]) / (yDomain[1] - yDomain[0])) * ih;
  const ticks = Array.from({ length: 5 }, (_, k) => yDomain[0] + (k / 4) * (yDomain[1] - yDomain[0]));
  const path = data.map((d, i) => `${i ? 'L' : 'M'}${x(i)},${y(value(d))}`).join('');

  const onMove = (e: React.MouseEvent<SVGRectElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    const i = Math.round(((e.clientX - r.left) / r.width) * (data.length - 1));
    setHover(Math.max(0, Math.min(data.length - 1, i)));
  };

  return (
    <div className="chart" ref={ref}>
      <svg width={width} height={height} role="img" aria-label="Line chart">
        <g transform={`translate(${m.l},${m.t})`}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={0} x2={iw} y1={y(t)} y2={y(t)} stroke="var(--grid)" />
              <text x={-8} y={y(t)} dy="0.32em" textAnchor="end" className="axis-label">{t.toFixed(2)}</text>
            </g>
          ))}
          {threshold !== undefined && (
            <g>
              <line x1={0} x2={iw} y1={y(threshold)} y2={y(threshold)} stroke="var(--critical)" strokeDasharray="5 4" strokeWidth={1.5} />
              <text x={iw} y={y(threshold) - 6} textAnchor="end" className="axis-label" style={{ fill: 'var(--critical-ink)' }}>
                {thresholdLabel}
              </text>
            </g>
          )}
          {hover !== null && <line x1={x(hover)} x2={x(hover)} y1={0} y2={ih} stroke="var(--border-strong)" />}
          <path d={path} fill="none" stroke="var(--series-1)" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
          {data.map((d, i) => (
            <circle
              key={i} cx={x(i)} cy={y(value(d))} r={hover === i ? 6 : 4.5}
              fill={pointTone?.(d) ?? 'var(--series-1)'} stroke="var(--surface-1)" strokeWidth={2}
            />
          ))}
          {data.map((d, i) =>
            (() => {
              const step = Math.ceil(data.length / 8);
              const last = data.length - 1;
              return (i % step === 0 && last - i >= step) || i === last;
            })() ? (
              <text key={`l${i}`} x={x(i)} y={ih + 16} textAnchor="middle" className="axis-label">{label(d)}</text>
            ) : null,
          )}
          <rect x={-10} y={0} width={iw + 20} height={ih} fill="transparent" onMouseMove={onMove} onMouseLeave={() => setHover(null)} />
        </g>
      </svg>
      {hover !== null && (
        <div className="tooltip" style={{ left: m.l + x(hover), top: m.t + y(value(data[hover])) }}>
          {tooltip(data[hover])}
        </div>
      )}
    </div>
  );
}

export function HBarList({
  rows, max, format, color = 'var(--series-1)', labelWidth,
}: {
  rows: { label: string; value: number; note?: string }[];
  max: number;
  format: (v: number) => string;
  color?: string;
  labelWidth?: number;
}) {
  const [hover, setHover] = useState<number | null>(null);
  return (
    <div>
      {rows.map((r, i) => (
        <div
          className="hbar-row" key={r.label}
          style={labelWidth ? { gridTemplateColumns: `${labelWidth}px 1fr 64px` } : undefined}
          onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}
          title={r.note ? `${r.label}: ${format(r.value)} — ${r.note}` : `${r.label}: ${format(r.value)}`}
        >
          <span className="hbar-label">{r.label}</span>
          <span className="hbar-track">
            <span className="hbar-fill" style={{ width: `${(r.value / max) * 100}%`, display: 'block', background: color, opacity: hover === null || hover === i ? 1 : 0.5 }} />
          </span>
          <span className="hbar-value">{format(r.value)}</span>
        </div>
      ))}
    </div>
  );
}
