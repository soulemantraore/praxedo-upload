import { QueryClient, useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { createFileApi } from './client';
import { config } from '../config';
import type { FileQuery, FileView, PageResult, StatsView } from './types';

export const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
});

export const api = createFileApi(config);

const hasActive = (items: FileView[]) =>
  items.some((f) => f.status === 'PENDING' || f.status === 'SCANNING');

export function useStats() {
  return useQuery<StatsView>({
    queryKey: ['stats'],
    queryFn: () => api.getStats(),
    refetchInterval: config.pollIntervalMs,
  });
}

export function useFiles(query: FileQuery) {
  return useQuery<PageResult<FileView>>({
    queryKey: ['files', query],
    queryFn: () => api.listFiles(query),
    placeholderData: (prev) => prev,   // evite le clignotement en pagination/recherche
    refetchInterval: (q) =>
      q.state.data && hasActive(q.state.data.items) ? config.pollIntervalMs : false,
  });
}

export interface UploadProgress {
  total: number;
  done: number;
  errors: { filename: string; message: string }[];
}

export function useUploadFiles() {
  const qc = useQueryClient();
  return useMutation<UploadProgress, Error, File[]>({
    mutationFn: async (files) => {
      const errors: UploadProgress['errors'] = [];
      let done = 0;
      for (const file of files) {
        try {
          const ticket = await api.registerUpload({
            filename: file.name,
            contentType: file.type || 'application/octet-stream',
            size: file.size,
          });
          await api.uploadBytes(ticket.uploadUrl, file);
          done++;
        } catch (e) {
          errors.push({ filename: file.name, message: (e as Error).message });
        }
      }
      return { total: files.length, done, errors };
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['files'] });
      qc.invalidateQueries({ queryKey: ['stats'] });
    },
  });
}

export function useRescan() {
  const qc = useQueryClient();
  return useMutation<void, Error, string>({
    mutationFn: (id) => api.rescan(id),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['files'] });
      qc.invalidateQueries({ queryKey: ['stats'] });
    },
  });
}
