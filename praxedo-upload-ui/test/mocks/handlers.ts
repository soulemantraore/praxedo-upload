import { http, HttpResponse } from 'msw';
import * as store from './store';

const BASE = 'http://api.test';

export const handlers = [
  http.get(`${BASE}/api/files/stats`, () => HttpResponse.json(store.stats())),

  http.get(`${BASE}/api/files/:id`, ({ params }) => {
    const f = store.getFile(String(params.id));
    return f ? HttpResponse.json(f) : new HttpResponse(null, { status: 404 });
  }),

  http.get(`${BASE}/api/files`, ({ request }) => {
    const url = new URL(request.url);
    return HttpResponse.json(store.listFiles(url.searchParams));
  }),

  http.post(`${BASE}/api/files`, async ({ request }) => {
    const body = (await request.json()) as { filename: string; contentType: string; size: number };
    return HttpResponse.json(store.register(body), { status: 201 });
  }),

  http.post(`${BASE}/api/files/:id/rescan`, ({ params }) =>
    store.rescan(String(params.id))
      ? new HttpResponse(null, { status: 202 })
      : new HttpResponse(null, { status: 404 })),

  http.get(`${BASE}/api/files/:id/content`, ({ params }) => {
    const f = store.getFile(String(params.id));
    if (!f) return new HttpResponse(null, { status: 404 });
    if (f.status !== 'CLEAN') return new HttpResponse(null, { status: 403 });
    return new HttpResponse(new Blob([`contenu de ${f.filename}`]), { status: 200 });
  }),

  http.put('http://mock.local/upload/:id', ({ params }) =>
    store.markUploaded(String(params.id))
      ? new HttpResponse(null, { status: 200 })
      : new HttpResponse(null, { status: 404 })),
];
