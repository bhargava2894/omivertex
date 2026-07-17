import { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useMotionVariants, chatMessage } from '../motion.js';
import { api } from '../api.js';
import Icon from './Icon.jsx';

const SUGGESTIONS = [
  'Who is on the bench right now?',
  'Which open positions have no bench match?',
  'Who rolls off a project in the next 30 days?',
  'Summarize our biggest skill gaps.',
  'Whose certifications expire soon?',
];

/** Friendly progress labels per read tool; anything unknown gets the generic line. */
const TOOL_LABELS = {
  search_associates: 'Searching associates…',
  get_associate_detail: 'Reading a profile…',
  get_project_detail: 'Reading a project…',
  list_rolloffs: 'Checking upcoming roll-offs…',
  list_open_positions: 'Looking up open positions…',
  get_position_matches: 'Ranking bench matches…',
  get_position_match_summary: 'Checking bench matches for every open position…',
  list_clients: 'Listing clients…',
  list_projects: 'Listing projects…',
  get_skill_gaps: 'Analyzing skill gaps…',
  list_expiring_certifications: 'Checking certifications…',
  get_workforce_summary: 'Compiling the workforce summary…',
  list_bench_aging: 'Reviewing the bench…',
};

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

export default function AssistantChat({ showToast, canEdit }) {
  const [messages, setMessages] = useState([]); // { role, content, action?, actionDone? }
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [activity, setActivity] = useState('');
  const [isFullScreen, setIsFullScreen] = useState(false);
  const messageAnim = useMotionVariants(chatMessage);

  useEffect(() => {
    if (isFullScreen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isFullScreen]);

  const logRef = useRef(null);

  const ask = async (text) => {
    const question = (text || '').trim();
    if (!question || busy) return;
    setBusy(true);
    setInput('');
    setActivity('Thinking…');
    const history = messages;
    setMessages((m) => [...m, { role: 'user', content: question }]);
    try {
      const { reply, proposedAction } = await api.askAssistantStream(question, history, (tool) =>
        setActivity(TOOL_LABELS[tool] || 'Looking things up…')
      );
      setMessages((m) => [...m, { role: 'model', content: reply, action: proposedAction }]);
    } catch (err) {
      setMessages((m) => m.slice(0, -1));
      showToast(err.message, true);
    } finally {
      setBusy(false);
      setActivity('');
      setTimeout(() => logRef.current?.scrollTo(0, logRef.current.scrollHeight), 50);
    }
  };

  const confirmAction = async (index) => {
    const action = messages[index]?.action;
    if (!action || busy) return;
    setBusy(true);
    try {
      if (action.type === 'CREATE_ALLOCATION') {
        await api.create('allocations', {
          associateId: action.associateId,
          projectId: action.projectId,
          billable: action.billable,
          allocationPercent: action.percent,
          startDate: action.startDate,
          endDate: action.endDate,
        });
      } else if (action.type === 'END_ALLOCATION' || action.type === 'EDIT_ALLOCATION') {
        await api.update('allocations', action.allocationId, {
          billable: action.billable,
          allocationPercent: action.percent,
          startDate: action.startDate,
          endDate: action.endDate,
        });
      } else if (action.type === 'CREATE_POSITION') {
        await api.create('positions', {
          title: action.positionTitle,
          projectId: action.projectId,
          billable: action.billable,
          allocationPercent: action.percent,
          startDate: action.startDate,
          endDate: action.endDate,
          headcount: 1,
          skills: action.skillId
            ? [{ skillId: action.skillId, minProficiency: action.minProficiency, required: true }]
            : [],
        });
      } else {
        await api.create(`positions/${action.positionId}/fill`, {
          associateId: action.associateId,
        });
      }
      setMessages((m) => m.map((msg, i) => (i === index ? { ...msg, actionDone: true } : msg)));
      showToast(`Done — ${action.summary}`);
    } catch (err) {
      showToast(err.message, true);
    } finally {
      setBusy(false);
    }
  };

  const dismissAction = (index) => {
    setMessages((m) => m.map((msg, i) => (i === index ? { ...msg, action: null } : msg)));
  };

  return (
    <>
      {isFullScreen && <div className="mirai-backdrop" onClick={() => setIsFullScreen(false)} />}
      <div className={`card panel mirai-card ${isFullScreen ? 'mirai-full-screen' : ''}`}>
        <div
          className="mirai-band"
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
        >
          <div>
            <div className="mirai-brand">
              <span className="mirai-mark" aria-hidden="true">
                <Icon name="sparkles" size={14} />
              </span>
              <span className="mirai-wordmark">Mirai</span>
            </div>
            <p className="mirai-tagline">orchestrate with intelligence</p>
          </div>
          <button
            type="button"
            className="mirai-expand-btn"
            onClick={() => setIsFullScreen(!isFullScreen)}
            title={isFullScreen ? 'Exit full screen' : 'Open full screen'}
          >
            <Icon name={isFullScreen ? 'minimize' : 'maximize'} size={16} />
          </button>
        </div>
        <div className="mirai-body">
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
                maxHeight: isFullScreen ? 'none' : '320px',
                flex: isFullScreen ? '1' : 'unset',
                overflowY: 'auto',
                display: 'flex',
                flexDirection: 'column',
                gap: '10px',
                marginBottom: '10px',
              }}
            >
              <AnimatePresence mode="popLayout">
                {messages.map((m, i) => (
                  <motion.div
                    key={i}
                    layout
                    initial={messageAnim.initial}
                    animate={messageAnim.animate}
                    exit={messageAnim.exit}
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
                    {m.action && (
                      <div
                        style={{
                          marginTop: '8px',
                          padding: '10px 12px',
                          borderRadius: '8px',
                          border: '1px solid var(--color-primary)',
                          background: 'var(--color-primary-soft)',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: '6px',
                        }}
                      >
                        <strong style={{ fontSize: '13px' }}>{m.action.summary}</strong>
                        {(m.action.warnings || []).map((w) => (
                          <div key={w} style={{ fontSize: '12px', color: 'var(--color-warn)' }}>
                            ⚠ {w}
                          </div>
                        ))}
                        {m.actionDone ? (
                          <div style={{ fontSize: '12px', color: 'var(--color-accent)' }}>
                            ✓ Done
                          </div>
                        ) : (
                          <div style={{ display: 'flex', gap: '8px' }}>
                            {canEdit && (
                              <button
                                className="btn btn-primary btn-sm"
                                disabled={busy}
                                onClick={() => confirmAction(i)}
                              >
                                Confirm
                              </button>
                            )}
                            <button
                              className="btn btn-ghost btn-sm"
                              disabled={busy}
                              onClick={() => dismissAction(i)}
                            >
                              Dismiss
                            </button>
                            {!canEdit && (
                              <span className="stat-hint" style={{ alignSelf: 'center' }}>
                                Requires admin to confirm
                              </span>
                            )}
                          </div>
                        )}
                      </div>
                    )}
                  </motion.div>
                ))}
              </AnimatePresence>
              {busy && (
                <div className="mirai-typing-row">
                  <div className="mirai-typing" aria-hidden="true">
                    <span />
                    <span />
                    <span />
                  </div>
                  <span className="mirai-activity" aria-live="polite">
                    {activity}
                  </span>
                </div>
              )}
            </div>
          )}
          <form
            className="assistant-form"
            onSubmit={(e) => {
              e.preventDefault();
              ask(input);
            }}
          >
            <input
              className="assistant-input"
              value={input}
              maxLength={2000}
              placeholder="Ask Mirai…"
              onChange={(e) => setInput(e.target.value)}
              disabled={busy}
            />
            <button
              className="btn btn-primary assistant-btn"
              type="submit"
              disabled={busy || !input.trim()}
            >
              {busy ? '…' : <Icon name="send" size={16} />}
            </button>
          </form>
        </div>
      </div>
    </>
  );
}
