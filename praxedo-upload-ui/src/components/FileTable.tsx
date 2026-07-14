import type { FileView } from '../api/types';
import { formatBytes, formatDateShort, fileExtMeta, isDownloadable } from '../lib/format';
import { StatusBadge } from './StatusBadge';
import { SearchBar } from './SearchBar';
import { Pagination } from './Pagination';

interface FileTableProps {
  files: FileView[];
  loading: boolean;
  query: string;
  onSearch: (q: string) => void;
  page: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
  onPage: (p: number) => void;
  onView: (f: FileView) => void;
  onDownload: (f: FileView) => void;
}

const GRID = 'minmax(0,1fr) 72px 118px 120px 84px';
const MONO = "'IBM Plex Mono',monospace";

export function FileTable({
  files,
  loading,
  query,
  onSearch,
  page,
  totalPages,
  totalElements,
  pageSize,
  onPage,
  onView,
  onDownload,
}: FileTableProps) {
  return (
    <div style={{ background: '#fff', border: '1px solid #E1E7EE', borderRadius: 12, marginTop: 20, overflow: 'hidden' }}>
      {/* En-tete de la carte */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          padding: '16px 20px',
          borderBottom: '1px solid #EEF2F6',
          flexWrap: 'wrap',
        }}
      >
        <div>
          <div style={{ fontSize: 15, fontWeight: 600 }}>Mes fichiers</div>
          <div style={{ fontSize: 12, color: '#8A96A4', marginTop: 2, display: 'flex', alignItems: 'center', gap: 6 }}>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#28A15E" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 2 4 5v6c0 5 3.5 8.5 8 11 4.5-2.5 8-6 8-11V5l-8-3Z" />
            </svg>
            Seuls les fichiers validés par l'antivirus sont téléchargeables
          </div>
        </div>
        <SearchBar query={query} onSearch={onSearch} />
      </div>

      {/* En-tete de colonnes */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: GRID,
          gap: 10,
          padding: '12px 20px',
          background: '#F7F9FB',
          borderBottom: '1px solid #E1E7EE',
          fontSize: 11.5,
          fontWeight: 600,
          color: '#8A96A4',
          textTransform: 'uppercase',
          letterSpacing: '.04em',
        }}
      >
        <div>Nom</div>
        <div>Taille</div>
        <div>Ajouté le</div>
        <div>Statut</div>
        <div style={{ textAlign: 'right' }}>Action</div>
      </div>

      {/* Lignes */}
      {files.map((f) => {
        const meta = fileExtMeta(f.filename);
        return (
          <div
            key={f.id}
            style={{
              display: 'grid',
              gridTemplateColumns: GRID,
              gap: 10,
              padding: '14px 20px',
              borderBottom: '1px solid #F1F4F8',
              alignItems: 'center',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
              <div
                style={{
                  width: 34,
                  height: 34,
                  borderRadius: 8,
                  background: meta.bg,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                  fontSize: 9.5,
                  fontWeight: 700,
                  color: meta.color,
                  fontFamily: MONO,
                }}
              >
                {meta.ext}
              </div>
              <div
                onClick={() => onView(f)}
                style={{
                  fontSize: 13.5,
                  fontWeight: 500,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  cursor: 'pointer',
                  minWidth: 0,
                }}
              >
                {f.filename}
              </div>
            </div>
            <div style={{ fontSize: 12.5, color: '#667586', fontFamily: MONO }}>{formatBytes(f.sizeBytes)}</div>
            <div style={{ fontSize: 12.5, color: '#667586' }}>{formatDateShort(f.createdAt)}</div>
            <div>
              <StatusBadge status={f.status} />
              {f.status === 'SCANNING' && (
                <div style={{ height: 4, background: '#E6F0F8', borderRadius: 3, marginTop: 7, overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: '40%', background: '#2C8BD8', borderRadius: 3, animation: 'indet 1.1s ease-in-out infinite' }} />
                </div>
              )}
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              {isDownloadable(f.status) && (
                <button
                  aria-label={`Télécharger ${f.filename}`}
                  onClick={() => onDownload(f)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    background: '#E6F4EC',
                    color: '#1F7A46',
                    border: 'none',
                    borderRadius: 8,
                    padding: '8px 11px',
                    font: 'inherit',
                    fontSize: 12.5,
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#1F7A46" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                    <path d="M7 10l5 5 5-5" />
                    <path d="M12 15V3" />
                  </svg>
                </button>
              )}
              <button
                onClick={() => onView(f)}
                style={{
                  background: '#fff',
                  color: '#4A5A6A',
                  border: '1px solid #E1E7EE',
                  borderRadius: 8,
                  padding: '8px 11px',
                  font: 'inherit',
                  fontSize: 12.5,
                  fontWeight: 500,
                  cursor: 'pointer',
                }}
              >
                Voir
              </button>
            </div>
          </div>
        );
      })}

      {/* Etat vide */}
      {!loading && files.length === 0 && (
        <div style={{ padding: 48, textAlign: 'center', color: '#8A96A4', fontSize: 13.5 }}>
          Aucun fichier ne correspond à votre recherche.
        </div>
      )}

      {/* Pagination */}
      {totalElements > pageSize && (
        <Pagination page={page} totalPages={totalPages} totalElements={totalElements} pageSize={pageSize} onPage={onPage} />
      )}
    </div>
  );
}
