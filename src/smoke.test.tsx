import { render, screen } from '@testing-library/react';
import App from './App';

test('affiche le titre de la page', () => {
  render(<App />);
  expect(screen.getByRole('heading', { name: /fichiers securises/i })).toBeInTheDocument();
});
