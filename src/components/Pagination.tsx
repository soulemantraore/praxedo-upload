interface PaginationProps {
  page: number;         // 0-based
  totalPages: number;
  onPage: (p: number) => void;
}

export function Pagination({ page, totalPages, onPage }: PaginationProps) {
  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 10, padding: '12px 4px' }}>
      <button className="btn" disabled={page <= 0} onClick={() => onPage(page - 1)}>Precedent</button>
      <span style={{ color: 'var(--muted)', fontSize: '.88rem' }}>page {page + 1} / {totalPages}</span>
      <button className="btn" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>Suivant</button>
    </div>
  );
}
