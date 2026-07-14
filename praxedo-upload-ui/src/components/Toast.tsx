export function Toast({ message }: { message: string }) {
  if (!message) return null;
  return (
    <div
      style={{
        position: 'fixed',
        bottom: 24,
        left: '50%',
        transform: 'translateX(-50%)',
        background: '#16232F',
        color: '#fff',
        padding: '13px 20px',
        borderRadius: 10,
        fontSize: 13.5,
        fontWeight: 500,
        boxShadow: '0 8px 30px rgba(0,0,0,.22)',
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        animation: 'fadeUp .25s ease both',
        zIndex: 70,
      }}
    >
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="#28A15E" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M20 6 9 17l-5-5" />
      </svg>
      {message}
    </div>
  );
}
