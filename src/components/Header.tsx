interface HeaderProps {
  portalName: string;
  apiKey: string;
  onOpenUpload: () => void;
}

function maskKey(key: string): string {
  if (key.length <= 4) return '****';
  return `${'*'.repeat(6)}${key.slice(-4)}`;
}

export function Header({ portalName, apiKey, onOpenUpload }: HeaderProps) {
  return (
    <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span aria-hidden="true" style={{ fontSize: '1.6rem' }}>&#128737;</span>
        <div>
          <div style={{ fontWeight: 700, fontSize: '1.2rem' }}>{portalName}</div>
          <div style={{ color: 'var(--muted)', fontSize: '.82rem' }}>
            cle API : <code>{maskKey(apiKey)}</code>
          </div>
        </div>
      </div>
      <button className="btn btn-primary" onClick={onOpenUpload}>Deposer des fichiers</button>
    </header>
  );
}
