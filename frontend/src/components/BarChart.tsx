interface Props {
  labels: string[];
  values: number[];
  ariaLabel: string;
  height?: number;
}

/** Dependency-free responsive SVG bar chart. */
export default function BarChart({ labels, values, ariaLabel, height = 180 }: Props) {
  const max = Math.max(1, ...values);
  const w = 600;
  const pad = 24;
  const bw = (w - pad * 2) / Math.max(1, values.length);
  return (
    <svg viewBox={`0 0 ${w} ${height + 28}`} role="img" aria-label={ariaLabel} className="chart">
      <line x1={pad} y1={height} x2={w - pad} y2={height} className="chart__axis" />
      {values.map((v, i) => {
        const h = (v / max) * (height - 20);
        const x = pad + i * bw + bw * 0.15;
        return (
          <g key={labels[i] + i}>
            <title>{`${labels[i]}: ${v}`}</title>
            <rect x={x} y={height - h} width={bw * 0.7} height={h} rx={2} className="chart__bar" />
            {values.length <= 16 && (
              <text x={x + (bw * 0.7) / 2} y={height - h - 4} textAnchor="middle" fontSize="10" className="chart__value">
                {v > 0 ? v : ''}
              </text>
            )}
            {(values.length <= 10 || i % Math.ceil(values.length / 8) === 0) && (
              <text x={x + (bw * 0.7) / 2} y={height + 16} textAnchor="middle" fontSize="10" className="chart__label">
                {labels[i].slice(5)}
              </text>
            )}
          </g>
        );
      })}
    </svg>
  );
}
