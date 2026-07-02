import { useLayoutEffect, useRef, useState } from 'react';

/* Shared chart primitives. Colors come from --chart-* tokens (validated for both
   themes); text always wears text tokens, never the series color. */

function useWidth() {
  const ref = useRef(null);
  const [width, setWidth] = useState(0);
  useLayoutEffect(() => {
    if (!ref.current) return;
    const ro = new ResizeObserver((entries) => setWidth(entries[0].contentRect.width));
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, []);
  return [ref, width];
}

function Tooltip({ tip }) {
  if (!tip) return null;
  const style = { left: tip.x, top: tip.y, transform: `translate(${tip.align === 'right' ? '-100%' : '12px'}, -50%)` };
  return (
    <div className="chart-tooltip" style={style} role="status">
      {tip.title && <div className="tt-title">{tip.title}</div>}
      {tip.rows.map((r) => (
        <div className="tt-row" key={r.label}>
          <span className="tt-key" style={{ background: r.color }} />
          <strong>{r.value}</strong>
          <span className="tt-label">{r.label}</span>
        </div>
      ))}
    </div>
  );
}

export function Legend({ items, lines }) {
  return (
    <div className="chart-legend">
      {items.map((it) => (
        <span key={it.label} className="chart-legend-item">
          <span className={lines ? 'chart-key-line' : 'chart-key-rect'} style={{ background: it.color }} />
          {it.label}
          {it.value != null && <span className="chart-legend-value">{it.value}</span>}
        </span>
      ))}
    </div>
  );
}

function niceTicks(max) {
  if (max <= 0) return [0, 1];
  const step = Math.max(1, Math.ceil(max / 4));
  const rounded = step <= 2 ? step : step <= 5 ? 5 : Math.ceil(step / 5) * 5;
  const ticks = [];
  for (let v = 0; v <= max + rounded - 1; v += rounded) ticks.push(v);
  return ticks;
}

/* ---------- Line chart: staffing trend (2 series, crosshair + tooltip) ---------- */

export function TrendChart({ points, series }) {
  const [ref, width] = useWidth();
  const [hover, setHover] = useState(null); // index
  const height = 210;
  const m = { top: 14, right: 44, bottom: 26, left: 34 };

  const max = Math.max(1, ...points.flatMap((p) => series.map((s) => p[s.key])));
  const ticks = niceTicks(max);
  const yMax = ticks[ticks.length - 1];
  const iw = Math.max(0, width - m.left - m.right);
  const ih = height - m.top - m.bottom;
  const x = (i) => m.left + (points.length === 1 ? iw / 2 : (i / (points.length - 1)) * iw);
  const y = (v) => m.top + ih - (v / yMax) * ih;

  const onMove = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const idx = Math.round(((px - m.left) / Math.max(1, iw)) * (points.length - 1));
    setHover(Math.max(0, Math.min(points.length - 1, idx)));
  };

  const tip = hover == null ? null : {
    x: x(hover),
    y: m.top + 10,
    align: hover > points.length / 2 ? 'right' : 'left',
    title: points[hover].month,
    rows: series.map((s) => ({ label: s.label, value: points[hover][s.key], color: s.color })),
  };

  return (
    <div className="chart-box" ref={ref}>
      {width > 0 && (
        <svg width={width} height={height} role="img" aria-label={`Staffing trend: ${series.map((s) => s.label).join(' and ')} over ${points.length} months`}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={m.left} x2={width - m.right} y1={y(t)} y2={y(t)} className="chart-grid" />
              <text x={m.left - 8} y={y(t) + 4} textAnchor="end" className="chart-tick">{t}</text>
            </g>
          ))}
          {points.map((p, i) => (
            <text key={p.month} x={x(i)} y={height - 6} textAnchor="middle" className="chart-tick">{p.month}</text>
          ))}
          {hover != null && (
            <line x1={x(hover)} x2={x(hover)} y1={m.top} y2={m.top + ih} className="chart-crosshair" />
          )}
          {series.map((s) => {
            const d = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(p[s.key])}`).join(' ');
            const last = points.length - 1;
            return (
              <g key={s.key}>
                <path d={d} fill="none" stroke={s.color} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
                {points.map((p, i) => (
                  <circle
                    key={i}
                    cx={x(i)}
                    cy={y(p[s.key])}
                    r={i === last || i === hover ? 4.5 : 3.5}
                    fill={s.color}
                    stroke="var(--color-surface)"
                    strokeWidth="2"
                  />
                ))}
                <text x={x(last) + 10} y={y(points[last][s.key]) + 4} className="chart-endlabel">
                  {points[last][s.key]}
                </text>
              </g>
            );
          })}
          <rect
            x={m.left} y={m.top} width={iw} height={ih} fill="transparent"
            onPointerMove={onMove} onPointerLeave={() => setHover(null)}
          />
        </svg>
      )}
      <Tooltip tip={tip} />
      <Legend lines items={series.map((s) => ({ label: s.label, color: s.color }))} />
      <table className="sr-only">
        <caption>Staffing trend by month</caption>
        <thead><tr><th>Month</th>{series.map((s) => <th key={s.key}>{s.label}</th>)}</tr></thead>
        <tbody>
          {points.map((p) => (
            <tr key={p.month}><td>{p.month}</td>{series.map((s) => <td key={s.key}>{p[s.key]}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ---------- Part-to-whole: single horizontal stacked bar ---------- */

export function StackedBar({ segments, total }) {
  const [ref, width] = useWidth();
  const [tip, setTip] = useState(null);
  const height = 20;
  const sum = total || segments.reduce((a, s) => a + s.value, 0);
  const visible = segments.filter((s) => s.value > 0);

  let cursor = 0;
  const rects = visible.map((s, i) => {
    const w = sum > 0 ? (s.value / sum) * width : 0;
    const seg = { ...s, x: cursor, w: Math.max(0, w - (i < visible.length - 1 ? 2 : 0)) };
    cursor += w;
    return seg;
  });

  return (
    <div className="chart-box" ref={ref}>
      {width > 0 && (
        <svg width={width} height={height} role="img"
             aria-label={segments.map((s) => `${s.label}: ${s.value}`).join(', ')}>
          <defs>
            <clipPath id="stack-clip">
              <rect x="0" y="0" width={width} height={height} rx="6" />
            </clipPath>
          </defs>
          <g clipPath="url(#stack-clip)">
            <rect x="0" y="0" width={width} height={height} fill="var(--color-muted)" />
            {rects.map((s) => (
              <rect
                key={s.label}
                x={s.x} y="0" width={s.w} height={height} fill={s.color}
                opacity={tip && tip.title !== s.label ? 0.55 : 1}
                onPointerMove={(e) => {
                  const box = e.currentTarget.closest('.chart-box').getBoundingClientRect();
                  setTip({
                    x: e.clientX - box.left, y: e.clientY - box.top - 14,
                    align: e.clientX - box.left > width * 0.6 ? 'right' : 'left',
                    title: s.label,
                    rows: [{ label: sum ? `${Math.round((s.value / sum) * 100)}% of ${sum}` : '', value: s.value, color: s.color }],
                  });
                }}
                onPointerLeave={() => setTip(null)}
              />
            ))}
          </g>
          {rects.map((s) =>
            s.w > 46 ? (
              <text key={s.label} x={s.x + s.w / 2} y={height / 2 + 4} textAnchor="middle"
                    className="chart-seglabel">
                {Math.round((s.value / sum) * 100)}%
              </text>
            ) : null
          )}
        </svg>
      )}
      <Tooltip tip={tip} />
      <Legend items={segments.map((s) => ({ label: s.label, color: s.color, value: s.value }))} />
    </div>
  );
}

/* ---------- Horizontal bar chart: magnitude comparison, single hue ---------- */

export function HBarChart({ rows, color, unit }) {
  const [ref, width] = useWidth();
  const [tip, setTip] = useState(null);
  const barH = 18;
  const rowH = 34;
  const m = { left: 128, right: 52 };
  const height = rows.length * rowH + 8;
  const max = Math.max(1, ...rows.map((r) => r.value));
  const ticks = niceTicks(max);
  const xMax = ticks[ticks.length - 1];
  const iw = Math.max(0, width - m.left - m.right);
  const w = (v) => (v / xMax) * iw;

  // rounded data-end, square baseline
  const barPath = (v, yTop) => {
    const bw = Math.max(4, w(v));
    const r = 4;
    return `M${m.left},${yTop} h${bw - r} a${r},${r} 0 0 1 ${r},${r} v${barH - 2 * r} a${r},${r} 0 0 1 ${-r},${r} h${-(bw - r)} Z`;
  };

  return (
    <div className="chart-box" ref={ref}>
      {width > 0 && (
        <svg width={width} height={height} role="img" aria-label={rows.map((r) => `${r.label}: ${r.value}`).join(', ')}>
          {ticks.map((t) => (
            <line key={t} x1={m.left + w(t)} x2={m.left + w(t)} y1="0" y2={height - 4} className="chart-grid" />
          ))}
          {rows.map((r, i) => {
            const yTop = i * rowH + (rowH - barH) / 2;
            const hovered = tip && tip.title === r.label;
            return (
              <g key={r.label}
                 onPointerMove={(e) => {
                   const box = e.currentTarget.closest('.chart-box').getBoundingClientRect();
                   setTip({
                     x: e.clientX - box.left, y: e.clientY - box.top - 12,
                     align: e.clientX - box.left > width * 0.6 ? 'right' : 'left',
                     title: r.label,
                     rows: [{ label: unit, value: r.value, color }],
                   });
                 }}
                 onPointerLeave={() => setTip(null)}>
                <rect x="0" y={i * rowH} width={width} height={rowH} fill="transparent" />
                <text x={m.left - 10} y={yTop + barH / 2 + 4} textAnchor="end" className="chart-cat">{r.label}</text>
                <path d={barPath(r.value, yTop)} fill={color} opacity={tip && !hovered ? 0.6 : 1} />
                <text x={m.left + w(r.value) + 8} y={yTop + barH / 2 + 4} className="chart-endlabel">{r.value}</text>
              </g>
            );
          })}
        </svg>
      )}
      <Tooltip tip={tip} />
    </div>
  );
}
