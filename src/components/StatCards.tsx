import type { CSSProperties } from 'react';
import type { StatsView } from '../api/types';

interface CardDef {
  key: keyof StatsView;
  label: string;
  bar?: string;
  valueColor?: string;
}

const CARDS: CardDef[] = [
  { key: 'total', label: 'Total fichiers' },
  { key: 'clean', label: 'Validés', bar: '#28A15E', valueColor: '#1F7A46' },
  { key: 'scanning', label: 'En analyse', bar: '#2C8BD8', valueColor: '#1D6FB0' },
  { key: 'blocked', label: 'Bloqués', bar: '#D14B3E', valueColor: '#B23B30' },
];

const cardBase: CSSProperties = {
  background: '#fff',
  border: '1px solid #E1E7EE',
  borderRadius: 12,
  padding: '18px 20px',
};

export function StatCards({ stats }: { stats?: StatsView }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 16 }}>
      {CARDS.map((c) => (
        <div
          key={c.key}
          style={c.bar ? { ...cardBase, position: 'relative', overflow: 'hidden' } : cardBase}
        >
          {c.bar && (
            <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 3, background: c.bar }} />
          )}
          <div style={{ fontSize: 12.5, color: '#667586', fontWeight: 500 }}>{c.label}</div>
          <div
            style={{
              fontSize: 30,
              fontWeight: 700,
              marginTop: 6,
              letterSpacing: '-.02em',
              color: c.valueColor,
            }}
          >
            {stats ? stats[c.key] : '—'}
          </div>
        </div>
      ))}
    </div>
  );
}
