import { useReducedMotion } from 'framer-motion';

// Single source of motion truth (AGENTS.md: one shared module per
// cross-cutting concern). No page defines its own durations or easings.
export const EASE = [0.4, 0, 0.2, 1]; // matches the CSS --ease feel
export const ENTER = 0.22;
export const EXIT = 0.15;

export const collapse = {
  initial: { height: 0, opacity: 0 },
  animate: { height: 'auto', opacity: 1, transition: { duration: ENTER, ease: EASE } },
  exit: { height: 0, opacity: 0, transition: { duration: EXIT, ease: EASE } },
};

export const modalPop = {
  overlay: {
    initial: { opacity: 0 },
    animate: { opacity: 1, transition: { duration: 0.18, ease: EASE } },
    exit: { opacity: 0, transition: { duration: 0.12, ease: EASE } },
  },
  dialog: {
    initial: { opacity: 0, scale: 0.96, y: 10 },
    animate: { opacity: 1, scale: 1, y: 0, transition: { duration: 0.18, ease: EASE } },
    exit: { opacity: 0, scale: 0.96, y: 0, transition: { duration: 0.12, ease: EASE } },
  },
};

export const toastSlide = {
  initial: { opacity: 0, y: 16 },
  animate: { opacity: 1, y: 0, transition: { duration: ENTER, ease: EASE } },
  exit: { opacity: 0, y: 16, transition: { duration: EXIT, ease: EASE } },
};

export const pageTransition = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0, transition: { duration: ENTER, ease: EASE } },
  exit: { opacity: 0, y: -8, transition: { duration: EXIT, ease: EASE } },
};

export const chatMessage = {
  initial: { opacity: 0, y: 12, scale: 0.97 },
  animate: { opacity: 1, y: 0, scale: 1, transition: { duration: ENTER, ease: EASE } },
  exit: { opacity: 0, scale: 0.95, transition: { duration: EXIT, ease: EASE } },
};

export const listContainer = {
  hidden: {},
  show: { transition: { staggerChildren: 0.04 } },
};

export const listItem = {
  hidden: { opacity: 0, y: 8 },
  show: { opacity: 1, y: 0, transition: { duration: ENTER, ease: EASE } },
};

export const badgePop = {
  initial: { scale: 0.85, opacity: 0 },
  animate: { scale: 1, opacity: 1, transition: { type: 'spring', stiffness: 400, damping: 22 } },
};

function withZeroDurations(node) {
  if (Array.isArray(node) || node === null || typeof node !== 'object') return node;
  const out = {};
  for (const [k, v] of Object.entries(node)) out[k] = withZeroDurations(v);
  if ('duration' in out) out.duration = 0;
  return out;
}

// The CSS reduced-motion kill-switch can't reach framer-motion (it animates
// inline styles), so every animated component gets its variants through here.
export function useMotionVariants(variants) {
  const reduce = useReducedMotion();
  return reduce ? withZeroDurations(variants) : variants;
}
