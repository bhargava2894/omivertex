import { useEffect, useRef, useState } from 'react';

/**
 * Cosmic intro: glowing stars scatter across the viewport, converge along
 * curved paths onto the logo's constellation, connect with animated lines,
 * crossfade into the real logo-mark with a smooth 60° settle (the three
 * orbits sit 60° apart, so the turn reads as orbital motion), then FLY to
 * the page's real logo placeholder — the
 * sidebar brand or the login card's logo — un-twisting as it shrinks, while
 * the page rises in underneath (the parent is told via onLanding).
 *
 * Runs once per full page load; click skips; prefers-reduced-motion replaces
 * the whole sequence with a short fade; a hard time cap ends it regardless.
 * The component unmounts completely afterwards — zero post-intro cost.
 */

// The constellation mirrors the logo-mark's structure: three elliptical orbits
// around a central figure, with bead nodes along the arcs. (The finale
// crossfades to the real image, which guarantees exactness.)
function constellation() {
  const nodes = []; // [x, y, weight] — beads are heavier than arc points
  const edges = [];
  const ORBITS = [-Math.PI / 6, Math.PI / 6, Math.PI / 2]; // -30°, 30°, 90°
  const PTS = 12;
  ORBITS.forEach((rot) => {
    const first = nodes.length;
    for (let i = 0; i < PTS; i++) {
      const a = (Math.PI * 2 * i) / PTS;
      const ex = Math.cos(a);
      const ey = Math.sin(a) * 0.45;
      const x = ex * Math.cos(rot) - ey * Math.sin(rot);
      const y = ex * Math.sin(rot) + ey * Math.cos(rot);
      nodes.push([x, y, i % 4 === 0 ? 3.1 : 1.7]); // every 4th point is a bead
      edges.push([first + i, first + ((i + 1) % PTS)]);
    }
  });
  nodes.push([0, -0.13, 4.2]); // the figure's head
  nodes.push([0, 0.14, 5.2]); // the figure's shoulders
  return { nodes, edges };
}

/** The page's real logo slot the intro logo lands on. */
const LANDING_TARGETS = '.brand-logo, .login-logo';

const STAR_COUNT = 64; // 38 become constellation nodes; the rest fade out during convergence
const T_SCATTER = 700;
const T_CONVERGE = 900;
const T_CONNECT = 700;
const T_REVEAL = 1000; // crossfade + 60° settle
const T_FLY = 650;
const HARD_CAP_MS = 4200;

const easeInOut = (t) => (t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2);

export default function IntroOverlay({ onLanding, onDone }) {
  const canvasRef = useRef(null);
  const logoRef = useRef(null);
  const landedRef = useRef(false);
  const [phase, setPhase] = useState('run'); // run -> reveal -> fly | fade
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const land = () => {
    if (!landedRef.current) {
      landedRef.current = true;
      onLanding?.();
    }
  };

  useEffect(() => {
    if (reduced) {
      // accessibility path: brief logo fade, no particles, no flight
      land();
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
        targetR: target ? target[2] : 0,
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
        const r = s.node ? Math.max(1.4, s.targetR * (0.55 + 0.45 * k)) : 1.4;
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [onDone, reduced]);

  // reveal -> fly: after the settle, launch toward the page's logo placeholder
  useEffect(() => {
    if (phase !== 'reveal') return undefined;
    const toFly = setTimeout(() => setPhase('fly'), T_REVEAL + 150);
    return () => clearTimeout(toFly);
  }, [phase]);

  // fly: FLIP the centered logo onto the real placeholder, un-twisting the 60°;
  // the page rises in underneath (onLanding). No placeholder -> plain fade.
  useEffect(() => {
    if (phase !== 'fly') return undefined;
    const img = logoRef.current;
    const target = document.querySelector(LANDING_TARGETS);
    land(); // page starts rising either way
    if (!img || !target) {
      setPhase('fade');
      return undefined;
    }
    const from = img.getBoundingClientRect();
    const to = target.getBoundingClientRect();
    target.style.visibility = 'hidden'; // no double logo while in flight
    const dx = to.left + to.width / 2 - (from.left + from.width / 2);
    const dy = to.top + to.height / 2 - (from.top + from.height / 2);
    const s = to.width / from.width;
    // starting point = where the settle animation left it (rotated 60°)
    img.classList.remove('intro-logo-reveal');
    img.classList.add('intro-logo-flying');
    img.style.transform = 'rotate(60deg) scale(1)';
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        img.style.transform = `translate(${dx}px, ${dy}px) rotate(0deg) scale(${s})`;
      });
    });
    const done = setTimeout(() => {
      target.style.visibility = '';
      onDone();
    }, T_FLY + 80);
    return () => {
      clearTimeout(done);
      target.style.visibility = '';
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase, onDone]);

  // fade fallback (no landing target found)
  useEffect(() => {
    if (phase !== 'fade') return undefined;
    const done = setTimeout(onDone, 420);
    return () => clearTimeout(done);
  }, [phase, onDone]);

  const outClass =
    phase === 'fly'
      ? 'intro-overlay-out'
      : phase === 'fade'
        ? 'intro-overlay-out intro-overlay-gone'
        : '';
  return (
    <div className={`intro-overlay ${outClass}`} role="presentation" aria-hidden="true">
      {!reduced && <canvas ref={canvasRef} className="intro-canvas" />}
      <img
        ref={logoRef}
        src="/logo-mark.png"
        alt=""
        className={`intro-logo ${reduced ? 'intro-logo-reduced' : phase !== 'run' ? 'intro-logo-reveal' : ''}`}
      />
    </div>
  );
}
