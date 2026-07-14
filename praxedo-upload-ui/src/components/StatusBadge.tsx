import type { CSSProperties } from 'react';
import type { FileStatus } from '../api/types';
import { statusPresentation, badgeColors } from '../lib/format';

export function StatusBadge({ status }: { status: FileStatus }) {
  const { label, tone } = statusPresentation(status);
  const c = badgeColors[tone];

  const dotStyle: CSSProperties = {
    width: 6,
    height: 6,
    borderRadius: '50%',
    background: c.dot,
    flexShrink: 0,
    ...(c.pulse
      ? { boxShadow: '0 0 0 3px rgba(44,139,216,.15)', animation: 'spin 1s linear infinite' }
      : {}),
  };

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '4px 10px',
        borderRadius: 999,
        fontSize: 11.5,
        fontWeight: 600,
        background: c.bg,
        color: c.fg,
      }}
    >
      <span aria-hidden="true" style={dotStyle} />
      {label}
    </span>
  );
}
