import { formatBytes, formatDate, statusPresentation, isDownloadable } from './format';

test('formatBytes en unites francaises (base 1000)', () => {
  expect(formatBytes(0)).toBe('0 o');
  expect(formatBytes(920)).toBe('920 o');
  expect(formatBytes(18_000_000)).toBe('18 Mo');
  expect(formatBytes(2_400_000)).toBe('2,4 Mo');
  expect(formatBytes(1_100_000_000)).toBe('1,1 Go');
});

test('formatDate en francais court et deterministe', () => {
  expect(formatDate('2026-07-11T09:30:00Z')).toMatch(/2026/);
  expect(formatDate('2026-07-11T09:30:00Z')).toMatch(/juil/i);
});

test('statusPresentation mappe statut -> label + ton', () => {
  expect(statusPresentation('CLEAN')).toEqual({ label: 'Valide', tone: 'clean' });
  expect(statusPresentation('SCANNING')).toEqual({ label: 'Scan en cours', tone: 'scanning' });
  expect(statusPresentation('PENDING')).toEqual({ label: 'En attente', tone: 'pending' });
  expect(statusPresentation('INFECTED')).toEqual({ label: 'Bloque', tone: 'blocked' });
  expect(statusPresentation('SCAN_FAILED')).toEqual({ label: 'Echec du scan', tone: 'blocked' });
  expect(statusPresentation('EXPIRED')).toEqual({ label: 'Expire', tone: 'pending' });
});

test('isDownloadable vrai uniquement pour CLEAN (invariant spec section 2)', () => {
  expect(isDownloadable('CLEAN')).toBe(true);
  for (const s of ['PENDING', 'SCANNING', 'INFECTED', 'SCAN_FAILED', 'EXPIRED'] as const) {
    expect(isDownloadable(s)).toBe(false);
  }
});
