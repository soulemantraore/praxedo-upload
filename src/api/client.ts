import type { AppConfig } from '../config';
import type { FileView, PageResult, StatsView, UploadTicket, FileQuery } from './types';

export class ApiError extends Error {
  constructor(public status: number, message: string, public body?: string) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface RegisterUploadInput {
  filename: string;
  contentType: string;
  size: number;
}

export interface FileApi {
  getStats(): Promise<StatsView>;
  listFiles(query: FileQuery): Promise<PageResult<FileView>>;
  getFile(id: string): Promise<FileView>;
  registerUpload(input: RegisterUploadInput): Promise<UploadTicket>;
  uploadBytes(uploadUrl: string, file: File): Promise<void>;
  rescan(id: string): Promise<void>;
  downloadFile(id: string, filename: string): Promise<void>;
}

export function createFileApi(config: AppConfig, fetchImpl: typeof fetch = fetch): FileApi {
  const base = config.apiBaseUrl;
  const authHeaders = (): Record<string, string> => ({ 'X-API-Key': config.apiKey });

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const res = await fetchImpl(`${base}${path}`, {
      ...init,
      headers: { ...authHeaders(), ...(init?.headers ?? {}) },
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new ApiError(res.status, `HTTP ${res.status} on ${path}`, body);
    }
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    if (!text) return undefined as T;
    return JSON.parse(text) as T;
  }

  return {
    getStats: () => request<StatsView>('/api/files/stats'),

    listFiles: async (query) => {
      const p = new URLSearchParams();
      if (query.page != null) p.set('page', String(query.page));
      if (query.size != null) p.set('size', String(query.size));
      if (query.q) p.set('q', query.q);
      if (query.status) p.set('status', query.status);
      const qs = p.toString();
      const raw = await request<Omit<PageResult<FileView>, 'totalPages'>>(
        `/api/files${qs ? `?${qs}` : ''}`,
      );
      // Le backend ne garantit pas `totalPages` dans le JSON : on le derive ici
      // a partir du nombre total d'elements et de la taille de page effective.
      const size = raw.size || query.size || raw.items.length || 1;
      const totalPages = Math.max(1, Math.ceil(raw.totalElements / size));
      return { ...raw, totalPages };
    },

    getFile: (id) => request<FileView>(`/api/files/${encodeURIComponent(id)}`),

    registerUpload: (input) =>
      request<UploadTicket>('/api/files', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      }),

    uploadBytes: async (uploadUrl, file) => {
      // URL deja signee (GCS) ou proxy local : PAS de X-API-Key ici.
      const res = await fetchImpl(uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type || 'application/octet-stream' },
        body: file,
      });
      if (!res.ok) throw new ApiError(res.status, `Upload PUT failed (${res.status})`);
    },

    rescan: (id) =>
      request<void>(`/api/files/${encodeURIComponent(id)}/rescan`, { method: 'POST' }),

    downloadFile: async (id, filename) => {
      // GET /content -> 302 URL signee ; en dev/mock le fetch suit la redirection.
      // Compromis demo : on recupere les octets puis on declenche l'enregistrement.
      // Evolution : le backend renvoie l'URL signee en JSON pour que le navigateur
      // telecharge directement depuis GCS (offload des octets).
      const res = await fetchImpl(`${base}/api/files/${encodeURIComponent(id)}/content`, {
        headers: authHeaders(),
      });
      if (!res.ok) throw new ApiError(res.status, `Download failed (${res.status})`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    },
  };
}
