export interface AppConfig {
  apiBaseUrl: string;
  apiKey: string;
  portalName: string;
  clientName: string;
  clientOrg: string;
  useMock: boolean;
  pollIntervalMs: number;
}

export function readConfig(env: ImportMetaEnv): AppConfig {
  const trimSlash = (u: string) => u.replace(/\/+$/, '');
  return {
    apiBaseUrl: trimSlash(env.VITE_API_BASE_URL ?? 'http://localhost:8080'),
    apiKey: env.VITE_API_KEY ?? 'dev-local-key',
    portalName: env.VITE_PORTAL_NAME ?? 'Praxedo',
    clientName: env.VITE_CLIENT_NAME ?? 'Marie Colin',
    clientOrg: env.VITE_CLIENT_ORG ?? 'Bâtir SA',
    useMock: (env.VITE_USE_MOCK ?? 'true') !== 'false',
    pollIntervalMs: Number(env.VITE_POLL_MS ?? '2500'),
  };
}

export const config = readConfig(import.meta.env);
