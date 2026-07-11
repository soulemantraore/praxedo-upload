import { render, screen } from '@testing-library/react';
import { StatCards } from './StatCards';

test('affiche les 4 cartes avec les valeurs', () => {
  render(<StatCards stats={{ total: 7, clean: 4, scanning: 2, pending: 1, blocked: 1 }} />);
  expect(screen.getByText('Total').previousSibling).toHaveTextContent('7');
  expect(screen.getByText('Valides')).toBeInTheDocument();
  expect(screen.getByText('En analyse')).toBeInTheDocument();
  expect(screen.getByText('Bloques')).toBeInTheDocument();
});

test('affiche un tiret sans donnees', () => {
  render(<StatCards />);
  expect(screen.getAllByText('-')).toHaveLength(4);
});
