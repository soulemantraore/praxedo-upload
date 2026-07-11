import { readConfig } from './config';

test('lit la config depuis les variables VITE_ avec valeurs par defaut', () => {
  const cfg = readConfig({
    VITE_API_BASE_URL: 'http://api.test',
    VITE_API_KEY: 'k',
    VITE_PORTAL_NAME: 'Portail',
    VITE_CLIENT_NAME: 'Jean Test',
    VITE_CLIENT_ORG: 'ACME',
    VITE_USE_MOCK: 'false',
    VITE_POLL_MS: '3000',
  } as unknown as ImportMetaEnv);
  expect(cfg).toEqual({
    apiBaseUrl: 'http://api.test',
    apiKey: 'k',
    portalName: 'Portail',
    clientName: 'Jean Test',
    clientOrg: 'ACME',
    useMock: false,
    pollIntervalMs: 3000,
  });
});

test('retire le slash final et applique les defauts', () => {
  const cfg = readConfig({ VITE_API_BASE_URL: 'http://api.test/' } as unknown as ImportMetaEnv);
  expect(cfg.apiBaseUrl).toBe('http://api.test');
  expect(cfg.useMock).toBe(true);
  expect(cfg.pollIntervalMs).toBe(2500);
  expect(cfg.portalName).toBe('Praxedo');
  expect(cfg.clientName).toBe('Marie Colin');
  expect(cfg.clientOrg).toBe('Bâtir SA');
});
