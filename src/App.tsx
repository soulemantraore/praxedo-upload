import { useState, useCallback } from 'react';
import { config } from './config';
import { useStats, useFiles, api } from './api/hooks';
import type { FileStatus, FileView } from './api/types';
import { Header } from './components/Header';
import { StatCards } from './components/StatCards';
import { SearchBar } from './components/SearchBar';
import { FileTable } from './components/FileTable';
import { Pagination } from './components/Pagination';
import { UploadModal } from './components/UploadModal';
import { FileReportModal } from './components/FileReportModal';

const PAGE_SIZE = 6;

export default function App() {
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<FileStatus | ''>('');
  const [uploadOpen, setUploadOpen] = useState(false);
  const [selected, setSelected] = useState<FileView | null>(null);

  const stats = useStats();
  const files = useFiles({ page, size: PAGE_SIZE, q, status });

  const onSearch = useCallback((value: string) => { setQ(value); setPage(0); }, []);
  const onStatus = useCallback((s: FileStatus | '') => { setStatus(s); setPage(0); }, []);

  return (
    <div className="app">
      <Header portalName={config.portalName} apiKey={config.apiKey} onOpenUpload={() => setUploadOpen(true)} />
      <StatCards stats={stats.data} />
      <h2 style={{ fontSize: '1.05rem', margin: '8px 0' }}>Mes fichiers</h2>
      <SearchBar status={status} onSearch={onSearch} onStatus={onStatus} />
      <FileTable
        files={files.data?.items ?? []}
        loading={files.isLoading}
        onView={setSelected}
        onDownload={(f) => api.downloadFile(f.id, f.filename)}
      />
      <Pagination page={page} totalPages={files.data?.totalPages ?? 1} onPage={setPage} />

      {uploadOpen && <UploadModal onClose={() => setUploadOpen(false)} />}
      {selected && <FileReportModal file={selected} onClose={() => setSelected(null)} />}
    </div>
  );
}
