import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pagination } from './Pagination';

test('affiche la plage et la page courante, Precedent desactive', () => {
  render(<Pagination page={0} totalPages={3} totalElements={15} pageSize={6} onPage={() => {}} />);
  expect(screen.getByText('Page 1 / 3')).toBeInTheDocument();
  expect(screen.getByText('1–6 sur 15 fichier(s)')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Précédent' })).toBeDisabled();
});

test('clic sur Suivant appelle onPage(1)', async () => {
  const onPage = vi.fn();
  render(<Pagination page={0} totalPages={3} totalElements={15} pageSize={6} onPage={onPage} />);
  await userEvent.click(screen.getByRole('button', { name: 'Suivant' }));
  expect(onPage).toHaveBeenCalledWith(1);
});
