import { createFileApi } from '../../src/api/client';
import type { AppConfig } from '../../src/config';
import { resetStore } from './store';

const cfg: AppConfig = { apiBaseUrl: 'http://api.test', apiKey: 'k', portalName: 'P', clientName: 'C', clientOrg: 'O', useMock: true, pollIntervalMs: 10 };
const api = createFileApi(cfg);

test('cycle complet : register -> upload -> SCANNING -> CLEAN', async () => {
  resetStore();
  const ticket = await api.registerUpload({ filename: 'rapport.pdf', contentType: 'application/pdf', size: 2_400_000 });
  expect(ticket.status).toBe('PENDING');
  await api.uploadBytes(ticket.uploadUrl, new File(['x'], 'rapport.pdf'));
  const p1 = await api.listFiles({ page: 0, size: 6 }); // tick 1 -> SCANNING
  expect(p1.items[0].status).toBe('SCANNING');
  const p2 = await api.listFiles({ page: 0, size: 6 }); // tick 2 -> CLEAN
  expect(p2.items[0].status).toBe('CLEAN');
});

test('un fichier eicar finit INFECTED et le download renvoie 403', async () => {
  resetStore();
  const t = await api.registerUpload({ filename: 'eicar.txt', contentType: 'text/plain', size: 68 });
  await api.uploadBytes(t.uploadUrl, new File(['x'], 'eicar.txt'));
  await api.listFiles({ page: 0, size: 6 });
  await api.listFiles({ page: 0, size: 6 });
  const f = await api.getFile(t.id);
  expect(f.status).toBe('INFECTED');
  expect(f.scanVerdict?.threatName).toBe('Eicar-Test-Signature');
  await expect(api.downloadFile(t.id, 'eicar.txt')).rejects.toMatchObject({ status: 403 });
});

test('stats reflete les compteurs', async () => {
  resetStore([{ status: 'CLEAN' }, { status: 'INFECTED' }, { status: 'PENDING' }]);
  const s = await api.getStats();
  expect(s).toMatchObject({ total: 3, clean: 1, blocked: 1, pending: 1 });
});
