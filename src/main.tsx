import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { config } from './config';
import { queryClient } from './api/hooks';
import './styles.css';

async function enableMockIfNeeded() {
  if (!config.useMock) return;
  try {
    const { makeWorker } = await import('../test/mocks/browser');
    await makeWorker(config.apiBaseUrl).start({ onUnhandledRequest: 'bypass' });
  } catch (err) {
    console.error('[demo] MSW worker failed to start; rendering without mock', err);
  }
}

function render() {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </React.StrictMode>,
  );
}

enableMockIfNeeded().finally(render);
