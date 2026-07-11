import { formatBytes, formatDateShort, formatDateLong, formatDate, statusPresentation, badgeColors, isDownloadable, fileExtMeta } from './format';

test('formatBytes en unites francaises (base 1000, format maquette)', () => {
  expect(formatBytes(0)).toBe('0 o');
  expect(formatBytes(920)).toBe('920 o');
  expect(formatBytes(42_000)).toBe('42 Ko');
  expect(formatBytes(2_400_000)).toBe('2,4 Mo');
  expect(formatBytes(18_000_000)).toBe('18,0 Mo');
  expect(formatBytes(88_400_000)).toBe('88,4 Mo');
  expect(formatBytes(1_100_000_000)).toBe('1,1 Go');
});

test('formatDateShort et formatDateLong (deterministes UTC)', () => {
  expect(formatDateShort('2026-07-11T09:30:00Z')).toBe('11/07/26 09:30');
  expect(formatDateLong('2026-07-11T09:30:00Z')).toBe('11 juil. 2026, 09:30');
  expect(formatDate('2026-07-11T09:30:00Z')).toMatch(/juil/i);
});

test('statusPresentation mappe statut -> label accentue + ton', () => {
  expect(statusPresentation('CLEAN')).toEqual({ label: 'Validé', tone: 'clean' });
  expect(statusPresentation('SCANNING')).toEqual({ label: 'Scan en cours', tone: 'scanning' });
  expect(statusPresentation('PENDING')).toEqual({ label: 'En attente', tone: 'pending' });
  expect(statusPresentation('INFECTED')).toEqual({ label: 'Bloqué', tone: 'blocked' });
  expect(statusPresentation('SCAN_FAILED')).toEqual({ label: 'Erreur', tone: 'error' });
  expect(statusPresentation('EXPIRED')).toEqual({ label: 'Expiré', tone: 'error' });
});

test('badgeColors : scanning pulse, clean dot vert', () => {
  expect(badgeColors.scanning.pulse).toBe(true);
  expect(badgeColors.clean.dot).toBe('#28A15E');
  expect(badgeColors.error.fg).toBe('#5A6674');
});

test('isDownloadable vrai uniquement pour CLEAN (invariant spec section 2)', () => {
  expect(isDownloadable('CLEAN')).toBe(true);
  for (const s of ['PENDING', 'SCANNING', 'INFECTED', 'SCAN_FAILED', 'EXPIRED'] as const) {
    expect(isDownloadable(s)).toBe(false);
  }
});

test('fileExtMeta : extension + couleurs', () => {
  expect(fileExtMeta('rapport.pdf')).toEqual({ ext: 'PDF', bg: '#FBEBE9', color: '#C0392B' });
  expect(fileExtMeta('Export_clients.xlsx')).toEqual({ ext: 'XLSX', bg: '#E6F4EC', color: '#1F7A46' });
  expect(fileExtMeta('sansextension')).toEqual({ ext: 'FILE', bg: '#EEF2F6', color: '#667586' });
});
