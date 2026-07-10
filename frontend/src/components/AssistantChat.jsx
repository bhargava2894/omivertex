import { useRef, useState } from 'react';
import { api } from '../api.js';
import Icon from './Icon.jsx';

const SUGGESTIONS = [
  'Who is on the bench right now?',
  'Which open positions have no bench match?',
  'Who rolls off a project in the next 30 days?',
  'Summarize our biggest skill gaps.',
];

/** Renders reply text: blank-line paragraphs, "- " bullets, **bold** — no innerHTML. */
function ReplyText({ text }) {
  const lines = (text || '').split('\n');
  const renderInline = (line, key) => {
    const parts = line.split(/(\*\*[^*]+\*\*)/g).filter(Boolean);
    return (
      <span key={key}>
        {parts.map((p, i) =>
          p.startsWith('**') && p.endsWith('**') ? <strong key={i}>{p.slice(2, -2)}</strong> : p
        )}
      </span>
    );
  };
  const blocks = [];
  let bullets = [];
  lines.forEach((line, i) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      bullets.push(renderInline(trimmed.slice(2), i));
    } else {
      if (bullets.length) {
        blocks.push(
          <ul key={`ul-${i}`} style={{ margin: '4px 0', paddingLeft: '18px' }}>
            {bullets.map((b, j) => (
              <li key={j}>{b}</li>
            ))}
          </ul>
        );
        bullets = [];
      }
      if (trimmed) {
        blocks.push(
          <p key={i} style={{ margin: '4px 0' }}>
            {renderInline(trimmed, i)}
          </p>
        );
      }
    }
  });
  if (bullets.length) {
    blocks.push(
      <ul key="ul-end" style={{ margin: '4px 0', paddingLeft: '18px' }}>
        {bullets.map((b, j) => (
          <li key={j}>{b}</li>
        ))}
      </ul>
    );
  }
  return <>{blocks}</>;
}

export default function AssistantChat({ showToast }) {
  const [messages, setMessages] = useState([]); // { role: 'user'|'model', content }
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const logRef = useRef(null);

  const ask = async (text) => {
    const question = (text || '').trim();
    if (!question || busy) return;
    setBusy(true);
    setInput('');
    const history = messages;
    setMessages((m) => [...m, { role: 'user', content: question }]);
    try {
      const { reply } = await api.askAssistant(question, history);
      setMessages((m) => [...m, { role: 'model', content: reply }]);
    } catch (err) {
      setMessages((m) => m.slice(0, -1));
      showToast(err.message, true);
    } finally {
      setBusy(false);
      setTimeout(() => logRef.current?.scrollTo(0, logRef.current.scrollHeight), 50);
    }
  };

  return (
    <div className="card panel" style={{ gridColumn: '1 / -1' }}>
      <h2>
        <Icon name="sparkles" size={15} /> Ask OmiVertex AI
      </h2>
      <p className="stat-hint" style={{ marginTop: 0 }}>
        Natural-language questions over the live workforce data — bench, projects, demand,
        roll-offs.
      </p>
      {messages.length === 0 && (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '10px' }}>
          {SUGGESTIONS.map((s) => (
            <button key={s} className="btn btn-ghost btn-sm" onClick={() => ask(s)}>
              {s}
            </button>
          ))}
        </div>
      )}
      {messages.length > 0 && (
        <div
          ref={logRef}
          style={{
            maxHeight: '320px',
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column',
            gap: '10px',
            marginBottom: '10px',
          }}
        >
          {messages.map((m, i) => (
            <div
              key={i}
              style={{
                alignSelf: m.role === 'user' ? 'flex-end' : 'flex-start',
                maxWidth: '85%',
                padding: '8px 12px',
                borderRadius: '10px',
                background:
                  m.role === 'user' ? 'var(--color-primary-soft)' : 'var(--color-surface)',
                border: '1px solid var(--color-border)',
                fontSize: '14px',
              }}
            >
              {m.role === 'model' && <Icon name="sparkles" size={12} />}{' '}
              <ReplyText text={m.content} />
            </div>
          ))}
          {busy && <div className="stat-hint">Thinking…</div>}
        </div>
      )}
      <form
        style={{ display: 'flex', gap: '8px' }}
        onSubmit={(e) => {
          e.preventDefault();
          ask(input);
        }}
      >
        <input
          style={{ flex: 1 }}
          value={input}
          maxLength={2000}
          placeholder="e.g. How many people are on the bench, and what projects are running?"
          onChange={(e) => setInput(e.target.value)}
          disabled={busy}
        />
        <button className="btn btn-primary" type="submit" disabled={busy || !input.trim()}>
          {busy ? '…' : 'Ask'}
        </button>
      </form>
    </div>
  );
}
