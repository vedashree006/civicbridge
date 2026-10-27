/**
 * Pure (DOM-free) helpers for the sparkle background: device profiling, deterministic particle
 * generation and per-route intensity. Kept separate from the WebGL renderer so it is unit-testable.
 */

export interface DeviceInfo {
  width: number;
  hardwareConcurrency?: number;
  deviceMemory?: number;
  coarsePointer?: boolean;
  saveData?: boolean;
  reducedMotion?: boolean;
}

export interface QualityProfile {
  /** Number of particles uploaded to the GPU. */
  count: number;
  /** Upper bound for devicePixelRatio used to size the canvas. */
  dprCap: number;
  /** Maximum frames per second (0 = uncapped, i.e. follow display refresh). */
  fpsCap: number;
  /** Whether pointer parallax is enabled. */
  parallax: boolean;
  /** false => render one static frame only (reduced motion). */
  animate: boolean;
}

export function getQualityProfile(d: DeviceInfo): QualityProfile {
  const mobile = d.width < 768 || !!d.coarsePointer;
  const lowEnd = (d.hardwareConcurrency ?? 8) <= 4 || (d.deviceMemory ?? 8) <= 4 || !!d.saveData;

  let count = 900;
  if (d.width >= 1600) count = 1100;
  if (mobile) count = 380;
  if (lowEnd) count = Math.round(count * 0.55);
  if (d.reducedMotion) count = Math.min(count, 420);

  return {
    count,
    dprCap: mobile || lowEnd ? 1 : 1.5,
    fpsCap: mobile || lowEnd ? 30 : 0,
    parallax: !mobile && !d.reducedMotion,
    animate: !d.reducedMotion,
  };
}

/** Small, fast, seedable PRNG so the starfield is identical across renders/tests. */
export function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Floats per particle in the interleaved vertex buffer. */
export const FLOATS_PER_PARTICLE = 8;

/**
 * Interleaved layout (8 floats): x, y, z (−1..1 volume), size (0..1), phase (0..2π),
 * twinkle rate, colour mix (0..1), kind (0 = dust, 1 = star with diffraction cross, 2 = bokeh).
 */
export function generateParticles(count: number, seed = 20260101): Float32Array {
  const rnd = mulberry32(seed);
  const out = new Float32Array(count * FLOATS_PER_PARTICLE);
  for (let i = 0; i < count; i++) {
    const o = i * FLOATS_PER_PARTICLE;
    const roll = rnd();
    const kind = roll < 0.07 ? 2 : roll < 0.2 ? 1 : 0; // few bokeh, some stars, mostly dust
    out[o] = rnd() * 2 - 1;
    out[o + 1] = rnd() * 2 - 1;
    out[o + 2] = rnd() * 2 - 1;
    out[o + 3] = kind === 2 ? 0.75 + rnd() * 0.25 : kind === 1 ? 0.35 + rnd() * 0.45 : rnd() * 0.3;
    out[o + 4] = rnd() * Math.PI * 2;
    out[o + 5] = 0.25 + rnd() * 1.1;
    out[o + 6] = rnd();
    out[o + 7] = kind;
  }
  return out;
}

/**
 * Sparkle strength per route (0..1). Auth screens get the fullest effect; data-dense screens are
 * calmer so tables and forms stay easy to read.
 */
export function intensityForPath(pathname: string): number {
  if (pathname === '/login' || pathname === '/register') return 1;
  if (pathname.startsWith('/admin')) return 0.5;
  if (pathname.startsWith('/cases')) return 0.6;
  if (pathname === '/privacy') return 0.55;
  return 0.75; // dashboard and everything else
}
