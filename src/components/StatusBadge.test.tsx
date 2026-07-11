import { render, screen } from '@testing-library/react';
import { StatusBadge } from './StatusBadge';

test('affiche le label Valide pour CLEAN', () => {
  render(<StatusBadge status="CLEAN" />);
  expect(screen.getByText('Valide')).toHaveClass('badge', 'clean');
});

test('affiche Bloque pour INFECTED', () => {
  render(<StatusBadge status="INFECTED" />);
  expect(screen.getByText('Bloque')).toHaveClass('badge', 'blocked');
});
