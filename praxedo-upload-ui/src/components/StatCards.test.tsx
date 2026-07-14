import { render, screen } from '@testing-library/react';
import { StatCards } from './StatCards';

test('affiche les 4 libelles et leurs valeurs', () => {
  render(<StatCards stats={{ total: 7, clean: 4, scanning: 2, pending: 1, blocked: 1 }} />);
  expect(screen.getByText('Total fichiers')).toBeInTheDocument();
  expect(screen.getByText('Validés')).toBeInTheDocument();
  expect(screen.getByText('En analyse')).toBeInTheDocument();
  expect(screen.getByText('Bloqués')).toBeInTheDocument();
  expect(screen.getByText('7')).toBeInTheDocument();
  expect(screen.getByText('4')).toBeInTheDocument();
  expect(screen.getByText('2')).toBeInTheDocument();
  expect(screen.getByText('1')).toBeInTheDocument();
});

test('affiche un tiret sans donnees', () => {
  render(<StatCards />);
  expect(screen.getAllByText('—')).toHaveLength(4);
});
