import { useEffect } from 'react';
import { motion } from 'framer-motion';
import { modalPop, useMotionVariants } from '../motion.js';
import Icon from './Icon.jsx';

export default function Modal({ title, onClose, children, footer, size }) {
  const anim = useMotionVariants(modalPop);
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <motion.div
      className="modal-overlay"
      initial={anim.overlay.initial}
      animate={anim.overlay.animate}
      exit={anim.overlay.exit}
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <motion.div
        className={`modal ${size === 'lg' ? 'modal-lg' : ''}`}
        initial={anim.dialog.initial}
        animate={anim.dialog.animate}
        exit={anim.dialog.exit}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="modal-close" onClick={onClose} aria-label="Close dialog">
            <Icon name="x" size={18} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </motion.div>
    </motion.div>
  );
}
