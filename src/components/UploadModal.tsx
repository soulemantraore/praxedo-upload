import { useRef, useState } from 'react';
import { Modal } from './Modal';
import { useUploadFiles } from '../api/hooks';
import { formatBytes } from '../lib/format';

export function UploadModal({ onClose }: { onClose: () => void }) {
  const [files, setFiles] = useState<File[]>([]);
  const [over, setOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const upload = useUploadFiles();

  const addFiles = (list: FileList | null) => {
    if (list) setFiles((prev) => [...prev, ...Array.from(list)]);
  };

  const submit = () => {
    if (files.length === 0) return;
    upload.mutate(files, {
      onSuccess: (res) => { if (res.errors.length === 0) onClose(); },
    });
  };

  return (
    <Modal title="Deposer des fichiers" onClose={onClose}>
      <div
        className={`dropzone ${over ? 'over' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setOver(true); }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); addFiles(e.dataTransfer.files); }}
      >
        <p>Glissez des fichiers ici</p>
        <button className="btn" onClick={() => inputRef.current?.click()}>Parcourir</button>
        <input
          ref={inputRef}
          type="file"
          multiple
          hidden
          aria-label="Choisir des fichiers"
          onChange={(e) => addFiles(e.target.files)}
        />
        <div className="label" style={{ marginTop: 8 }}>upload direct via URL signee</div>
      </div>

      {files.length > 0 && (
        <ul style={{ listStyle: 'none', padding: 0, margin: '14px 0', maxHeight: 160, overflow: 'auto' }}>
          {files.map((f, i) => (
            <li key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--border)' }}>
              <span>{f.name}</span>
              <span style={{ color: 'var(--muted)' }}>{formatBytes(f.size)}</span>
            </li>
          ))}
        </ul>
      )}

      {upload.data && upload.data.errors.length > 0 && (
        <div className="badge blocked" style={{ display: 'block', padding: 10 }}>
          {upload.data.errors.length} echec(s) : {upload.data.errors.map((e) => e.filename).join(', ')}
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 14 }}>
        <button className="btn" onClick={onClose}>Annuler</button>
        <button className="btn btn-primary" disabled={files.length === 0 || upload.isPending} onClick={submit}>
          {upload.isPending ? 'Envoi...' : `Envoyer (${files.length})`}
        </button>
      </div>
    </Modal>
  );
}
