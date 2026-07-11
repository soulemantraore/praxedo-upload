/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./test/setup.ts'],
    css: false,
    env: { VITE_API_BASE_URL: 'http://api.test', VITE_USE_MOCK: 'true', VITE_POLL_MS: '20' },
  },
});
