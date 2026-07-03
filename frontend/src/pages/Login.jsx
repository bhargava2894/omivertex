import { useState } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';
import { api } from '../api.js';
import Icon from '../components/Icon.jsx';

const container = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08, delayChildren: 0.15 } },
};

const item = {
  hidden: { opacity: 0, y: 18 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 260, damping: 24 } },
};

const MOCK_ACCOUNTS = [
  { name: 'Priya Sharma', email: 'priya.sharma@softility.com' },
  { name: 'Rahul Verma', email: 'rahul.verma@softility.com' },
  { name: 'Alex Turner', email: 'alex.turner@softility.com' },
];

const GoogleLogo = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" className="google-logo-svg">
    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
  </svg>
);

export default function Login({ onLogin }) {
  const reduceMotion = useReducedMotion();
  const [loginMode, setLoginMode] = useState('form'); // form, google-chooser, google-custom, pending
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [googleName, setGoogleName] = useState('');
  const [googleEmail, setGoogleEmail] = useState('');
  const [pendingEmail, setPendingEmail] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [shake, setShake] = useState(0);

  const submit = async (e) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const user = await api.login(username.trim(), password);
      onLogin(user);
    } catch (err) {
      setError(err.message);
      setShake((s) => s + 1);
    } finally {
      setBusy(false);
    }
  };

  const handleGoogleSelect = async (account) => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const user = await api.googleLogin(account.email, account.name);
      onLogin(user);
    } catch (err) {
      if (err.message.toLowerCase().includes('pending') || err.message.toLowerCase().includes('requested') || err.status === 401) {
        setPendingEmail(account.email);
        setLoginMode('pending');
      } else {
        setError(err.message);
        setShake((s) => s + 1);
      }
    } finally {
      setBusy(false);
    }
  };

  const handleGoogleCustomSubmit = async (e) => {
    e.preventDefault();
    const email = googleEmail.trim().toLowerCase();
    if (!email.endsWith('@softility.com')) {
      setError('Only @softility.com Google accounts are allowed.');
      setShake((s) => s + 1);
      return;
    }
    await handleGoogleSelect({ name: googleName.trim(), email });
  };

  return (
    <div className="login-page">
      {!reduceMotion && (
        <>
          <motion.div
            className="login-blob blob-a"
            animate={{ x: [0, 60, -30, 0], y: [0, -40, 30, 0], scale: [1, 1.15, 0.95, 1] }}
            transition={{ duration: 22, repeat: Infinity, ease: 'easeInOut' }}
          />
          <motion.div
            className="login-blob blob-b"
            animate={{ x: [0, -50, 40, 0], y: [0, 35, -25, 0], scale: [1, 0.9, 1.12, 1] }}
            transition={{ duration: 26, repeat: Infinity, ease: 'easeInOut' }}
          />
          <motion.div
            className="login-blob blob-c"
            animate={{ x: [0, 40, -40, 0], y: [0, 30, -35, 0] }}
            transition={{ duration: 30, repeat: Infinity, ease: 'easeInOut' }}
          />
        </>
      )}

      <motion.div
        key={`${shake}-${loginMode}`}
        className="login-card"
        variants={container}
        initial="hidden"
        animate="show"
        {...(shake > 0 && !reduceMotion
          ? { initial: { x: 0 }, animate: { x: [0, -10, 10, -7, 7, -3, 0] }, transition: { duration: 0.45 } }
          : {})}
      >
        <motion.div variants={item} className="login-brand">
          <motion.img
            src="/logo-mark.png"
            alt=""
            className="login-logo"
            animate={reduceMotion ? {} : { y: [0, -6, 0], rotate: 360 }}
            transition={{
              y: { duration: 5, repeat: Infinity, ease: 'easeInOut' },
              rotate: { duration: 16, repeat: Infinity, ease: 'linear' },
            }}
          />
          <h1>OmiVertex</h1>
          <p>Softility Resource Hub — sign in to continue</p>
        </motion.div>

        <AnimatePresence>
          {error && (
            <motion.div
              className="form-alert"
              role="alert"
              initial={{ opacity: 0, height: 0, marginBottom: 0 }}
              animate={{ opacity: 1, height: 'auto', marginBottom: 14 }}
              exit={{ opacity: 0, height: 0, marginBottom: 0 }}
            >
              {error}
            </motion.div>
          )}
        </AnimatePresence>

        {loginMode === 'form' && (
          <>
            <form onSubmit={submit}>
              <motion.div variants={item} className="field login-field">
                <label htmlFor="login-user">Username</label>
                <input
                  id="login-user"
                  autoComplete="username"
                  autoFocus
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="admin or viewer"
                  required
                />
              </motion.div>

              <motion.div variants={item} className="field login-field">
                <label htmlFor="login-pass">Password</label>
                <div className="password-wrap">
                  <input
                    id="login-pass"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    required
                  />
                  <button
                    type="button"
                    className="password-toggle"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    <Icon name={showPassword ? 'eyeOff' : 'eye'} size={16} />
                  </button>
                </div>
              </motion.div>

              <motion.div variants={item}>
                <motion.button
                  type="submit"
                  className="btn btn-primary login-submit"
                  disabled={busy}
                  whileHover={reduceMotion ? {} : { scale: 1.015 }}
                  whileTap={reduceMotion ? {} : { scale: 0.98 }}
                >
                  {busy ? 'Signing in…' : 'Sign In'}
                </motion.button>
              </motion.div>
            </form>

            <motion.div variants={item} className="google-login-divider">
              or
            </motion.div>

            <motion.div variants={item}>
              <button
                type="button"
                className="btn-google"
                onClick={() => {
                  setError(null);
                  setLoginMode('google-chooser');
                }}
              >
                <GoogleLogo /> Sign in with Google
              </button>
            </motion.div>
          </>
        )}

        {loginMode === 'google-chooser' && (
          <div style={{ animation: 'fade-in 0.25s ease' }}>
            <motion.div variants={item} className="google-chooser-title">
              Sign in with Google
            </motion.div>
            <div className="google-account-list">
              {MOCK_ACCOUNTS.map((acc) => (
                <button
                  key={acc.email}
                  type="button"
                  className="google-account-item"
                  onClick={() => handleGoogleSelect(acc)}
                  disabled={busy}
                >
                  <span className="google-account-avatar">
                    {acc.name.charAt(0).toUpperCase()}
                  </span>
                  <span className="google-account-info">
                    <span className="google-account-name">{acc.name}</span>
                    <span className="google-account-email">{acc.email}</span>
                  </span>
                </button>
              ))}
            </div>
            <motion.div variants={item} style={{ textAlign: 'center', marginBottom: '14px' }}>
              <button
                type="button"
                className="btn-google"
                onClick={() => {
                  setError(null);
                  setLoginMode('google-custom');
                }}
              >
                Use another account
              </button>
            </motion.div>
            <motion.div variants={item} style={{ textAlign: 'left' }}>
              <button
                type="button"
                className="btn-back-link"
                onClick={() => {
                  setError(null);
                  setLoginMode('form');
                }}
              >
                ← Back to standard login
              </button>
            </motion.div>
          </div>
        )}

        {loginMode === 'google-custom' && (
          <form onSubmit={handleGoogleCustomSubmit} style={{ animation: 'fade-in 0.25s ease' }}>
            <motion.div variants={item} className="google-chooser-title">
              Sign in with Google
            </motion.div>
            <motion.div variants={item} className="field login-field">
              <label htmlFor="google-custom-name">Full Name</label>
              <input
                id="google-custom-name"
                value={googleName}
                onChange={(e) => setGoogleName(e.target.value)}
                placeholder="e.g. John Doe"
                required
                autoFocus
              />
            </motion.div>
            <motion.div variants={item} className="field login-field">
              <label htmlFor="google-custom-email">Google Email Address</label>
              <input
                id="google-custom-email"
                type="email"
                value={googleEmail}
                onChange={(e) => setGoogleEmail(e.target.value)}
                placeholder="username@softility.com"
                required
              />
            </motion.div>
            <motion.div variants={item}>
              <motion.button
                type="submit"
                className="btn btn-primary login-submit"
                disabled={busy}
                whileHover={reduceMotion ? {} : { scale: 1.015 }}
                whileTap={reduceMotion ? {} : { scale: 0.98 }}
              >
                {busy ? 'Connecting…' : 'Proceed to Sign In'}
              </motion.button>
            </motion.div>
            <motion.div variants={item} style={{ textAlign: 'left', marginTop: '14px' }}>
              <button
                type="button"
                className="btn-back-link"
                onClick={() => {
                  setError(null);
                  setLoginMode('google-chooser');
                }}
              >
                ← Back to accounts
              </button>
            </motion.div>
          </form>
        )}

        {loginMode === 'pending' && (
          <div className="pending-request-card" style={{ animation: 'fade-in 0.25s ease' }}>
            <div className="pending-request-icon">
              <Icon name="shield" size={32} />
            </div>
            <h2>Access Requested</h2>
            <p>
              Your request to access OmiVertex as a Viewer using email <strong>{pendingEmail}</strong> has been registered.
              <br /><br />
              As this system is for internal HR purposes only, the Admin must approve your request before you can sign in.
            </p>
            <button
              type="button"
              className="btn btn-primary"
              style={{ width: '100%' }}
              onClick={() => {
                setError(null);
                setLoginMode('form');
              }}
            >
              Back to Login
            </button>
          </div>
        )}

        {loginMode === 'form' && (
          <motion.div variants={item} className="login-roles">
            <div className="login-role">
              <span className="badge badge-blue">Super Admin</span>
              <span>Full view &amp; edit access</span>
            </div>
            <div className="login-role">
              <span className="badge badge-gray">User</span>
              <span>Read-only access</span>
            </div>
          </motion.div>
        )}
      </motion.div>

      <div className="login-footer">Developed by Bhargava Sista | Softility · Internal use only</div>
    </div>
  );
}
