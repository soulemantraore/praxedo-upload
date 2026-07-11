import { useState, useCallback, useRef, useEffect } from 'react';
import { config } from './config';
import { useStats, useFiles, api } from './api/hooks';
import type { FileView } from './api/types';
import { Header } from './components/Header';
import { StatCards } from './components/StatCards';
import { FileTable } from './components/FileTable';
import { Toast } from './components/Toast';
import { UploadModal } from './components/UploadModal';
import { FileReportModal } from './components/FileReportModal';

const PAGE_SIZE = 6;

export default function App() {
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [uploadOpen, setUploadOpen] = useState(false);
  const [selected, setSelected] = useState<FileView | null>(null);
  const [toast, setToast] = useState('');
  const toastTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const stats = useStats();
  const files = useFiles({ page, size: PAGE_SIZE, q });

  const onSearch = useCallback((value: string) => {
    setQ(value);
    setPage(0);
  }, []);

  const showToast = useCallback((msg: string) => {
    setToast(msg);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(''), 2600);
  }, []);

  useEffect(() => () => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
  }, []);

  return (
    <div style={{ minHeight: '100vh' }}>
      <Header
        portalName={config.portalName}
        clientName={config.clientName}
        clientOrg={config.clientOrg}
        onOpenUpload={() => setUploadOpen(true)}
      />
      <main style={{ maxWidth: 1080, margin: '0 auto', padding: 28, animation: 'fadeUp .3s ease both' }}>
        <StatCards stats={stats.data} />
        <FileTable
          files={files.data?.items ?? []}
          loading={files.isLoading}
          query={q}
          onSearch={onSearch}
          page={page}
          totalPages={files.data?.totalPages ?? 1}
          totalElements={files.data?.totalElements ?? 0}
          pageSize={PAGE_SIZE}
          onPage={setPage}
          onView={setSelected}
          onDownload={(f) => {
            api.downloadFile(f.id, f.filename);
            showToast('Téléchargement démarré : ' + f.filename);
          }}
        />
        <div style={{ textAlign: 'center', fontSize: 11.5, color: '#9AA6B3', marginTop: 22 }}>
          Micro-service de gestion de fichiers sécurisés · analyse antivirus obligatoire avant tout téléchargement
        </div>
      </main>

      {uploadOpen && (
        <UploadModal
          onClose={() => setUploadOpen(false)}
          onUploaded={(res) =>
            showToast(
              res.errors.length
                ? `${res.errors.length} echec(s) a l'upload`
                : `${res.done} fichier(s) reçu(s) — analyse en cours`,
            )
          }
        />
      )}
      {selected && (
        <FileReportModal file={selected} onClose={() => setSelected(null)} onToast={showToast} />
      )}
      <Toast message={toast} />
    </div>
  );
}
