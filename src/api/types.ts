export type FileStatus =
  | 'PENDING' | 'SCANNING' | 'CLEAN' | 'INFECTED' | 'SCAN_FAILED' | 'EXPIRED';

// Forme exacte renvoyee par le backend (application/dto/FileViews.FileView) :
// le verdict d'analyse est aplati en `infected` + `threatName` (pas d'objet imbrique).
export interface FileView {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;            // octets
  status: FileStatus;
  infected: boolean;
  threatName: string | null;
  createdAt: string;            // ISO 8601
  scannedAt: string | null;     // null tant qu'aucune analyse n'a abouti
}

// Le backend serialise { items, page, size, totalElements } (domain/file/PageResult).
// `totalPages` est derive cote client (le backend ne le garantit pas dans le JSON).
export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface StatsView {
  total: number;
  clean: number;
  scanning: number;
  pending: number;
  blocked: number;
}

// Reponse de POST /api/files (application/dto/UploadCommands.UploadRegistration).
// Pas de `filename` : le client garde le nom du fichier localement.
export interface UploadTicket {
  id: string;
  status: FileStatus;
  uploadUrl: string;
  uploadExpiresAt: string;
}

export interface FileQuery {
  page?: number;
  size?: number;
  q?: string;
  status?: FileStatus | '';
}
