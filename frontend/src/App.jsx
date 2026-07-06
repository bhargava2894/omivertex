import { useEffect, useState, useCallback } from 'react';
import Icon from './components/Icon.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Associates from './pages/Associates.jsx';
import Clients from './pages/Clients.jsx';
import Projects from './pages/Projects.jsx';
import Allocations from './pages/Allocations.jsx';
import Positions from './pages/Positions.jsx';
import Settings from './pages/Settings.jsx';
import Login from './pages/Login.jsx';
import AccessRequests from './pages/AccessRequests.jsx';
import AuditLog from './pages/AuditLog.jsx';
import Profile from './pages/Profile.jsx';
import Taxonomy from './pages/Taxonomy.jsx';
import SkillReports from './pages/SkillReports.jsx';
import { api } from './api.js';
import { storedTheme, applyTheme, resolveTheme } from './theme.js';

const ROUTES = [
  {
    path: 'dashboard',
    label: 'Dashboard',
    icon: 'dashboard',
    component: Dashboard,
    sub: 'Resource overview across Softility',
  },
  {
    path: 'associates',
    label: 'Associates',
    icon: 'users',
    component: Associates,
    sub: 'Consultant roster and staffing status',
  },
  {
    path: 'clients',
    label: 'Clients',
    icon: 'building',
    component: Clients,
    sub: 'Master client list',
  },
  {
    path: 'projects',
    label: 'Projects',
    icon: 'briefcase',
    component: Projects,
    sub: 'Master project list by client',
  },
  {
    path: 'allocations',
    label: 'Allocations',
    icon: 'link',
    component: Allocations,
    sub: 'Assign associates to projects',
  },
  {
    path: 'demand',
    label: 'Demand',
    icon: 'target',
    component: Positions,
    sub: 'Open positions and bench matching',
  },
  {
    path: 'skill-reports',
    label: 'Skill Reports',
    icon: 'activity',
    component: SkillReports,
    sub: 'Proficiency distribution by category',
  },
  {
    path: 'taxonomy',
    label: 'Skill Taxonomy',
    icon: 'sheet',
    component: Taxonomy,
    sub: 'Skill categories and tools',
    adminOnly: true,
  },
  {
    path: 'access-requests',
    label: 'Access Requests',
    icon: 'shield',
    component: AccessRequests,
    sub: 'Manage pending user access requests',
    adminOnly: true,
  },
  {
    path: 'audit',
    label: 'Audit Log',
    icon: 'list',
    component: AuditLog,
    sub: 'Who changed what, and when',
    adminOnly: true,
  },
  {
    path: 'settings',
    label: 'Settings',
    icon: 'settings',
    component: Settings,
    sub: 'Appearance and data management',
  },
];

const routeByPath = Object.fromEntries(ROUTES.map((r) => [r.path, r]));

// Display grouping for the sidebar. A section with a null label renders no header
// (standalone). The Admin section is entirely admin-only, so it disappears for viewers.
const NAV_SECTIONS = [
  { label: null, items: ['dashboard'] },
  { label: 'Workforce', items: ['associates', 'taxonomy', 'skill-reports'] },
  { label: 'Delivery', items: ['clients', 'projects', 'allocations', 'demand'] },
  { label: 'Admin', items: ['access-requests', 'audit'] },
  { label: null, items: ['settings'] },
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
  const [user, setUser] = useState(undefined); // undefined = checking, null = logged out

  useEffect(() => {
    api
      .me()
      .then(setUser)
      .catch(() => setUser(null));
    const onUnauthorized = () => setUser(null);
    window.addEventListener('ov-unauthorized', onUnauthorized);
    return () => window.removeEventListener('ov-unauthorized', onUnauthorized);
  }, []);

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

  const logout = async () => {
    try {
      await api.logout();
    } finally {
      setUser(null);
    }
  };

  const canEdit = user?.role === 'ADMIN';
  const visibleRoutes = ROUTES.filter((r) => !r.adminOnly || canEdit);
  const baseRoute = route.split('?')[0];
  let active = visibleRoutes.find((r) => r.path === baseRoute);
  let isProfile = false;
  let profileId = null;
  if (!active && baseRoute.startsWith('associates/')) {
    active = visibleRoutes.find((r) => r.path === 'associates');
    isProfile = true;
    profileId = Number(baseRoute.split('/')[1]);
  }
  if (!active) {
    active = visibleRoutes[0];
  }
  const Page = isProfile ? Profile : active.component;
  const isDark = resolveTheme(theme) === 'dark';

  if (user === undefined) {
    return null; // session check in flight — avoid flashing the login page
  }
  if (user === null) {
    return <Login onLogin={setUser} />;
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <img src="/logo-mark.png" alt="" className="brand-logo" />
          <div>
            <div className="brand-name">OmiVertex</div>
            <div className="brand-sub">Softility Resource Hub</div>
          </div>
        </div>
        <nav className="sidebar-nav" aria-label="Primary">
          {NAV_SECTIONS.map((section) => {
            const items = section.items
              .map((p) => routeByPath[p])
              .filter((r) => r && (!r.adminOnly || canEdit));
            if (items.length === 0) return null;
            return (
              <div className="nav-group" key={section.label || items[0].path}>
                {section.label && <div className="nav-group-label">{section.label}</div>}
                {items.map((r) => (
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
              </div>
            );
          })}
        </nav>
        <div className="sidebar-footer">
          <div>Softility · Internal</div>
          <div className="sidebar-credit">Built by Bhargava Sista</div>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div>
            <h1>{isProfile ? 'Associate Profile' : active.label}</h1>
            <div className="topbar-sub">
              {isProfile ? 'Skills, certifications, and history' : active.sub}
            </div>
          </div>
          <div className="topbar-actions">
            <div className="user-chip" title={`Signed in as ${user.username}`}>
              <span className="user-avatar">{user.displayName.charAt(0)}</span>
              <span className="user-meta">
                <span className="user-name">{user.displayName}</span>
                <span className={`badge ${canEdit ? 'badge-blue' : 'badge-gray'}`}>
                  {canEdit ? 'Admin' : 'Read-only'}
                </span>
              </span>
            </div>
            <button
              className="icon-btn"
              onClick={toggleTheme}
              aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
              title={isDark ? 'Light theme' : 'Dark theme'}
            >
              <Icon name={isDark ? 'sun' : 'moon'} size={17} />
            </button>
            <button className="icon-btn" onClick={logout} aria-label="Sign out" title="Sign out">
              <Icon name="logout" size={17} />
            </button>
          </div>
        </header>
        <main className="content">
          {isProfile ? (
            <Page id={profileId} showToast={showToast} canEdit={canEdit} />
          ) : (
            <Page showToast={showToast} theme={theme} setTheme={setTheme} canEdit={canEdit} />
          )}
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
