import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach } from 'vitest';
import { server } from './mocks/server';
import { resetStore } from './mocks/store';

// server.listen() est appele de facon synchrone au chargement du module (et non
// dans beforeAll) : certains fichiers de test construisent leur client HTTP au
// top-level (avant que les hooks beforeAll ne s'executent), ce qui capture sinon
// une reference a fetch non patchee par MSW -> requetes reseau reelles (ENOTFOUND).
server.listen({ onUnhandledRequest: 'error' });
afterEach(() => {
  server.resetHandlers();
  resetStore();
});
afterAll(() => server.close());
