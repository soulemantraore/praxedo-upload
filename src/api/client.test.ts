import { createFileApi, ApiError } from './client';
import type { AppConfig } from '../config';

const cfg: AppConfig = {
  apiBaseUrl: 'http://api.test', apiKey: 'secret',
  portalName: 'P', clientName: 'C', clientOrg: 'O', useMock: false, pollIntervalMs: 1000,
};

function fakeFetch(handler: (url: string, init?: RequestInit) => Response) {
  const calls: { url: string; init?: RequestInit }[] = [];
  const fn = async (url: string | URL, init?: RequestInit) => {
    calls.push({ url: String(url), init });
    return handler(String(url), init);
  };
  return { fn: fn as unknown as typeof fetch, calls };
}

test('getStats envoie X-API-Key et parse le corps', async () => {
  const { fn, calls } = fakeFetch(() =>
    new Response(JSON.stringify({ total: 3, clean: 1, scanning: 1, pending: 1, blocked: 0 }),
      { status: 200, headers: { 'content-type': 'application/json' } }));
  const api = createFileApi(cfg, fn);
  const stats = await api.getStats();
  expect(stats.total).toBe(3);
  expect(calls[0].url).toBe('http://api.test/api/files/stats');
  expect((calls[0].init!.headers as Record<string, string>)['X-API-Key']).toBe('secret');
});

test('listFiles serialise les parametres de requete', async () => {
  const { fn, calls } = fakeFetch(() =>
    new Response(JSON.stringify({ items: [], page: 0, totalPages: 0, totalElements: 0 }),
      { status: 200, headers: { 'content-type': 'application/json' } }));
  const api = createFileApi(cfg, fn);
  await api.listFiles({ page: 1, size: 6, q: 'rap', status: 'CLEAN' });
  const u = new URL(calls[0].url);
  expect(u.pathname).toBe('/api/files');
  expect(u.searchParams.get('page')).toBe('1');
  expect(u.searchParams.get('size')).toBe('6');
  expect(u.searchParams.get('q')).toBe('rap');
  expect(u.searchParams.get('status')).toBe('CLEAN');
});

test('registerUpload POST le corps JSON', async () => {
  const { fn, calls } = fakeFetch(() =>
    new Response(JSON.stringify({ id: 'f1', filename: 'a.pdf', status: 'PENDING', uploadUrl: 'http://up/f1', uploadExpiresAt: 'x' }),
      { status: 201, headers: { 'content-type': 'application/json' } }));
  const api = createFileApi(cfg, fn);
  const ticket = await api.registerUpload({ filename: 'a.pdf', contentType: 'application/pdf', size: 10 });
  expect(ticket.uploadUrl).toBe('http://up/f1');
  expect(calls[0].init!.method).toBe('POST');
  expect(JSON.parse(calls[0].init!.body as string)).toEqual({ filename: 'a.pdf', contentType: 'application/pdf', size: 10 });
});

test('uploadBytes PUT les octets sans X-API-Key (URL deja signee)', async () => {
  const { fn, calls } = fakeFetch(() => new Response(null, { status: 200 }));
  const api = createFileApi(cfg, fn);
  const file = new File(['hello'], 'a.txt', { type: 'text/plain' });
  await api.uploadBytes('http://up/f1', file);
  expect(calls[0].init!.method).toBe('PUT');
  expect(calls[0].url).toBe('http://up/f1');
  expect((calls[0].init!.headers as Record<string, string>)['X-API-Key']).toBeUndefined();
});

test('rescan POST /rescan', async () => {
  const { fn, calls } = fakeFetch(() => new Response(null, { status: 202 }));
  const api = createFileApi(cfg, fn);
  await api.rescan('f1');
  expect(calls[0].url).toBe('http://api.test/api/files/f1/rescan');
  expect(calls[0].init!.method).toBe('POST');
});

test('une reponse non-OK leve ApiError avec le status', async () => {
  const { fn } = fakeFetch(() => new Response('boom', { status: 403 }));
  const api = createFileApi(cfg, fn);
  await expect(api.getFile('f1')).rejects.toMatchObject({ status: 403 });
  await expect(api.getFile('f1')).rejects.toBeInstanceOf(ApiError);
});
