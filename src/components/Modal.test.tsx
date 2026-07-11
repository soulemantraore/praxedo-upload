import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from './Modal';

test('ferme sur clic du bouton fermer', async () => {
  const onClose = vi.fn();
  render(<Modal title="Test" onClose={onClose}><p>corps</p></Modal>);
  await userEvent.click(screen.getByLabelText('Fermer'));
  expect(onClose).toHaveBeenCalled();
});

test('ferme sur touche Echap', async () => {
  const onClose = vi.fn();
  render(<Modal title="Test" onClose={onClose}><p>corps</p></Modal>);
  await userEvent.keyboard('{Escape}');
  expect(onClose).toHaveBeenCalled();
});
