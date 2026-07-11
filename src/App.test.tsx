import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { resetStore } from '../test/mocks/store';

function renderApp() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={qc}><App /></QueryClientProvider>);
}

test('affiche l en-tete, les cartes et la liste seedee', async () => {
  resetStore([{ filename: 'rapport.pdf', status: 'CLEAN', size: 2_400_000 }]);
  renderApp();
  expect(screen.getByText(/mes fichiers/i)).toBeInTheDocument();
  await waitFor(() => expect(screen.getByText('rapport.pdf')).toBeInTheDocument());
  expect(screen.getByLabelText('Télécharger rapport.pdf')).toBeInTheDocument();
});

test('ouvre la modale de depot', async () => {
  resetStore();
  renderApp();
  await userEvent.click(screen.getByRole('button', { name: /déposer un fichier/i }));
  expect(screen.getByRole('dialog')).toBeInTheDocument();
});
