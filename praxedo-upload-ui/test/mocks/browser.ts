import { setupWorker } from 'msw/browser';
import { http, HttpResponse } from 'msw';
import * as store from './store';

export function makeWorker(apiBaseUrl: string) {
  store.seedDemo();
  const B = apiBaseUrl;
  return setupWorker(
    http.get(`${B}/api/files/stats`, () => HttpResponse.json(store.stats())),
    http.get(`${B}/api/files/:id`, ({ params }) => {
      const f = store.getFile(String(params.id));
      return f ? HttpResponse.json(f) : new HttpResponse(null, { status: 404 });
    }),
    http.get(`${B}/api/files`, ({ request }) =>
      HttpResponse.json(store.listFiles(new URL(request.url).searchParams))),
    http.post(`${B}/api/files`, async ({ request }) =>
      HttpResponse.json(
        store.register((await request.json()) as { filename: string; contentType: string; size: number }),
        { status: 201 },
      )),
    http.post(`${B}/api/files/:id/rescan`, ({ params }) =>
      store.rescan(String(params.id)) ? new HttpResponse(null, { status: 202 }) : new HttpResponse(null, { status: 404 })),
    http.get(`${B}/api/files/:id/content`, ({ params }) => {
      const f = store.getFile(String(params.id));
      if (!f) return new HttpResponse(null, { status: 404 });
      if (f.status !== 'CLEAN') return new HttpResponse(null, { status: 403 });
      // Comme le vrai backend : renvoie l'URL (ici un blob local) au lieu des octets.
      const url = URL.createObjectURL(new Blob([`contenu de ${f.filename}`]));
      return HttpResponse.json({ url });
    }),
    http.put('http://mock.local/upload/:id', ({ params }) =>
      store.markUploaded(String(params.id)) ? new HttpResponse(null, { status: 200 }) : new HttpResponse(null, { status: 404 })),
  );
}
