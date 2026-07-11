import { useEffect, useRef, useState } from 'react';

/**
 * A searchable single-select combobox: type to filter, click or Enter to pick.
 * Unlike a native <select>, it matches the query anywhere in the label (so
 * "trans" finds "Transunion · Data Platform"), not just the first letter.
 *
 * Two modes:
 *  - Sync:  pass `options` [{ value, label }]; filtered in the browser.
 *  - Async: pass `onSearch(query) => Promise<[{ value, label }]>`; queried on the
 *    server as you type (debounced). Used for large lists (e.g. 500+ associates).
 *
 * value:    the currently selected value (matched by String equality)
 * onChange: (value) => void
 */
export default function SearchSelect({
  options,
  onSearch,
  onCreate,
  value,
  onChange,
  placeholder = 'Select…',
  invalid,
}) {
  const isAsync = typeof onSearch === 'function';
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [highlight, setHighlight] = useState(0);
  const [results, setResults] = useState([]); // async results
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [chosen, setChosen] = useState(null); // remembered {value,label} for async display
  const ref = useRef(null);
  const onSearchRef = useRef(onSearch);
  const reqId = useRef(0);

  useEffect(() => {
    onSearchRef.current = onSearch;
  });

  useEffect(() => {
    if (!open) return undefined;
    const onDoc = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  // Async: debounce the query and fetch, ignoring out-of-order responses.
  useEffect(() => {
    if (!isAsync || !open) return undefined;
    const rid = ++reqId.current;
    setLoading(true);
    const timer = setTimeout(() => {
      Promise.resolve(onSearchRef.current(query.trim()))
        .then((opts) => {
          if (rid !== reqId.current) return; // a newer query superseded this one
          setResults(Array.isArray(opts) ? opts : []);
          setLoading(false);
          setHighlight(0);
        })
        .catch(() => {
          if (rid !== reqId.current) return;
          setResults([]);
          setLoading(false);
        });
    }, 250);
    return () => clearTimeout(timer);
  }, [isAsync, open, query]);

  const q = query.trim().toLowerCase();
  const selected = !isAsync ? (options || []).find((o) => String(o.value) === String(value)) : null;
  const shown = isAsync
    ? results
    : (q ? (options || []).filter((o) => o.label.toLowerCase().includes(q)) : options || []).slice(
        0,
        100
      );

  const displayLabel = isAsync
    ? chosen && String(chosen.value) === String(value)
      ? chosen.label
      : ''
    : selected
      ? selected.label
      : '';

  const choose = (o) => {
    onChange(o.value);
    if (isAsync) setChosen(o);
    setQuery('');
    setOpen(false);
  };

  const term = query.trim();
  const exactMatch = shown.some((o) => o.label.toLowerCase() === term.toLowerCase());
  const canCreate = typeof onCreate === 'function' && term && !exactMatch;

  const create = async () => {
    if (!canCreate || creating) return;
    setCreating(true);
    try {
      const created = await onCreate(term); // parent creates it and returns { value, label }
      if (created) {
        if (isAsync) setChosen(created);
        choose(created);
      }
    } finally {
      setCreating(false);
    }
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
        value={open ? query : displayLabel}
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
      <span className="search-select-chevron">▼</span>
      {open && (
        <ul className="search-select-menu" role="listbox">
          {isAsync && loading ? (
            <li className="search-select-empty">Searching…</li>
          ) : (
            <>
              {shown.length === 0 && !canCreate && (
                <li className="search-select-empty">No matches</li>
              )}
              {shown.map((o, i) => (
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
              ))}
              {canCreate && (
                <li
                  className="search-select-create"
                  onMouseDown={(e) => {
                    e.preventDefault();
                    create();
                  }}
                >
                  {creating ? 'Creating…' : `+ Add “${term}”`}
                </li>
              )}
            </>
          )}
        </ul>
      )}
    </div>
  );
}
