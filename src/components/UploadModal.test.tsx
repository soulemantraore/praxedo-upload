import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UploadModal } from './UploadModal';
import { resetStore } from '../../test/mocks/store';

function renderModal(onClose = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={qc}><UploadModal onClose={onClose} /></QueryClientProvider>);
  return onClose;
}

test('selectionne un fichier et l envoie, puis ferme', async () => {
  resetStore();
  const onClose = renderModal();
  const file = new File(['bytes'], 'rapport.pdf', { type: 'application/pdf' });
  await userEvent.upload(screen.getByLabelText('Choisir des fichiers'), file);
  expect(screen.getByText('rapport.pdf')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /envoyer/i }));
  await waitFor(() => expect(onClose).toHaveBeenCalled());
});
