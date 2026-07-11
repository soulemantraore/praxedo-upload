import type { CSSProperties, ReactNode } from 'react';
import { formatBytes, formatDateLong, fileExtMeta } from '../lib/format';
import { useRescan, api } from '../api/hooks';
import type { FileStatus, FileView } from '../api/types';

interface Props {
  file: FileView;
  onClose: () => void;
  onToast?: (msg: string) => void;
}

interface VerdictConf {
  border: string;
  bg: string;
  iconBg: string;
  color: string;
  title: string;
  text: string;
  glyph?: string;
  spinner?: boolean;
}

function verdictConf(status: FileStatus): VerdictConf {
  switch (status) {
    case 'CLEAN':
      return {
        border: '#BFE3CC', bg: '#EEF8F1', iconBg: '#28A15E', color: '#1F7A46',
        title: 'Fichier validé', text: 'Aucune menace détectée — téléchargement sécurisé.', glyph: '✓',
      };
    case 'INFECTED':
      return {
        border: '#F1C7C1', bg: '#FCEEEC', iconBg: '#D14B3E', color: '#B23B30',
        title: 'Menace détectée', text: 'Fichier mis en quarantaine, téléchargement interdit.', glyph: '✕',
      };
    case 'SCAN_FAILED':
      return {
        border: '#DEE3E9', bg: '#F5F7F9', iconBg: '#7A8794', color: '#5A6674',
        title: 'Analyse échouée', text: "L'analyse n'a pas abouti. Relancez le scan.", glyph: '!',
      };
    case 'EXPIRED':
      return {
        border: '#DEE3E9', bg: '#F5F7F9', iconBg: '#7A8794', color: '#5A6674',
        title: 'Expiré', text: "L'analyse n'a pas abouti. Relancez le scan.", glyph: '!',
      };
    default: // SCANNING or PENDING
      return {
        border: '#CFE0EE', bg: '#EFF5FA', iconBg: '#2C8BD8', color: '#1D6FB0',
        title: status === 'PENDING' ? "En file d'attente" : 'Analyse en cours',
        text: 'Analyse antivirus du contenu en cours…', spinner: true,
      };
  }
}

interface TimelineStep {
  label: string;
  detail: string;
  color: string;
  done: boolean;
  last?: boolean;
}

function timeline(file: FileView): TimelineStep[] {
  const { status, createdAt } = file;
  const scanning = status === 'SCANNING' || status === 'PENDING';

  let final: TimelineStep;
  if (status === 'CLEAN') {
    final = { label: 'Validé — téléchargeable', detail: 'Disponible', color: '#28A15E', done: true, last: true };
  } else if (status === 'INFECTED') {
    final = { label: 'Bloqué — quarantaine', detail: 'Menace confirmée', color: '#D14B3E', done: true, last: true };
  } else if (status === 'SCAN_FAILED') {
    final = { label: 'Erreur', detail: 'À relancer', color: '#7A8794', done: true, last: true };
  } else {
    final = { label: 'Décision', detail: 'En attente', color: '#D0D7DE', done: false, last: true };
  }

  return [
    { label: 'Fichier reçu', detail: formatDateLong(createdAt), color: '#28A15E', done: true },
    { label: "Mis en file d'attente", detail: "Ajouté à la file d'analyse", color: status === 'PENDING' ? '#C99A2E' : '#28A15E', done: status !== 'PENDING' },
    { label: 'Analyse antivirus', detail: scanning ? 'En cours…' : 'Terminée', color: scanning ? (status === 'SCANNING' ? '#2C8BD8' : '#D0D7DE') : '#28A15E', done: !scanning },
    final,
  ];
}

function dlHint(status: FileStatus): string {
  switch (status) {
    case 'CLEAN': return 'Le fichier a été validé par l’antivirus.';
    case 'INFECTED': return 'Téléchargement interdit : une menace a été détectée.';
    case 'SCANNING':
    case 'PENDING': return 'Disponible une fois l’analyse terminée.';
    default: return 'Relancez l’analyse pour rendre le fichier téléchargeable.';
  }
}

export function FileReportModal({ file, onClose, onToast }: Props) {
  const rescan = useRescan();
  const { status } = file;
  const ext = fileExtMeta(file.filename);
  const v = verdictConf(status);
  const scanningState = status === 'SCANNING' || status === 'PENDING';

  const threatValue =
    status === 'INFECTED'
      ? file.scanVerdict?.threatName ?? 'inconnue'
      : scanningState ? '—' : 'Aucune';
  const threatColor = status === 'INFECTED' ? '#B23B30' : '#16232F';

  const metaRow = (label: string, value: ReactNode, valueColor: string, last = false) => (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        padding: '11px 0',
        borderBottom: last ? 'none' : '1px solid #F1F4F8',
        fontSize: 13,
      }}
    >
      <span style={{ color: '#667586' }}>{label}</span>
      <span style={{ fontWeight: 500, color: valueColor }}>{value}</span>
    </div>
  );

  const closeBtn = (
    <button
      onClick={onClose}
      aria-label="Fermer"
      style={{
        width: 32, height: 32, borderRadius: 8, border: 'none', background: '#F0F4F8',
        color: '#667586', cursor: 'pointer', display: 'flex', alignItems: 'center',
        justifyContent: 'center', flexShrink: 0,
      }}
    >
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18" /><path d="m6 6 12 12" /></svg>
    </button>
  );

  const chipBase: CSSProperties = {
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 9,
    width: '100%', borderRadius: 10, padding: 12, fontSize: 13, fontWeight: 600,
  };
  const btnBase: CSSProperties = {
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 9,
    width: '100%', color: '#fff', border: 'none', borderRadius: 10, padding: 12,
    font: 'inherit', fontSize: 14, fontWeight: 600, cursor: 'pointer',
  };

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(16,35,51,.5)', display: 'flex',
        alignItems: 'flex-start', justifyContent: 'center', padding: '32px 24px',
        zIndex: 60, overflowY: 'auto', animation: 'overlayIn .18s ease both',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Rapport antivirus"
        style={{
          width: 760, maxWidth: '100%', background: '#fff', borderRadius: 16,
          overflow: 'hidden', boxShadow: '0 24px 70px rgba(0,0,0,.28)',
          animation: 'popIn .2s ease both',
        }}
      >
        {/* Header */}
        <div
          style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            gap: 14, padding: '20px 24px', borderBottom: '1px solid #EEF2F6',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, minWidth: 0 }}>
            <div
              style={{
                width: 46, height: 46, borderRadius: 10, background: ext.bg, display: 'flex',
                alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontSize: 11,
                fontWeight: 700, color: ext.color, fontFamily: "'IBM Plex Mono',monospace",
              }}
            >
              {ext.ext}
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 17, fontWeight: 600, letterSpacing: '-.01em', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {file.filename}
              </div>
              <div style={{ fontSize: 12.5, color: '#667586', marginTop: 2, fontFamily: "'IBM Plex Mono',monospace" }}>
                {formatBytes(file.size)} · {formatDateLong(file.createdAt)}
              </div>
            </div>
          </div>
          {closeBtn}
        </div>

        {/* Body */}
        <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: 16, padding: 24 }}>
          {/* LEFT */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, minWidth: 0 }}>
            {/* Verdict card */}
            <div style={{ border: `1px solid ${v.border}`, background: v.bg, borderRadius: 12, padding: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <div
                  style={{
                    width: 44, height: 44, borderRadius: '50%', background: v.iconBg, display: 'flex',
                    alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                  }}
                >
                  {v.spinner ? (
                    <span
                      aria-hidden="true"
                      style={{
                        width: 22, height: 22, borderRadius: '50%',
                        border: '2.5px solid rgba(255,255,255,.4)', borderTopColor: '#fff',
                        animation: 'spin .8s linear infinite',
                      }}
                    />
                  ) : (
                    <span style={{ color: '#fff', fontSize: 20, fontWeight: 700, lineHeight: 1 }}>{v.glyph}</span>
                  )}
                </div>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: v.color }}>{v.title}</div>
                  <div style={{ fontSize: 12.5, color: v.color, opacity: 0.85, marginTop: 2 }}>{v.text}</div>
                </div>
              </div>
            </div>

            {/* Metadata card */}
            <div style={{ background: '#fff', border: '1px solid #E1E7EE', borderRadius: 12, padding: '4px 18px' }}>
              {metaRow('Moteur antivirus', file.scanVerdict?.engine ?? '—', '#16232F')}
              {metaRow('Base de signatures', '—', '#16232F')}
              {metaRow("Durée d'analyse", '—', '#16232F')}
              {metaRow('Menace détectée', threatValue, threatColor, true)}
            </div>

            {/* SHA-256 card */}
            {file.sha256 && (
              <div style={{ background: '#F7F9FB', border: '1px solid #EEF2F6', borderRadius: 12, padding: '14px 16px' }}>
                <div style={{ fontSize: 11, color: '#667586', marginBottom: 5, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.04em' }}>
                  Empreinte SHA-256
                </div>
                <div style={{ fontFamily: "'IBM Plex Mono',monospace", fontSize: 11, lineHeight: 1.6, color: '#16232F', wordBreak: 'break-all' }}>
                  {file.sha256}
                </div>
              </div>
            )}
          </div>

          {/* RIGHT */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {/* Download card */}
            <div style={{ background: '#fff', border: '1px solid #E1E7EE', borderRadius: 12, padding: 18 }}>
              <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Téléchargement</div>
              <div style={{ fontSize: 12.5, color: '#667586', lineHeight: 1.5, marginBottom: 14 }}>{dlHint(status)}</div>

              {status === 'CLEAN' && (
                <button
                  onClick={() => {
                    void api.downloadFile(file.id, file.filename);
                    onToast?.('Téléchargement démarré : ' + file.filename);
                  }}
                  style={{ ...btnBase, background: '#1F9254' }}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><path d="M7 10l5 5 5-5" /><path d="M12 15V3" /></svg>
                  Télécharger
                </button>
              )}

              {status === 'INFECTED' && (
                <div style={{ ...chipBase, background: '#FBEBE9', color: '#B23B30' }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#B23B30" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="9" /><path d="m15 9-6 6" /><path d="m9 9 6 6" /></svg>
                  Bloqué
                </div>
              )}

              {scanningState && (
                <div style={{ ...chipBase, background: '#F0F4F8', color: '#667586' }}>Analyse en cours…</div>
              )}

              {(status === 'SCAN_FAILED' || status === 'EXPIRED') && (
                <button
                  onClick={() => rescan.mutate(file.id, { onSuccess: () => onToast?.('Analyse relancée') })}
                  disabled={rescan.isPending}
                  style={{ ...btnBase, background: '#005EA8', cursor: rescan.isPending ? 'default' : 'pointer', opacity: rescan.isPending ? 0.7 : 1 }}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 12a9 9 0 0 1 15-6.7L21 8" /><path d="M21 3v5h-5" /><path d="M21 12a9 9 0 0 1-15 6.7L3 16" /><path d="M3 21v-5h5" /></svg>
                  {rescan.isPending ? 'Relance…' : "Relancer l'analyse"}
                </button>
              )}
            </div>

            {/* Cycle de vie card */}
            <div style={{ background: '#fff', border: '1px solid #E1E7EE', borderRadius: 12, padding: 18 }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: '#8A96A4', textTransform: 'uppercase', letterSpacing: '.04em', marginBottom: 14 }}>
                Cycle de vie
              </div>
              {timeline(file).map((step, i) => (
                <div key={i} style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', alignSelf: 'stretch' }}>
                    <span style={{ width: 11, height: 11, borderRadius: '50%', background: step.color, flexShrink: 0, marginTop: 2 }} />
                    {!step.last && (
                      <span style={{ width: 2, flexGrow: 1, background: step.done ? '#D3ECDD' : '#EAEEF2', marginTop: 4 }} />
                    )}
                  </div>
                  <div style={{ paddingBottom: 14 }}>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>{step.label}</div>
                    <div style={{ fontSize: 11.5, color: '#8A96A4', marginTop: 1 }}>{step.detail}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
