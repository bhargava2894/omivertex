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

export default function Login({ onLogin }) {
  const reduceMotion = useReducedMotion();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
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
        key={shake}
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
            animate={reduceMotion ? {} : { y: [0, -6, 0] }}
            transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
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
      </motion.div>

      <div className="login-footer">Softility · Internal use only</div>
    </div>
  );
}
