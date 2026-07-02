import { useEffect, useState, useCallback } from 'react';
import Icon from './components/Icon.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Associates from './pages/Associates.jsx';
import Clients from './pages/Clients.jsx';
import Projects from './pages/Projects.jsx';
import Allocations from './pages/Allocations.jsx';
import Settings from './pages/Settings.jsx';
import { storedTheme, applyTheme, resolveTheme } from './theme.js';

const ROUTES = [
  { path: 'dashboard', label: 'Dashboard', icon: 'dashboard', component: Dashboard, sub: 'Resource overview across Softility' },
  { path: 'associates', label: 'Associates', icon: 'users', component: Associates, sub: 'Consultant roster and staffing status' },
  { path: 'clients', label: 'Clients', icon: 'building', component: Clients, sub: 'Master client list' },
  { path: 'projects', label: 'Projects', icon: 'briefcase', component: Projects, sub: 'Master project list by client' },
  { path: 'allocations', label: 'Allocations', icon: 'link', component: Allocations, sub: 'Assign associates to projects' },
  { path: 'settings', label: 'Settings', icon: 'settings', component: Settings, sub: 'Appearance and data management' },
];

function useHashRoute() {
  const read = () => window.location.hash.replace(/^#\/?/, '') || 'dashboard';
  const [route, setRoute] = useState(read);
  useEffect(() => {
    const onChange = () => setRoute(read());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}

export default function App() {
  const route = useHashRoute();
  const [toast, setToast] = useState(null);
  const [theme, setThemeState] = useState(storedTheme);

  useEffect(() => {
    applyTheme(theme);
    if (theme !== 'system') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => applyTheme('system');
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [theme]);

  const setTheme = (value) => setThemeState(value);
  const toggleTheme = () => setTheme(resolveTheme(theme) === 'dark' ? 'light' : 'dark');

  const showToast = useCallback((message, isError = false) => {
    setToast({ message, isError });
    setTimeout(() => setToast(null), 3500);
  }, []);

  const active = ROUTES.find((r) => r.path === route) || ROUTES[0];
  const Page = active.component;
  const isDark = resolveTheme(theme) === 'dark';

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-brand" style={{ padding: '14px 16px', background: '#ffffff', borderBottom: '1px solid rgba(0, 0, 0, 0.06)', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
          <img src="/logo.jpg" alt="OmiVertex Logo" style={{ height: '42px', width: 'auto', display: 'block' }} />
        </div>
        <nav className="sidebar-nav" aria-label="Primary">
          {ROUTES.map((r) => (
            <button
              key={r.path}
              className={`nav-item ${r.path === active.path ? 'active' : ''}`}
              aria-current={r.path === active.path ? 'page' : undefined}
              onClick={() => (window.location.hash = `/${r.path}`)}
            >
              <Icon name={r.icon} size={18} />
              {r.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">Softility · Internal</div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div>
            <h1>{active.label}</h1>
            <div className="topbar-sub">{active.sub}</div>
          </div>
          <div className="topbar-actions">
            <button
              className="icon-btn"
              onClick={toggleTheme}
              aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
              title={isDark ? 'Light theme' : 'Dark theme'}
            >
              <Icon name={isDark ? 'sun' : 'moon'} size={17} />
            </button>
          </div>
        </header>
        <main className="content">
          <Page showToast={showToast} theme={theme} setTheme={setTheme} />
        </main>
      </div>

      {toast && (
        <div className={`toast ${toast.isError ? 'error' : ''}`} role="status" aria-live="polite">
          {toast.message}
        </div>
      )}
    </div>
  );
}
