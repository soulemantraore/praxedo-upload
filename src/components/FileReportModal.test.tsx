import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { FileReportModal } from './FileReportModal';
import type { FileView } from '../api/types';

const infected: FileView = {
  id: 'f1', filename: 'eicar.txt', contentType: 'text/plain', sizeBytes: 68, status: 'INFECTED',
  infected: true, threatName: 'Trojan.Win32.Agent.dx',
  createdAt: '2026-07-11T09:00:00Z', scannedAt: '2026-07-11T09:00:00Z',
};

function renderReport(file: FileView) {
  const qc = new QueryClient();
  render(<QueryClientProvider client={qc}><FileReportModal file={file} onClose={() => {}} /></QueryClientProvider>);
}

test('affiche la menace et pas de bouton telecharger pour INFECTED', () => {
  renderReport(infected);
  expect(screen.getAllByText('Menace détectée').length).toBeGreaterThan(0);
  expect(screen.getByText('Trojan.Win32.Agent.dx')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /télécharger/i })).toBeNull();
});

test('affiche telecharger pour CLEAN', () => {
  renderReport({ ...infected, status: 'CLEAN', filename: 'ok.pdf', infected: false, threatName: null });
  expect(screen.getByRole('button', { name: /télécharger/i })).toBeInTheDocument();
});

test('affiche relancer l analyse pour SCAN_FAILED', () => {
  renderReport({ ...infected, status: 'SCAN_FAILED', filename: 'raté.bin', infected: false, threatName: null, scannedAt: null });
  expect(screen.getByRole('button', { name: /relancer l'analyse/i })).toBeInTheDocument();
});
