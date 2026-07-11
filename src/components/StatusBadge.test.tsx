import { render, screen } from '@testing-library/react';
import { StatusBadge } from './StatusBadge';

test('affiche le label Valide pour CLEAN', () => {
  render(<StatusBadge status="CLEAN" />);
  expect(screen.getByText('Validé')).toHaveClass('badge', 'clean');
});

test('affiche Bloque pour INFECTED', () => {
  render(<StatusBadge status="INFECTED" />);
  expect(screen.getByText('Bloqué')).toHaveClass('badge', 'blocked');
});
