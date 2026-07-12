import { motion } from 'framer-motion';
import { useMotionVariants, badgePop } from '../motion.js';

const tones = {
  ACTIVE: 'badge-green',
  INACTIVE: 'badge-gray',
  ON_HOLD: 'badge-amber',
  COMPLETED: 'badge-blue',
  ONSHORE: 'badge-blue',
  OFFSHORE: 'badge-amber',
  Billable: 'badge-green',
  'Non-billable': 'badge-amber',
  Bench: 'badge-red',
  Current: 'badge-green',
  Ended: 'badge-gray',
};

export default function Badge({ value, label, tone }) {
  const cls = tone ? `badge-${tone}` : tones[value] || 'badge-gray';
  const anim = useMotionVariants(badgePop);

  return (
    <motion.span
      className={`badge ${cls}`}
      initial={anim.initial}
      animate={anim.animate}
      style={{ display: 'inline-block' }}
    >
      {label || String(value).replace('_', ' ')}
    </motion.span>
  );
}
