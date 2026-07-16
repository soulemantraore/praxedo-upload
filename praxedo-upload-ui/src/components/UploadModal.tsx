import { useRef, useState, type CSSProperties } from 'react';
import { useUploadFiles, type UploadProgress } from '../api/hooks';
import { MAX_UPLOAD_BYTES } from '../config';

interface Props {
  onClose: () => void;
  onUploaded: (res: UploadProgress) => void;
}

export function UploadModal({ onClose, onUploaded }: Props) {
  const [dragOver, setDragOver] = useState(false);
  const [rejected, setRejected] = useState<string[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  const upload = useUploadFiles();

  const handleFiles = (list: FileList | null) => {
    if (!list || list.length === 0) return;
    const all = Array.from(list);
    const tooBig = all.filter((f) => f.size > MAX_UPLOAD_BYTES);
    const ok = all.filter((f) => f.size <= MAX_UPLOAD_BYTES);
    setRejected(tooBig.map((f) => f.name));
    if (ok.length === 0) return;
    upload.mutate(ok, {
      onSuccess: (res) => {
        onUploaded(res);
        if (res.errors.length === 0 && tooBig.length === 0) onClose();
      },
    });
  };

  const dropStyle: CSSProperties = {
    border: `2px dashed ${dragOver ? '#005EA8' : '#C3CFDB'}`,
    background: dragOver ? '#EDF4FA' : '#FBFCFE',
    borderRadius: 14,
    padding: '36px 28px',
    textAlign: 'center',
    transition: 'border-color .15s, background .15s',
  };

  const stepCard: CSSProperties = {
    flex: 1,
    background: '#F7F9FB',
    border: '1px solid #EEF2F6',
    borderRadius: 10,
    padding: '12px 14px',
  };

  const failed = upload.data?.errors ?? [];

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(16,35,51,.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
        zIndex: 60,
        animation: 'overlayIn .18s ease both',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Déposer des fichiers"
        style={{
          width: 560,
          maxWidth: '100%',
          background: '#fff',
          borderRadius: 16,
          overflow: 'hidden',
          boxShadow: '0 24px 70px rgba(0,0,0,.28)',
          animation: 'popIn .2s ease both',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '20px 24px',
            borderBottom: '1px solid #EEF2F6',
          }}
        >
          <div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>Déposer des fichiers</div>
            <div style={{ fontSize: 12.5, color: '#667586', marginTop: 2 }}>
              Un ou plusieurs fichiers · analyse antivirus automatique
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Fermer"
            style={{
              width: 32,
              height: 32,
              borderRadius: 8,
              border: 'none',
              background: '#F0F4F8',
              color: '#667586',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18" /><path d="m6 6 12 12" /></svg>
          </button>
        </div>

        <div style={{ padding: 24 }}>
          <div
            onDrop={(e) => { e.preventDefault(); setDragOver(false); handleFiles(e.dataTransfer.files); }}
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={(e) => { e.preventDefault(); setDragOver(false); }}
            style={dropStyle}
          >
            <div
              style={{
                width: 60,
                height: 60,
                borderRadius: '50%',
                background: '#E6F0F8',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 16px',
              }}
            >
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#005EA8" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><path d="M12 3v13" /><path d="m7 8 5-5 5 5" /></svg>
            </div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>Glissez vos fichiers ici</div>
            <div style={{ fontSize: 13, color: '#667586', marginTop: 5 }}>ou</div>
            <button
              onClick={() => inputRef.current?.click()}
              disabled={upload.isPending}
              style={{
                marginTop: 12,
                background: '#005EA8',
                color: '#fff',
                border: 'none',
                borderRadius: 9,
                padding: '11px 22px',
                font: 'inherit',
                fontSize: 14,
                fontWeight: 600,
                cursor: upload.isPending ? 'default' : 'pointer',
                opacity: upload.isPending ? 0.7 : 1,
              }}
            >
              {upload.isPending ? 'Envoi…' : 'Parcourir mes fichiers'}
            </button>
            <input
              ref={inputRef}
              type="file"
              multiple
              aria-label="Choisir des fichiers"
              style={{ display: 'none' }}
              onChange={(e) => { handleFiles(e.target.files); e.target.value = ''; }}
            />
            <div style={{ fontSize: 12, color: '#8A96A4', marginTop: 16 }}>
              Tous formats · jusqu'à 1 Go par fichier · sélection multiple
            </div>
          </div>

          {rejected.length > 0 && (
            <div
              role="alert"
              style={{
                marginTop: 16,
                background: '#FBEBE9',
                color: '#B23B30',
                borderRadius: 10,
                padding: '10px 14px',
                fontSize: 12.5,
                fontWeight: 600,
                lineHeight: 1.5,
              }}
            >
              {rejected.length} fichier(s) trop volumineux (max 1 Go) : {rejected.join(', ')}
            </div>
          )}

          {failed.length > 0 && (
            <div
              role="alert"
              style={{
                marginTop: 16,
                background: '#FBEBE9',
                color: '#B23B30',
                borderRadius: 10,
                padding: '10px 14px',
                fontSize: 12.5,
                fontWeight: 600,
                lineHeight: 1.5,
              }}
            >
              {failed.length} échec(s) à l'envoi : {failed.map((e) => e.filename).join(', ')}
            </div>
          )}

          <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
            <div style={stepCard}>
              <div style={{ fontSize: 18, fontWeight: 700, color: '#005EA8' }}>1</div>
              <div style={{ fontSize: 11.5, color: '#667586', marginTop: 3, lineHeight: 1.4 }}>Réception & mise en file</div>
            </div>
            <div style={stepCard}>
              <div style={{ fontSize: 18, fontWeight: 700, color: '#005EA8' }}>2</div>
              <div style={{ fontSize: 11.5, color: '#667586', marginTop: 3, lineHeight: 1.4 }}>Analyse antivirus</div>
            </div>
            <div style={stepCard}>
              <div style={{ fontSize: 18, fontWeight: 700, color: '#005EA8' }}>3</div>
              <div style={{ fontSize: 11.5, color: '#667586', marginTop: 3, lineHeight: 1.4 }}>Validé & téléchargeable</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
