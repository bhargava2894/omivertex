import { useEffect, useState, useCallback } from 'react';
import Icon from './components/Icon.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Associates from './pages/Associates.jsx';
import Clients from './pages/Clients.jsx';
import Projects from './pages/Projects.jsx';
import Allocations from './pages/Allocations.jsx';

const ROUTES = [
  { path: 'dashboard', label: 'Dashboard', icon: 'dashboard', component: Dashboard, sub: 'Resource overview across Softility' },
  { path: 'associates', label: 'Associates', icon: 'users', component: Associates, sub: 'Consultant roster and staffing status' },
  { path: 'clients', label: 'Clients', icon: 'building', component: Clients, sub: 'Master client list' },
  { path: 'projects', label: 'Projects', icon: 'briefcase', component: Projects, sub: 'Master project list by client' },
  { path: 'allocations', label: 'Allocations', icon: 'link', component: Allocations, sub: 'Assign associates to projects' },
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

  const showToast = useCallback((message, isError = false) => {
    setToast({ message, isError });
    setTimeout(() => setToast(null), 3500);
  }, []);

  const active = ROUTES.find((r) => r.path === route) || ROUTES[0];
  const Page = active.component;

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-mark">OV</div>
          <div>
            <div className="brand-name">OmiVertex</div>
            <div className="brand-sub">Softility Resource Hub</div>
          </div>
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
        </header>
        <main className="content">
          <Page showToast={showToast} />
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
