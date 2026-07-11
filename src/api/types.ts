export type FileStatus =
  | 'PENDING' | 'SCANNING' | 'CLEAN' | 'INFECTED' | 'SCAN_FAILED' | 'EXPIRED';

export interface ScanVerdict {
  engine: string | null;
  verdict: 'CLEAN' | 'INFECTED' | null;
  threatName: string | null;
  scannedAt: string | null;
}

export interface FileView {
  id: string;
  filename: string;
  contentType: string;
  size: number;                 // octets
  status: FileStatus;
  batchId: string | null;
  scanVerdict: ScanVerdict | null;
  createdAt: string;            // ISO 8601
  updatedAt: string;
  scannedAt: string | null;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export interface StatsView {
  total: number;
  clean: number;
  scanning: number;
  pending: number;
  blocked: number;
}

export interface UploadTicket {
  id: string;
  filename: string;
  status: FileStatus;
  uploadUrl: string;
  uploadExpiresAt: string;
}

export interface FileQuery {
  page?: number;
  size?: number;
  q?: string;
  status?: FileStatus | '';
  batchId?: string;
}
