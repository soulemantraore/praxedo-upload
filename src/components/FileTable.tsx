import type { FileView } from '../api/types';
import { formatBytes, formatDate, isDownloadable } from '../lib/format';
import { StatusBadge } from './StatusBadge';

interface FileTableProps {
  files: FileView[];
  loading: boolean;
  onView: (file: FileView) => void;
  onDownload: (file: FileView) => void;
}

export function FileTable({ files, loading, onView, onDownload }: FileTableProps) {
  if (!loading && files.length === 0) {
    return (
      <div className="card" style={{ padding: 40, textAlign: 'center', color: 'var(--muted)' }}>
        Aucun fichier pour le moment. Deposez un fichier pour commencer.
      </div>
    );
  }
  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      <table>
        <thead>
          <tr>
            <th>Nom</th><th>Taille</th><th>Ajoute le</th><th>Statut</th><th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {files.map((f) => (
            <tr key={f.id}>
              <td style={{ fontWeight: 600 }}>{f.filename}</td>
              <td>{formatBytes(f.size)}</td>
              <td>{formatDate(f.createdAt)}</td>
              <td><StatusBadge status={f.status} /></td>
              <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                {isDownloadable(f.status) && (
                  <button className="btn" aria-label={`Telecharger ${f.filename}`} onClick={() => onDownload(f)}>
                    &#8681; Telecharger
                  </button>
                )}
                <button className="btn" style={{ marginLeft: 8 }} onClick={() => onView(f)}>Voir</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
