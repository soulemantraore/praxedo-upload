import type { FileStatus } from '../api/types';

// ---- Tailles (base 1000, virgule francaise, format de la maquette) ----
export function formatBytes(bytes: number): string {
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(1).replace('.', ',')} Go`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(1).replace('.', ',')} Mo`;
  if (bytes >= 1e3) return `${Math.round(bytes / 1e3)} Ko`;
  return `${bytes} o`;
}

// ---- Dates (UTC pour des tests deterministes) ----
const MONTHS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];
const pad = (n: number) => String(n).padStart(2, '0');

export function formatDateShort(iso: string): string {
  const d = new Date(iso);
  return `${pad(d.getUTCDate())}/${pad(d.getUTCMonth() + 1)}/${String(d.getUTCFullYear()).slice(2)} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
}

export function formatDateLong(iso: string): string {
  const d = new Date(iso);
  return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}, ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
}

// Retro-compatibilite : formatDate = forme longue (utilise par la modale detail actuelle)
export const formatDate = formatDateLong;

// ---- Statut -> presentation ----
export type BadgeTone = 'clean' | 'scanning' | 'pending' | 'blocked' | 'error';

const PRESENTATION: Record<FileStatus, { label: string; tone: BadgeTone }> = {
  CLEAN: { label: 'Validé', tone: 'clean' },
  SCANNING: { label: 'Scan en cours', tone: 'scanning' },
  PENDING: { label: 'En attente', tone: 'pending' },
  INFECTED: { label: 'Bloqué', tone: 'blocked' },
  SCAN_FAILED: { label: 'Erreur', tone: 'error' },
  EXPIRED: { label: 'Expiré', tone: 'error' },
};

export function statusPresentation(status: FileStatus) {
  return PRESENTATION[status];
}

export interface BadgeColor { bg: string; fg: string; dot: string; pulse: boolean; }

export const badgeColors: Record<BadgeTone, BadgeColor> = {
  clean:    { bg: '#E6F4EC', fg: '#1F7A46', dot: '#28A15E', pulse: false },
  scanning: { bg: '#E6F0F8', fg: '#1D6FB0', dot: '#2C8BD8', pulse: true },
  pending:  { bg: '#FBF3DE', fg: '#8A6D1F', dot: '#C99A2E', pulse: false },
  blocked:  { bg: '#FBEBE9', fg: '#B23B30', dot: '#D14B3E', pulse: false },
  error:    { bg: '#EEF1F4', fg: '#5A6674', dot: '#7A8794', pulse: false },
};

export function isDownloadable(status: FileStatus): boolean {
  return status === 'CLEAN';
}

// ---- Icone par extension (couleurs de la maquette) ----
const EXT_COLORS: Record<string, [string, string]> = {
  PDF: ['#FBEBE9', '#C0392B'], XLSX: ['#E6F4EC', '#1F7A46'], CSV: ['#E6F4EC', '#1F7A46'],
  ZIP: ['#F3EDF9', '#7A4FB5'], DOCX: ['#E6F0F8', '#1D6FB0'], SQL: ['#EEF1F4', '#5A6674'],
  EXE: ['#FBEBE9', '#B23B30'],
};

export function fileExtMeta(filename: string): { ext: string; bg: string; color: string } {
  const parts = filename.split('.');
  const ext = parts.length > 1 ? parts.pop()!.toUpperCase().slice(0, 4) : 'FILE';
  const [bg, color] = EXT_COLORS[ext] ?? ['#EEF2F6', '#667586'];
  return { ext, bg, color };
}
