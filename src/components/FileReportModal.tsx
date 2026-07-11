import type { ReactNode } from 'react';
import { Modal } from './Modal';
import { StatusBadge } from './StatusBadge';
import { formatBytes, formatDate, isDownloadable } from '../lib/format';
import { useRescan, api } from '../api/hooks';
import type { FileView } from '../api/types';

interface Props {
  file: FileView;
  onClose: () => void;
}

export function FileReportModal({ file, onClose }: Props) {
  const rescan = useRescan();
  const canRescan = file.status === 'SCAN_FAILED' || file.status === 'INFECTED' || file.status === 'CLEAN';

  const row = (label: string, value: ReactNode) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderTop: '1px solid var(--border)' }}>
      <span style={{ color: 'var(--muted)' }}>{label}</span>
      <span style={{ fontWeight: 600, textAlign: 'right' }}>{value}</span>
    </div>
  );

  return (
    <Modal title="Rapport antivirus" onClose={onClose}>
      <h3 style={{ margin: '0 0 4px', wordBreak: 'break-all' }}>{file.filename}</h3>
      <div style={{ marginBottom: 8 }}><StatusBadge status={file.status} /></div>
      {row('Taille', formatBytes(file.size))}
      {row('Type', file.contentType)}
      {row('Ajoute le', formatDate(file.createdAt))}
      {row('Moteur', file.scanVerdict?.engine ?? 'en attente de scan')}
      {row('Verdict', file.scanVerdict?.verdict ?? '-')}
      {file.status === 'INFECTED' && row('Menace', file.scanVerdict?.threatName ?? 'inconnue')}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 16 }}>
        {canRescan && (
          <button className="btn" disabled={rescan.isPending} onClick={() => rescan.mutate(file.id)}>
            {rescan.isPending ? 'Relance...' : 'Rescanner'}
          </button>
        )}
        {isDownloadable(file.status) && (
          <button className="btn btn-primary" onClick={() => api.downloadFile(file.id, file.filename)}>
            Telecharger
          </button>
        )}
      </div>
    </Modal>
  );
}
