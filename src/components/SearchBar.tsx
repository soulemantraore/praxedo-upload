import { useEffect, useState } from 'react';
import type { FileStatus } from '../api/types';

interface SearchBarProps {
  onSearch: (q: string) => void;
  onStatus: (s: FileStatus | '') => void;
  status: FileStatus | '';
}

const STATUSES: { value: FileStatus | ''; label: string }[] = [
  { value: '', label: 'Tous les statuts' },
  { value: 'CLEAN', label: 'Valides' },
  { value: 'SCANNING', label: 'Scan en cours' },
  { value: 'PENDING', label: 'En attente' },
  { value: 'INFECTED', label: 'Bloques' },
  { value: 'SCAN_FAILED', label: 'Echec du scan' },
];

export function SearchBar({ onSearch, onStatus, status }: SearchBarProps) {
  const [text, setText] = useState('');
  useEffect(() => {
    const id = setTimeout(() => onSearch(text), 300);
    return () => clearTimeout(id);
  }, [text, onSearch]);

  return (
    <div style={{ display: 'flex', gap: 10, margin: '4px 0 12px' }}>
      <input
        className="btn"
        style={{ flex: 1, cursor: 'text' }}
        placeholder="Rechercher un fichier..."
        aria-label="Rechercher un fichier"
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
      <select className="btn" aria-label="Filtrer par statut" value={status} onChange={(e) => onStatus(e.target.value as FileStatus | '')}>
        {STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
      </select>
    </div>
  );
}
