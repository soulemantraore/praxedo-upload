import type { FileStatus } from '../api/types';

const UNITS = ['o', 'Ko', 'Mo', 'Go', 'To'];

export function formatBytes(bytes: number): string {
  if (bytes < 1000) return `${bytes} o`;
  let value = bytes;
  let i = 0;
  while (value >= 1000 && i < UNITS.length - 1) {
    value /= 1000;
    i++;
  }
  const rounded = Math.round(value * 10) / 10;
  const text = Number.isInteger(rounded)
    ? String(rounded)
    : rounded.toFixed(1).replace('.', ',');
  return `${text} ${UNITS[i]}`;
}

const DATE_FMT = new Intl.DateTimeFormat('fr-FR', {
  day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC',
});

export function formatDate(iso: string): string {
  return DATE_FMT.format(new Date(iso));
}

export type BadgeTone = 'clean' | 'scanning' | 'pending' | 'blocked';

const PRESENTATION: Record<FileStatus, { label: string; tone: BadgeTone }> = {
  CLEAN: { label: 'Valide', tone: 'clean' },
  SCANNING: { label: 'Scan en cours', tone: 'scanning' },
  PENDING: { label: 'En attente', tone: 'pending' },
  INFECTED: { label: 'Bloque', tone: 'blocked' },
  SCAN_FAILED: { label: 'Echec du scan', tone: 'blocked' },
  EXPIRED: { label: 'Expire', tone: 'pending' },
};

export function statusPresentation(status: FileStatus) {
  return PRESENTATION[status];
}

export function isDownloadable(status: FileStatus): boolean {
  return status === 'CLEAN';
}
