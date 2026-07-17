# Cosmic intro animation

**Date:** 2026-07-17 · **Status:** Approved

## What & why

A premium intro on every full page load/reload: glowing dots appear across
the viewport like stars, converge inward along curved paths, connect with
animated lines, and form the existing constellation logo (`logo-mark.png`)
exactly; the formed logo does one subtle rotation-and-settle, then the
overlay fades out to the app. Elegant, modern, enterprise-grade.

## Design

- **One self-contained component** `frontend/src/components/IntroOverlay.jsx`:
  full-screen fixed canvas + rAF loop, mounted once in `App.jsx`, unmounts
  completely when finished (zero post-intro cost).
- **Sequence (~2.8s):** stars fade in scattered (~0.7s) → converge to the
  logo's node coordinates with easing (~0.9s) → edges draw between nodes
  with a soft glow (~0.7s) → crossfade to the real `logo-mark.png`, subtle
  few-degree rotation with settle, overlay fades (~0.5s). The crossfade to
  the actual PNG is what guarantees the logo forms "exactly".
- **Node coordinates** extracted once from `logo-mark.png` (offline script)
  and embedded as a normalized constant array; edges connect each node to
  its nearest neighbors to recreate the logo's web.
- **Colors from tokens:** new `--intro-*` tokens in `styles.css` (both
  themes) sampled from the logo's pink→violet→cyan gradient; the backdrop
  uses existing surface tokens. No raw hex in the component.
- **Guardrails:** click anywhere skips instantly; `prefers-reduced-motion`
  replaces the whole sequence with a 300ms logo fade; a hard ~3s cap ends
  the overlay even if frames hitch; runs only on full page load (component
  mounts once), never on SPA navigation.

## Testing & verification

Frontend-only: `npm run format && npm run build` (Prettier + ESLint), then a
live look — reload the running app, watch the sequence, click-skip works,
reduced-motion emulation shows the short fade, both themes look right.

## Out of scope

Sound, per-route replays, a bespoke SVG logo asset, configurability.
