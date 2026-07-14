import { useEffect, useState } from 'react';

interface SearchBarProps {
  query: string;
  onSearch: (q: string) => void;
}

export function SearchBar({ query, onSearch }: SearchBarProps) {
  const [text, setText] = useState(query);
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    const id = setTimeout(() => onSearch(text), 300);
    return () => clearTimeout(id);
  }, [text, onSearch]);

  return (
    <div style={{ position: 'relative', width: 280, maxWidth: '100%' }}>
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="#8A96A4"
        strokeWidth="1.9"
        strokeLinecap="round"
        strokeLinejoin="round"
        style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)' }}
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m21 21-4.3-4.3" />
      </svg>
      <input
        value={text}
        onChange={(e) => setText(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        placeholder="Rechercher un fichier…"
        aria-label="Rechercher un fichier"
        style={{
          width: '100%',
          padding: '9px 12px 9px 36px',
          border: `1px solid ${focused ? '#005EA8' : '#E1E7EE'}`,
          borderRadius: 9,
          font: 'inherit',
          fontSize: 13.5,
          background: '#fff',
          outline: 'none',
        }}
      />
    </div>
  );
}
