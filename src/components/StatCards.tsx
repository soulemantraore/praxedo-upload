import type { StatsView } from '../api/types';

const CARDS: { key: keyof StatsView; label: string; tone: string }[] = [
  { key: 'total', label: 'Total', tone: '' },
  { key: 'clean', label: 'Valides', tone: 'clean' },
  { key: 'scanning', label: 'En analyse', tone: 'scanning' },
  { key: 'blocked', label: 'Bloques', tone: 'blocked' },
];

export function StatCards({ stats }: { stats?: StatsView }) {
  return (
    <div className="stat-grid">
      {CARDS.map((c) => (
        <div key={c.key} className="card stat">
          <div className={`value ${c.tone}`} style={c.tone ? { color: `var(--${c.tone}-fg)` } : undefined}>
            {stats ? stats[c.key] : '-'}
          </div>
          <div className="label">{c.label}</div>
        </div>
      ))}
    </div>
  );
}
