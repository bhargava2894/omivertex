import { useEffect, useState, useCallback } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import Icon from './components/Icon.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Associates from './pages/Associates.jsx';
import Clients from './pages/Clients.jsx';
import Projects from './pages/Projects.jsx';
import Positions from './pages/Positions.jsx';
import Settings from './pages/Settings.jsx';
import Login from './pages/Login.jsx';
import AccessRequests from './pages/AccessRequests.jsx';
import AuditLog from './pages/AuditLog.jsx';
import Profile from './pages/Profile.jsx';
import Taxonomy from './pages/Taxonomy.jsx';
import Staffing from './pages/Staffing.jsx';
import SkillReports from './pages/SkillReports.jsx';
import MyProfile from './pages/MyProfile.jsx';
import ProfileChanges from './pages/ProfileChanges.jsx';
import { api } from './api.js';
import { storedTheme, applyTheme, resolveTheme } from './theme.js';
import { toastSlide, pageTransition, useMotionVariants } from './motion.js';

const ROUTES = [
  {
    path: 'dashboard',
    accent: 'blue',
    label: 'Dashboard',
    icon: 'dashboard',
    component: Dashboard,
    sub: 'Resource overview across Softility',
  },
  {
    path: 'associates',
    accent: 'green',
    label: 'Associates',
    icon: 'users',
    component: Associates,
    sub: 'Consultant roster and staffing status',
  },
  {
    path: 'clients',
    accent: 'amber',
    label: 'Clients',
    icon: 'building',
    component: Clients,
    sub: 'Master client list',
  },
  {
    path: 'projects',
    accent: 'violet',
    label: 'Projects',
    icon: 'briefcase',
    component: Projects,
    sub: 'Master project list by client',
  },
  {
    path: 'staffing',
    accent: 'cyan',
    label: 'Staffing & Allocations',
    icon: 'users',
    component: Staffing,
    sub: 'Billable and non-billable staffing by company and project — assign, edit, or roll off inline',
  },
  {
    path: 'demand',
    accent: 'rose',
    label: 'Demand',
    icon: 'target',
    component: Positions,
    sub: 'Open positions and bench matching',
  },
  {
    path: 'skill-reports',
    accent: 'green',
    label: 'Skill Reports',
    icon: 'activity',
    component: SkillReports,
    sub: 'Proficiency distribution by category',
  },
  {
    path: 'taxonomy',
    accent: 'amber',
    label: 'Skill Taxonomy',
    icon: 'sheet',
    component: Taxonomy,
    sub: 'Skill categories and tools',
    adminOnly: true,
  },
  {
    path: 'access-requests',
    accent: 'cyan',
    label: 'Access Requests',
    icon: 'shield',
    component: AccessRequests,
    sub: 'Manage pending user access requests',
    adminOnly: true,
  },
  {
    path: 'audit',
    accent: 'violet',
    label: 'Audit Log',
    icon: 'list',
    component: AuditLog,
    sub: 'Who changed what, and when',
    adminOnly: true,
  },
  {
    path: 'my-profile',
    accent: 'green',
    label: 'My Profile',
    icon: 'users',
    component: MyProfile,
    sub: 'Your associate profile and proposed changes',
    associateOnly: true,
  },
  {
    path: 'profile-changes',
    accent: 'rose',
    label: 'Profile Changes',
    icon: 'inbox',
    component: ProfileChanges,
    sub: 'Review proposed profile edits by associates',
    adminOnly: true,
  },
  {
    path: 'settings',
    accent: 'blue',
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
  { label: null, items: ['my-profile', 'dashboard'] },
  { label: 'Workforce', items: ['associates', 'taxonomy', 'skill-reports'] },
  { label: 'Delivery', items: ['clients', 'projects', 'staffing', 'demand'] },
  { label: 'Admin', items: ['profile-changes', 'access-requests', 'audit'] },
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
  const toastAnim = useMotionVariants(toastSlide);
  const pageAnim = useMotionVariants(pageTransition);
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

  useEffect(() => {
    if (user?.role === 'ASSOCIATE' && window.location.hash !== '#/my-profile') {
      window.location.hash = '/my-profile';
    }
  }, [user]);

  const canEdit = user?.role === 'ADMIN';
  const visibleRoutes = ROUTES.filter((r) => {
    if (user?.role === 'ASSOCIATE') {
      return r.path === 'my-profile';
    }
    return !r.associateOnly && (!r.adminOnly || canEdit);
  });
  const baseRoute = route.split('?')[0];
  let active = visibleRoutes.find((r) => r.path === baseRoute);
  let isProfile = false;
  let profileId = null;
  let focusSkillId = null;
  if (!active && baseRoute.startsWith('associates/')) {
    active = visibleRoutes.find((r) => r.path === 'associates');
    isProfile = true;
    profileId = Number(baseRoute.split('/')[1]);
  }
  // skill-reports/<id>: the dashboard's gap rows link straight to the skill's drill-down.
  if (!active && baseRoute.startsWith('skill-reports/')) {
    active = visibleRoutes.find((r) => r.path === 'skill-reports');
    focusSkillId = Number(baseRoute.split('/')[1]) || null;
  }
  // The old Allocations page merged into Staffing; keep its deep links working.
  if (!active && baseRoute === 'allocations') {
    active = visibleRoutes.find((r) => r.path === 'staffing');
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
          <div className="brand-logo-wrap">
            <img src="/logo-mark.png" alt="" className="brand-logo" />
          </div>
          <div>
            <div className="brand-name">OmiVertex</div>
            <div className="brand-sub">Softility Resource Hub</div>
          </div>
        </div>
        <nav className="sidebar-nav" aria-label="Primary">
          {NAV_SECTIONS.map((section) => {
            let items = section.items
              .map((p) => routeByPath[p])
              .filter((r) => r && (!r.adminOnly || canEdit));
            if (user?.role === 'ASSOCIATE') {
              items = items.filter((r) => r.associateOnly);
            } else {
              items = items.filter((r) => !r.associateOnly);
            }
            if (items.length === 0) return null;
            return (
              <div className="nav-group" key={section.label || items[0].path}>
                {section.label && <div className="nav-group-label">{section.label}</div>}
                {items.map((r) => (
                  <button
                    key={r.path}
                    className={`nav-item ${r.path === active.path ? 'active' : ''}`}
                    data-accent={r.accent}
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
                <span
                  className={`badge ${user.role === 'ADMIN' ? 'badge-blue' : user.role === 'ASSOCIATE' ? 'badge-green' : 'badge-gray'}`}
                >
                  {user.role === 'ADMIN'
                    ? 'Admin'
                    : user.role === 'ASSOCIATE'
                      ? 'Associate'
                      : 'Viewer'}
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
        <main className="content" style={{ overflowX: 'hidden' }}>
          <AnimatePresence mode="wait">
            <motion.div
              key={
                isProfile
                  ? `profile-${profileId}`
                  : // remount on a new deep link, so the focused skill re-seeds panel state
                    focusSkillId
                    ? `${active.path}-${focusSkillId}`
                    : active.path
              }
              initial={pageAnim.initial}
              animate={pageAnim.animate}
              exit={pageAnim.exit}
            >
              {isProfile ? (
                <Page id={profileId} showToast={showToast} canEdit={canEdit} />
              ) : (
                <Page
                  showToast={showToast}
                  theme={theme}
                  setTheme={setTheme}
                  canEdit={canEdit}
                  focusSkillId={focusSkillId}
                />
              )}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>

      <AnimatePresence>
        {toast && (
          <motion.div
            className={`toast ${toast.isError ? 'error' : ''}`}
            role="status"
            aria-live="polite"
            initial={toastAnim.initial}
            animate={toastAnim.animate}
            exit={toastAnim.exit}
          >
            {toast.message}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
