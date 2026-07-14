import { render, screen } from '@testing-library/react';
import { FileTable } from './FileTable';
import type { FileView } from '../api/types';

const base: FileView = {
  id: 'f1', filename: 'rapport.pdf', contentType: 'application/pdf', sizeBytes: 2_400_000,
  status: 'CLEAN', infected: false, threatName: null,
  createdAt: '2026-07-11T09:00:00Z', scannedAt: null,
};

const noop = () => {};

function renderTable(files: FileView[]) {
  render(
    <FileTable
      files={files}
      loading={false}
      query=""
      onSearch={noop}
      page={0}
      totalPages={1}
      totalElements={files.length}
      pageSize={6}
      onPage={noop}
      onView={noop}
      onDownload={noop}
    />,
  );
}

test('bouton Telecharger seulement pour les fichiers CLEAN (invariant section 2)', () => {
  renderTable([base, { ...base, id: 'f2', filename: 'v.exe', status: 'INFECTED' }]);
  expect(screen.getByLabelText('Télécharger rapport.pdf')).toBeInTheDocument();
  expect(screen.queryByLabelText('Télécharger v.exe')).not.toBeInTheDocument();
});

test('affiche la taille formatee', () => {
  renderTable([base]);
  expect(screen.getByText('2,4 Mo')).toBeInTheDocument();
});

test('etat vide', () => {
  renderTable([]);
  expect(screen.getByText(/aucun fichier/i)).toBeInTheDocument();
});
