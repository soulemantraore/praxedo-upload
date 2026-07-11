interface HeaderProps {
  portalName: string;
  clientName: string;
  clientOrg: string;
  onOpenUpload: () => void;
}

function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0] ?? '')
    .join('')
    .toUpperCase();
}

export function Header({ portalName, clientName, clientOrg, onOpenUpload }: HeaderProps) {
  return (
    <header style={{ background: '#fff', borderBottom: '1px solid #E1E7EE' }}>
      <div
        style={{
          maxWidth: 1080,
          margin: '0 auto',
          padding: '0 28px',
          height: 66,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 9,
              background: '#005EA8',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 2 4 5v6c0 5 3.5 8.5 8 11 4.5-2.5 8-6 8-11V5l-8-3Z" />
              <path d="m9 12 2 2 4-4" />
            </svg>
          </div>
          <div style={{ lineHeight: 1.15 }}>
            <div style={{ fontWeight: 700, fontSize: 15.5, letterSpacing: '-.01em', color: '#16232F' }}>{portalName}</div>
            <div style={{ fontSize: 11.5, color: '#8A96A4', fontWeight: 500 }}>Fichiers sécurisés</div>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <button
            onClick={onOpenUpload}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              background: '#005EA8',
              color: '#fff',
              border: 'none',
              borderRadius: 9,
              padding: '10px 18px',
              font: 'inherit',
              fontSize: 13.5,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 5v14" />
              <path d="M5 12h14" />
            </svg>
            Déposer un fichier
          </button>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 9,
              paddingLeft: 14,
              borderLeft: '1px solid #E1E7EE',
            }}
          >
            <div
              style={{
                width: 34,
                height: 34,
                borderRadius: '50%',
                background: '#005EA8',
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 13,
                fontWeight: 600,
              }}
            >
              {initials(clientName)}
            </div>
            <div style={{ lineHeight: 1.15 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#16232F' }}>{clientName}</div>
              <div style={{ fontSize: 11, color: '#8A96A4' }}>{clientOrg}</div>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
