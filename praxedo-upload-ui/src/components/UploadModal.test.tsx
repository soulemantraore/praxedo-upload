import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UploadModal } from './UploadModal';
import { resetStore } from '../../test/mocks/store';

function renderModal() {
  const onClose = vi.fn();
  const onUploaded = vi.fn();
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <UploadModal onClose={onClose} onUploaded={onUploaded} />
    </QueryClientProvider>,
  );
  return { onClose, onUploaded };
}

test('envoie automatiquement le fichier choisi puis ferme', async () => {
  resetStore();
  const { onClose, onUploaded } = renderModal();
  const file = new File(['bytes'], 'rapport.pdf', { type: 'application/pdf' });
  await userEvent.upload(screen.getByLabelText('Choisir des fichiers'), file);
  await waitFor(() => expect(onClose).toHaveBeenCalled());
  expect(onUploaded).toHaveBeenCalled();
});
