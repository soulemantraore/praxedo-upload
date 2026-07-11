import type { FileView, FileStatus, StatsView } from '../../src/api/types';

const now = () => new Date('2026-07-11T09:00:00Z').toISOString();

interface StoreFile extends FileView {
  revealAtPoll: number; // le scan se resout au polling >= cette valeur
  uploaded: boolean;
}

let files: StoreFile[] = [];
let pollCount = 0;
let seq = 0;

function isThreat(filename: string): boolean {
  return /eicar|virus|infected|malware/i.test(filename);
}

function genSha256(): string {
  const h = '0123456789abcdef';
  let s = '';
  for (let i = 0; i < 64; i++) s += h[Math.floor(Math.random() * 16)];
  return s;
}

export function resetStore(seed: Partial<StoreFile>[] = []): void {
  files = [];
  pollCount = 0;
  seq = 0;
  for (const s of seed) addSeed(s);
}

function addSeed(s: Partial<StoreFile>): void {
  seq++;
  files.push({
    id: s.id ?? `seed-${seq}`,
    filename: s.filename ?? `file-${seq}.txt`,
    contentType: s.contentType ?? 'text/plain',
    size: s.size ?? 1024,
    status: s.status ?? 'CLEAN',
    batchId: s.batchId ?? null,
    scanVerdict: s.scanVerdict ?? null,
    createdAt: s.createdAt ?? now(),
    updatedAt: s.updatedAt ?? now(),
    scannedAt: s.scannedAt ?? null,
    sha256: s.sha256 ?? genSha256(),
    revealAtPoll: s.revealAtPoll ?? 0,
    uploaded: s.uploaded ?? true,
  });
}

export function register(input: { filename: string; contentType: string; size: number }) {
  seq++;
  const id = `f-${seq}`;
  files.push({
    id,
    filename: input.filename,
    contentType: input.contentType,
    size: input.size,
    status: 'PENDING',
    batchId: null,
    scanVerdict: null,
    createdAt: now(),
    updatedAt: now(),
    scannedAt: null,
    sha256: genSha256(),
    revealAtPoll: Number.MAX_SAFE_INTEGER,
    uploaded: false,
  });
  return {
    id,
    filename: input.filename,
    status: 'PENDING' as FileStatus,
    uploadUrl: `http://mock.local/upload/${id}`,
    uploadExpiresAt: now(),
  };
}

export function markUploaded(id: string): boolean {
  const f = files.find((x) => x.id === id);
  if (!f) return false;
  f.uploaded = true;
  f.status = 'SCANNING';
  // +2 (et pas +1) : le prochain tick() portera pollCount a pollCount+1, ce qui
  // egalerait deja revealAtPoll et resoudrait le scan des le premier polling.
  // +2 garantit un premier tick visible en SCANNING avant resolution au suivant.
  f.revealAtPoll = pollCount + 2;
  f.updatedAt = now();
  return true;
}

function advance(f: StoreFile): void {
  if (f.status === 'SCANNING' && pollCount >= f.revealAtPoll) {
    if (isThreat(f.filename)) {
      f.status = 'INFECTED';
      f.scanVerdict = { engine: 'ClamAV(mock)', verdict: 'INFECTED', threatName: 'Eicar-Test-Signature', scannedAt: now() };
    } else {
      f.status = 'CLEAN';
      f.scanVerdict = { engine: 'ClamAV(mock)', verdict: 'CLEAN', threatName: null, scannedAt: now() };
    }
    f.scannedAt = now();
    f.updatedAt = now();
  }
}

function tick(): void {
  pollCount++;
  files.forEach(advance);
}

export function listFiles(params: URLSearchParams) {
  tick();
  const page = Number(params.get('page') ?? '0');
  const size = Number(params.get('size') ?? '6');
  const q = (params.get('q') ?? '').toLowerCase();
  const status = params.get('status') ?? '';
  let filtered = files.filter((f) => f.status !== 'EXPIRED');
  if (q) filtered = filtered.filter((f) => f.filename.toLowerCase().includes(q));
  if (status) filtered = filtered.filter((f) => f.status === status);
  const totalElements = filtered.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  const items = filtered.slice(page * size, page * size + size).map(toView);
  return { items, page, totalPages, totalElements };
}

export function stats(): StatsView {
  tick();
  const c = (s: FileStatus) => files.filter((f) => f.status === s).length;
  return {
    total: files.length,
    clean: c('CLEAN'),
    scanning: c('SCANNING'),
    pending: c('PENDING'),
    blocked: c('INFECTED') + c('SCAN_FAILED'),
  };
}

export function getFile(id: string): FileView | undefined {
  const f = files.find((x) => x.id === id);
  return f ? toView(f) : undefined;
}

export function rescan(id: string): boolean {
  const f = files.find((x) => x.id === id);
  if (!f) return false;
  f.status = 'SCANNING';
  f.scanVerdict = null;
  f.revealAtPoll = pollCount + 2; // meme raisonnement que markUploaded
  f.updatedAt = now();
  return true;
}

function toView(f: StoreFile): FileView {
  return {
    id: f.id,
    filename: f.filename,
    contentType: f.contentType,
    size: f.size,
    status: f.status,
    batchId: f.batchId,
    scanVerdict: f.scanVerdict,
    createdAt: f.createdAt,
    updatedAt: f.updatedAt,
    scannedAt: f.scannedAt,
    sha256: f.sha256,
  };
}

export function seedDemo(): void {
  const base = Date.parse('2026-07-11T09:00:00Z');
  const H = 3600_000;
  const iso = (hoursAgo: number) => new Date(base - hoursAgo * H).toISOString();
  const verdict = (v: 'CLEAN' | 'INFECTED' | null, threat: string | null, hoursAgo: number) =>
    v ? { engine: 'Praxedo Secure Scan (ClamAV 1.3)', verdict: v, threatName: threat, scannedAt: iso(hoursAgo) } : null;
  resetStore([
    { filename: 'Rapport_intervention_2026-07.pdf', contentType: 'application/pdf', size: 4_830_000, status: 'CLEAN', createdAt: iso(2), scannedAt: iso(2), scanVerdict: verdict('CLEAN', null, 2) },
    { filename: 'Export_clients_Q2.xlsx', contentType: 'application/vnd.ms-excel', size: 1_240_000, status: 'CLEAN', createdAt: iso(5), scannedAt: iso(5), scanVerdict: verdict('CLEAN', null, 5) },
    { filename: 'Photos_chantier_Lyon.zip', contentType: 'application/zip', size: 88_400_000, status: 'SCANNING', createdAt: iso(26), revealAtPoll: Number.MAX_SAFE_INTEGER },
    { filename: 'planning_equipes.csv', contentType: 'text/csv', size: 42_000, status: 'PENDING', createdAt: iso(27) },
    { filename: 'archive_2019.exe', contentType: 'application/octet-stream', size: 12_600_000, status: 'INFECTED', createdAt: iso(30), scannedAt: iso(30), scanVerdict: verdict('INFECTED', 'Trojan.Win32.Agent.dx', 30) },
    { filename: 'Facture_F2026-0912.pdf', contentType: 'application/pdf', size: 318_000, status: 'CLEAN', createdAt: iso(49), scannedAt: iso(49), scanVerdict: verdict('CLEAN', null, 49) },
    { filename: 'Sauvegarde_base.sql', contentType: 'application/sql', size: 254_000_000, status: 'SCAN_FAILED', createdAt: iso(72) },
    { filename: 'Manuel_technique_v3.docx', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', size: 2_870_000, status: 'CLEAN', createdAt: iso(96), scannedAt: iso(96), scanVerdict: verdict('CLEAN', null, 96) },
  ]);
}
