import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { getQualityProfile, intensityForPath, QualityProfile } from '../background/sparkleConfig';
import '../background/sparkle.css';

type Status = 'loading' | 'webgl' | 'fallback';
type Renderer = { setIntensity(v: number): void; setProfile(p: QualityProfile): void; start(): void; dispose(): void };

function readProfile(): QualityProfile {
  const nav = navigator as Navigator & { deviceMemory?: number; connection?: { saveData?: boolean } };
  return getQualityProfile({
    width: window.innerWidth,
    hardwareConcurrency: nav.hardwareConcurrency,
    deviceMemory: nav.deviceMemory,
    coarsePointer: window.matchMedia('(pointer: coarse)').matches,
    saveData: nav.connection?.saveData,
    reducedMotion: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  });
}

/**
 * One app-wide, decorative 3D sparkle background (mounted once in App, behind all routes).
 *
 * - Layer 1: CSS gradient + glows (always present; it IS the fallback when WebGL is unavailable).
 * - Layer 2: a WebGL canvas with the particle field (loaded lazily so first paint is not delayed).
 * - aria-hidden, pointer-events: none, and z-index 0 so it never captures clicks, focus or text.
 */
export default function SparkleBackground() {
  const hostRef = useRef<HTMLDivElement>(null);
  const rendererRef = useRef<Renderer | null>(null);
  const intensityRef = useRef(1);
  const [status, setStatus] = useState<Status>('loading');
  const { pathname } = useLocation();

  // Route-based intensity; applied imperatively (no re-render of the scene).
  useEffect(() => {
    intensityRef.current = intensityForPath(pathname);
    rendererRef.current?.setIntensity(intensityRef.current);
  }, [pathname]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    let cancelled = false;
    let renderer: Renderer | null = null;
    // A fresh canvas per mount: a canvas whose WebGL context was released cannot be reused (React StrictMode).
    const canvas = document.createElement('canvas');
    canvas.className = 'sparkle-bg__canvas';
    canvas.setAttribute('aria-hidden', 'true');
    host.appendChild(canvas);

    const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onMotionChange = () => renderer?.setProfile(readProfile());

    import('../background/sparkleRenderer')
      .then(({ SparkleRenderer }) => {
        if (cancelled) return;
        renderer = SparkleRenderer.create(canvas, readProfile());
        if (!renderer) {
          setStatus('fallback');
          return;
        }
        rendererRef.current = renderer;
        renderer.setIntensity(intensityRef.current);
        renderer.start();
        motionQuery.addEventListener('change', onMotionChange);
        setStatus('webgl');
      })
      .catch(() => {
        if (!cancelled) setStatus('fallback'); // chunk failed to load: keep the CSS background
      });

    return () => {
      cancelled = true;
      motionQuery.removeEventListener('change', onMotionChange);
      renderer?.dispose();
      rendererRef.current = null;
      canvas.remove();
    };
  }, []);

  return (
    <div className={`sparkle-bg sparkle-bg--${status}`} aria-hidden="true" data-testid="sparkle-background">
      <div className="sparkle-bg__glow sparkle-bg__glow--a" />
      <div className="sparkle-bg__glow sparkle-bg__glow--b" />
      <div className="sparkle-bg__stars" />
      <div className="sparkle-bg__vignette" />
      <div className="sparkle-bg__host" ref={hostRef} />
    </div>
  );
}
