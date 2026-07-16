export interface AppConfig {
  apiBaseUrl: string;
  apiKey: string;
  portalName: string;
  useMock: boolean;
  pollIntervalMs: number;
}

export function readConfig(env: ImportMetaEnv): AppConfig {
  const trimSlash = (u: string) => u.replace(/\/+$/, '');
  return {
    apiBaseUrl: trimSlash(env.VITE_API_BASE_URL ?? 'http://localhost:8080'),
    apiKey: env.VITE_API_KEY ?? 'dev-local-key',
    portalName: env.VITE_PORTAL_NAME ?? 'Praxedo',
    useMock: (env.VITE_USE_MOCK ?? 'true') !== 'false',
    pollIntervalMs: Number(env.VITE_POLL_MS ?? '2500'),
  };
}

export const config = readConfig(import.meta.env);

// Plafond d'upload en octets. DOIT rester aligne avec storage.max-upload-size du backend
// (Spring DataSize "1GB" = 1 073 741 824 octets). Affiche "1 Go" cote UI.
export const MAX_UPLOAD_BYTES = 1024 * 1024 * 1024;
