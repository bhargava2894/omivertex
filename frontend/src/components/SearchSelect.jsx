import { useEffect, useRef, useState } from 'react';

/**
 * A searchable single-select combobox: type to filter, click or Enter to pick.
 * Unlike a native <select>, it matches the query anywhere in the label (so
 * "trans" finds "Transunion · Data Platform"), not just the first letter.
 *
 * options:  [{ value, label }]
 * value:    the currently selected value (matched by String equality)
 * onChange: (value) => void
 */
export default function SearchSelect({
  options,
  value,
  onChange,
  placeholder = 'Select…',
  invalid,
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlight, setHighlight] = useState(0);
  const ref = useRef(null);

  const selected = options.find((o) => String(o.value) === String(value));

  useEffect(() => {
    if (!open) return undefined;
    const onDoc = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  const q = query.trim().toLowerCase();
  const filtered = q ? options.filter((o) => o.label.toLowerCase().includes(q)) : options;
  const shown = filtered.slice(0, 100); // cap rendered rows for performance

  const choose = (o) => {
    onChange(o.value);
    setQuery('');
    setOpen(false);
  };

  return (
    <div className="search-select" ref={ref}>
      <input
        type="text"
        role="combobox"
        aria-expanded={open}
        autoComplete="off"
        className={invalid ? 'invalid' : ''}
        placeholder={placeholder}
        value={open ? query : selected ? selected.label : ''}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
          setHighlight(0);
        }}
        onFocus={() => {
          setQuery('');
          setOpen(true);
        }}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') {
            e.preventDefault();
            setOpen(true);
            setHighlight((h) => Math.min(h + 1, shown.length - 1));
          } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setHighlight((h) => Math.max(h - 1, 0));
          } else if (e.key === 'Enter') {
            e.preventDefault();
            if (shown[highlight]) choose(shown[highlight]);
          } else if (e.key === 'Escape') {
            setOpen(false);
          }
        }}
      />
      {open && (
        <ul className="search-select-menu" role="listbox">
          {shown.length === 0 ? (
            <li className="search-select-empty">No matches</li>
          ) : (
            shown.map((o, i) => (
              <li
                key={o.value}
                role="option"
                aria-selected={String(o.value) === String(value)}
                className={`search-select-option${i === highlight ? ' active' : ''}`}
                onMouseEnter={() => setHighlight(i)}
                onMouseDown={(e) => {
                  e.preventDefault(); // keep focus; select before the input blurs
                  choose(o);
                }}
              >
                {o.label}
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
