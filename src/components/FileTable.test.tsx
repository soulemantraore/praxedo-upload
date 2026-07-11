import { render, screen } from '@testing-library/react';
import { FileTable } from './FileTable';
import type { FileView } from '../api/types';

const base: FileView = {
  id: 'f1', filename: 'rapport.pdf', contentType: 'application/pdf', size: 2_400_000,
  status: 'CLEAN', batchId: null, scanVerdict: null,
  createdAt: '2026-07-11T09:00:00Z', updatedAt: '2026-07-11T09:00:00Z', scannedAt: null,
};

test('bouton Telecharger seulement pour les fichiers CLEAN (invariant section 2)', () => {
  render(<FileTable loading={false} files={[base, { ...base, id: 'f2', filename: 'v.exe', status: 'INFECTED' }]} onView={() => {}} onDownload={() => {}} />);
  expect(screen.getByLabelText('Telecharger rapport.pdf')).toBeInTheDocument();
  expect(screen.queryByLabelText('Telecharger v.exe')).not.toBeInTheDocument();
});

test('affiche taille et date formatees', () => {
  render(<FileTable loading={false} files={[base]} onView={() => {}} onDownload={() => {}} />);
  expect(screen.getByText('2,4 Mo')).toBeInTheDocument();
});

test('etat vide', () => {
  render(<FileTable loading={false} files={[]} onView={() => {}} onDownload={() => {}} />);
  expect(screen.getByText(/aucun fichier/i)).toBeInTheDocument();
});
