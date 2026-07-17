import { useEffect, useRef, useState } from 'react';

/**
 * Cosmic intro: glowing stars scatter across the viewport, converge along
 * curved paths onto the logo's constellation, connect with animated lines,
 * then crossfade into the real logo-mark, which settles with a smooth 60°
 * rotation (the mark is six-fold symmetric, so it lands on its own shape).
 *
 * Runs once per full page load; click skips; prefers-reduced-motion replaces
 * the whole sequence with a short fade; a hard time cap ends it regardless.
 * The component unmounts completely afterwards — zero post-intro cost.
 */

// The constellation mirrors logo-mark.png's structure: 6 hexagon corners,
// 6 perimeter midpoints, an inner ring of 6, and the center — 19 nodes.
// (The finale crossfades to the real image, which guarantees exactness.)
function constellation() {
  const nodes = [];
  for (let i = 0; i < 6; i++) {
    const corner = (Math.PI / 3) * i - Math.PI / 2;
    const mid = corner + Math.PI / 6;
    nodes.push([Math.cos(corner), Math.sin(corner)]); // corner, r = 1
    nodes.push([Math.cos(mid) * 0.866, Math.sin(mid) * 0.866]); // perimeter midpoint
    nodes.push([Math.cos(mid) * 0.48, Math.sin(mid) * 0.48]); // inner ring
  }
  nodes.push([0, 0]); // center
  const edges = [];
  for (let a = 0; a < nodes.length; a++) {
    for (let b = a + 1; b < nodes.length; b++) {
      const dx = nodes[a][0] - nodes[b][0];
      const dy = nodes[a][1] - nodes[b][1];
      if (Math.sqrt(dx * dx + dy * dy) <= 1.05) {
        edges.push([a, b]);
      }
    }
  }
  return { nodes, edges };
}

const STAR_COUNT = 64; // 19 become nodes; the rest fade out during convergence
const T_SCATTER = 700;
const T_CONVERGE = 900;
const T_CONNECT = 700;
const T_REVEAL = 550;
const HARD_CAP_MS = 3500;

const easeInOut = (t) => (t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2);

export default function IntroOverlay({ onDone }) {
  const canvasRef = useRef(null);
  const [phase, setPhase] = useState('run'); // run -> reveal -> fade
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  useEffect(() => {
    if (reduced) {
      // accessibility path: brief logo fade, no particles, no rotation
      const t = setTimeout(onDone, 900);
      return () => clearTimeout(t);
    }
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    const w = window.innerWidth;
    const h = window.innerHeight;
    canvas.width = w * dpr;
    canvas.height = h * dpr;
    ctx.scale(dpr, dpr);

    const styles = getComputedStyle(document.documentElement);
    const pink = styles.getPropertyValue('--intro-pink').trim();
    const violet = styles.getPropertyValue('--intro-violet').trim();
    const blue = styles.getPropertyValue('--intro-blue').trim();

    const { nodes, edges } = constellation();
    const scale = Math.min(w, h) * 0.21;
    const cx = w / 2;
    const cy = h / 2;
    // left-to-right gradient across the mark, like the artwork
    const colorAt = (nx) => (nx < -0.33 ? pink : nx > 0.33 ? blue : violet);

    const stars = Array.from({ length: STAR_COUNT }, (_, i) => {
      const target = i < nodes.length ? nodes[i] : null;
      const sx = Math.random() * w;
      const sy = Math.random() * h;
      return {
        sx,
        sy,
        // curved approach: a control point offset sideways from the straight path
        bendX: (Math.random() - 0.5) * w * 0.3,
        bendY: (Math.random() - 0.5) * h * 0.3,
        tx: target ? cx + target[0] * scale : sx,
        ty: target ? cy + target[1] * scale : sy,
        color: target ? colorAt(target[0]) : violet,
        node: !!target,
        twinkle: Math.random() * Math.PI * 2,
      };
    });

    let raf;
    let finished = false;
    const start = performance.now();

    const finish = () => {
      if (finished) return;
      finished = true;
      cancelAnimationFrame(raf);
      setPhase('reveal');
    };
    const cap = setTimeout(finish, HARD_CAP_MS);
    const skip = () => finish();
    window.addEventListener('pointerdown', skip);

    const frame = (now) => {
      const t = now - start;
      ctx.clearRect(0, 0, w, h);

      const scatter = Math.min(1, t / T_SCATTER);
      const converge = Math.min(1, Math.max(0, (t - T_SCATTER) / T_CONVERGE));
      const connect = Math.min(1, Math.max(0, (t - T_SCATTER - T_CONVERGE) / T_CONNECT));
      const k = easeInOut(converge);

      // edges draw in, each slightly staggered, with a soft glow
      if (connect > 0) {
        ctx.lineWidth = 1.4;
        edges.forEach(([a, b], i) => {
          const local = Math.min(1, Math.max(0, connect * edges.length * 0.35 - i * 0.22));
          if (local <= 0) return;
          const [ax, ay] = nodes[a];
          const [bx, by] = nodes[b];
          const x1 = cx + ax * scale;
          const y1 = cy + ay * scale;
          const x2 = cx + bx * scale;
          const y2 = cy + by * scale;
          const grad = ctx.createLinearGradient(x1, y1, x2, y2);
          grad.addColorStop(0, colorAt(ax));
          grad.addColorStop(1, colorAt(bx));
          ctx.strokeStyle = grad;
          ctx.globalAlpha = 0.55 * local;
          ctx.shadowBlur = 6;
          ctx.shadowColor = colorAt((ax + bx) / 2);
          ctx.beginPath();
          ctx.moveTo(x1, y1);
          ctx.lineTo(x1 + (x2 - x1) * local, y1 + (y2 - y1) * local);
          ctx.stroke();
        });
        ctx.globalAlpha = 1;
        ctx.shadowBlur = 0;
      }

      for (const s of stars) {
        let x = s.sx;
        let y = s.sy;
        if (converge > 0 && s.node) {
          // quadratic bezier from scatter position to constellation seat
          const mx = (s.sx + s.tx) / 2 + s.bendX;
          const my = (s.sy + s.ty) / 2 + s.bendY;
          const u = 1 - k;
          x = u * u * s.sx + 2 * u * k * mx + k * k * s.tx;
          y = u * u * s.sy + 2 * u * k * my + k * k * s.ty;
        }
        const ambientFade = s.node ? 1 : Math.max(0, 1 - converge * 1.6);
        if (ambientFade <= 0) continue;
        const tw = 0.65 + 0.35 * Math.sin(s.twinkle + t / 260);
        const r = s.node ? 2.2 + 1.2 * k : 1.4;
        ctx.globalAlpha = scatter * tw * ambientFade;
        ctx.shadowBlur = 10;
        ctx.shadowColor = s.color;
        ctx.fillStyle = s.color;
        ctx.beginPath();
        ctx.arc(x, y, r, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
      ctx.shadowBlur = 0;

      if (t >= T_SCATTER + T_CONVERGE + T_CONNECT) {
        finish();
        return;
      }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);

    return () => {
      cancelAnimationFrame(raf);
      clearTimeout(cap);
      window.removeEventListener('pointerdown', skip);
    };
  }, [onDone, reduced]);

  // reveal phase: crossfade to the real mark + 60° settle, then fade the overlay
  useEffect(() => {
    if (phase !== 'reveal') return undefined;
    const toFade = setTimeout(() => setPhase('fade'), T_REVEAL + 450);
    return () => clearTimeout(toFade);
  }, [phase]);

  useEffect(() => {
    if (phase !== 'fade') return undefined;
    const done = setTimeout(onDone, 420);
    return () => clearTimeout(done);
  }, [phase, onDone]);

  return (
    <div
      className={`intro-overlay ${phase === 'fade' ? 'intro-overlay-out' : ''}`}
      role="presentation"
      aria-hidden="true"
    >
      {!reduced && <canvas ref={canvasRef} className="intro-canvas" />}
      <img
        src="/logo-mark.png"
        alt=""
        className={`intro-logo ${reduced ? 'intro-logo-reduced' : phase !== 'run' ? 'intro-logo-reveal' : ''}`}
      />
    </div>
  );
}
