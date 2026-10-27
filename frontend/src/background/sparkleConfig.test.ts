import { describe, it, expect } from 'vitest';
import {
  FLOATS_PER_PARTICLE, generateParticles, getQualityProfile, intensityForPath, mulberry32,
} from './sparkleConfig';

describe('getQualityProfile', () => {
  it('uses the full particle budget on a capable desktop', () => {
    const p = getQualityProfile({ width: 1440, hardwareConcurrency: 12, deviceMemory: 16 });
    expect(p.count).toBe(900);
    expect(p.animate).toBe(true);
    expect(p.parallax).toBe(true);
    expect(p.fpsCap).toBe(0);
    expect(p.dprCap).toBe(1.5);
  });

  it('reduces particles, pixel ratio and frame rate on mobile / coarse pointers', () => {
    const p = getQualityProfile({ width: 390, coarsePointer: true, hardwareConcurrency: 8 });
    expect(p.count).toBeLessThan(500);
    expect(p.dprCap).toBe(1);
    expect(p.fpsCap).toBe(30);
    expect(p.parallax).toBe(false);
  });

  it('reduces further on low-end or data-saver devices', () => {
    const normal = getQualityProfile({ width: 1440, hardwareConcurrency: 8 });
    const low = getQualityProfile({ width: 1440, hardwareConcurrency: 2 });
    const saver = getQualityProfile({ width: 1440, saveData: true });
    expect(low.count).toBeLessThan(normal.count);
    expect(saver.count).toBeLessThan(normal.count);
  });

  it('disables animation and parallax for prefers-reduced-motion', () => {
    const p = getQualityProfile({ width: 1440, reducedMotion: true });
    expect(p.animate).toBe(false);
    expect(p.parallax).toBe(false);
    expect(p.count).toBeLessThanOrEqual(420);
  });
});

describe('generateParticles', () => {
  it('is deterministic and correctly sized', () => {
    const a = generateParticles(200, 7);
    const b = generateParticles(200, 7);
    expect(a.length).toBe(200 * FLOATS_PER_PARTICLE);
    expect(Array.from(a)).toEqual(Array.from(b));
    expect(Array.from(generateParticles(200, 8))).not.toEqual(Array.from(a));
  });

  it('keeps positions inside the volume and uses valid kinds', () => {
    const d = generateParticles(500);
    for (let i = 0; i < 500; i++) {
      const o = i * FLOATS_PER_PARTICLE;
      for (let k = 0; k < 3; k++) {
        expect(d[o + k]).toBeGreaterThanOrEqual(-1);
        expect(d[o + k]).toBeLessThanOrEqual(1);
      }
      expect([0, 1, 2]).toContain(d[o + 7]);
    }
  });

  it('is mostly fine dust with a minority of stars and bokeh', () => {
    const d = generateParticles(1000);
    const kinds = [0, 0, 0];
    for (let i = 0; i < 1000; i++) kinds[d[i * FLOATS_PER_PARTICLE + 7]]++;
    expect(kinds[0]).toBeGreaterThan(kinds[1]);
    expect(kinds[1]).toBeGreaterThan(kinds[2]);
  });
});

describe('mulberry32', () => {
  it('returns values in [0, 1)', () => {
    const r = mulberry32(1);
    for (let i = 0; i < 100; i++) {
      const v = r();
      expect(v).toBeGreaterThanOrEqual(0);
      expect(v).toBeLessThan(1);
    }
  });
});

describe('intensityForPath', () => {
  it('is strongest on auth screens and calmer on data-dense screens', () => {
    expect(intensityForPath('/login')).toBe(1);
    expect(intensityForPath('/register')).toBe(1);
    expect(intensityForPath('/cases/12')).toBeLessThan(intensityForPath('/dashboard'));
    expect(intensityForPath('/admin/analytics')).toBeLessThanOrEqual(intensityForPath('/cases'));
    for (const p of ['/', '/dashboard', '/privacy', '/nope']) {
      expect(intensityForPath(p)).toBeGreaterThan(0);
      expect(intensityForPath(p)).toBeLessThanOrEqual(1);
    }
  });
});
