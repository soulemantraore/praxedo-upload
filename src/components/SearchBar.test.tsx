import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchBar } from './SearchBar';

test('remonte la recherche apres debounce', async () => {
  const onSearch = vi.fn();
  render(<SearchBar status="" onSearch={onSearch} onStatus={() => {}} />);
  await userEvent.type(screen.getByLabelText('Rechercher un fichier'), 'rap');
  await new Promise((r) => setTimeout(r, 350));
  expect(onSearch).toHaveBeenLastCalledWith('rap');
});
