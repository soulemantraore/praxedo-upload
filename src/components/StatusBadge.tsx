import type { FileStatus } from '../api/types';
import { statusPresentation } from '../lib/format';

export function StatusBadge({ status }: { status: FileStatus }) {
  const { label, tone } = statusPresentation(status);
  return (
    <span className={`badge ${tone}`}>
      {status === 'SCANNING' && <span className="spin" aria-hidden="true" />}
      {label}
    </span>
  );
}
