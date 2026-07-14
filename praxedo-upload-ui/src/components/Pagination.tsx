import type { CSSProperties } from 'react';

interface PaginationProps {
  page: number; // 0-based
  totalPages: number;
  totalElements: number;
  pageSize: number;
  onPage: (p: number) => void;
}

const btnBase: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 32,
  height: 32,
  borderRadius: 8,
  border: '1px solid #E1E7EE',
  background: '#fff',
  color: '#4A5A6A',
  cursor: 'pointer',
};

function buttonStyle(disabled: boolean): CSSProperties {
  return disabled ? { ...btnBase, opacity: 0.4, cursor: 'not-allowed' } : btnBase;
}

export function Pagination({ page, totalPages, totalElements, pageSize, onPage }: PaginationProps) {
  const start = page * pageSize + 1;
  const end = Math.min((page + 1) * pageSize, totalElements);
  const noPrev = page <= 0;
  const noNext = page >= totalPages - 1;

  const rangeLabel = `${start}–${end} sur ${totalElements} fichier(s)`;
  const pageLabel = `Page ${page + 1} / ${totalPages}`;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '14px 20px',
        borderTop: '1px solid #EEF2F6',
      }}
    >
      <div style={{ fontSize: 12.5, color: '#8A96A4' }}>{rangeLabel}</div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <button
          aria-label="Précédent"
          disabled={noPrev}
          onClick={() => onPage(page - 1)}
          style={buttonStyle(noPrev)}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="m15 18-6-6 6-6" />
          </svg>
        </button>
        <span style={{ fontSize: 12.5, fontWeight: 600, color: '#4A5A6A', padding: '0 4px' }}>{pageLabel}</span>
        <button
          aria-label="Suivant"
          disabled={noNext}
          onClick={() => onPage(page + 1)}
          style={buttonStyle(noNext)}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="m9 18 6-6-6-6" />
          </svg>
        </button>
      </div>
    </div>
  );
}
