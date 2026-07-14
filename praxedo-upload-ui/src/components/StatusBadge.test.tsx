import { render, screen } from '@testing-library/react';
import { StatusBadge } from './StatusBadge';

test('affiche le label Valide pour CLEAN', () => {
  render(<StatusBadge status="CLEAN" />);
  expect(screen.getByText('Validé')).toBeInTheDocument();
});

test('affiche Bloque pour INFECTED', () => {
  render(<StatusBadge status="INFECTED" />);
  expect(screen.getByText('Bloqué')).toBeInTheDocument();
});
