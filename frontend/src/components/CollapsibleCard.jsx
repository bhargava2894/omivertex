import { AnimatePresence, motion } from 'framer-motion';
import { collapse, useMotionVariants } from '../motion.js';

export default function CollapsibleCard({ open, onToggle, header, children }) {
  const anim = useMotionVariants(collapse);
  return (
    <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
      <button type="button" className="staffing-toggle" onClick={onToggle} aria-expanded={open}>
        <span aria-hidden="true" className="staffing-toggle-arrow">
          ▸
        </span>
        {header}
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={anim.initial}
            animate={anim.animate}
            exit={anim.exit}
            style={{ overflow: 'hidden' }}
          >
            {children}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
