import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { useStats, useUploadFiles } from './hooks';
import { resetStore } from '../../test/mocks/store';

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

test('useStats charge les compteurs', async () => {
  resetStore([{ status: 'CLEAN' }, { status: 'PENDING' }]);
  const { result } = renderHook(() => useStats(), { wrapper: wrapper() });
  await waitFor(() => expect(result.current.isSuccess).toBe(true));
  expect(result.current.data!.total).toBe(2);
});

test('useUploadFiles enregistre puis pousse les octets', async () => {
  resetStore();
  const { result } = renderHook(() => useUploadFiles(), { wrapper: wrapper() });
  result.current.mutate([new File(['x'], 'a.pdf', { type: 'application/pdf' })]);
  await waitFor(() => expect(result.current.isSuccess).toBe(true));
  expect(result.current.data).toMatchObject({ total: 1, done: 1, errors: [] });
});
