import { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';

/** Soft fade/rise between routes. Keyed by pathname; CSS disables it under prefers-reduced-motion. */
export default function PageTransition({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  return (
    <div key={pathname} className="page-transition">
      {children}
    </div>
  );
}
