import { useEffect, useState } from 'react';
import { animate } from 'framer-motion';

export default function AnimatedNumber({ value }) {
  const [displayValue, setDisplayValue] = useState(() => {
    const isPercent = typeof value === 'string' && value.endsWith('%');
    const numericStr = isPercent ? value.slice(0, -1) : String(value);
    const numericValue = parseInt(numericStr, 10);
    return isNaN(numericValue) ? value : 0;
  });

  useEffect(() => {
    const isPercent = typeof value === 'string' && value.endsWith('%');
    const numericStr = isPercent ? value.slice(0, -1) : String(value);
    const numericValue = parseInt(numericStr, 10);

    if (isNaN(numericValue)) {
      return undefined;
    }

    const controls = animate(0, numericValue, {
      duration: 0.8,
      ease: 'easeOut',
      onUpdate(v) {
        setDisplayValue(Math.round(v));
      },
    });

    return () => controls.stop();
  }, [value]);

  const isPercent = typeof value === 'string' && value.endsWith('%');
  return (
    <span>
      {displayValue}
      {isPercent ? '%' : ''}
    </span>
  );
}
