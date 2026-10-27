// Pure consent logic (no React) so it can be unit-tested and used by non-React code.

export const CONSENT_COOKIE = 'cb_consent';
export const VISITOR_COOKIE = 'cb_vid';
/** Bump when cookie purposes/categories change; every visitor is asked again. Keep in sync with backend CONSENT_POLICY_VERSION. */
export const CONSENT_VERSION = '2026-10-1';
export const CONSENT_MAX_AGE_DAYS = 180;
const DAY_MS = 86_400_000;

export interface ConsentChoices {
  /** Strictly necessary is always on and not stored as a choice. */
  preferences: boolean;
  analytics: boolean;
}

export interface StoredConsent extends ConsentChoices {
  version: string;
  timestamp: number; // epoch ms
}

export const REJECT_ALL: ConsentChoices = { preferences: false, analytics: false };
export const ACCEPT_ALL: ConsentChoices = { preferences: true, analytics: true };

/** Minimal cookie jar interface so tests can inject a fake. */
export interface CookieJar {
  get(): string;
  set(cookie: string): void;
}

export const browserJar: CookieJar = {
  get: () => (typeof document === 'undefined' ? '' : document.cookie),
  set: (c) => {
    if (typeof document !== 'undefined') document.cookie = c;
  },
};

function secureFlag(): string {
  return typeof location !== 'undefined' && location.protocol === 'https:' ? '; Secure' : '';
}

export function readCookie(name: string, jar: CookieJar = browserJar): string | null {
  for (const part of jar.get().split('; ')) {
    const idx = part.indexOf('=');
    if (idx > 0 && part.slice(0, idx) === name) return decodeURIComponent(part.slice(idx + 1));
  }
  return null;
}

export function writeCookie(name: string, value: string, maxAgeSeconds: number, jar: CookieJar = browserJar): void {
  jar.set(`${name}=${encodeURIComponent(value)}; Max-Age=${maxAgeSeconds}; Path=/; SameSite=Lax${secureFlag()}`);
}

export function deleteCookie(name: string, jar: CookieJar = browserJar): void {
  jar.set(`${name}=; Max-Age=0; Path=/; SameSite=Lax${secureFlag()}`);
}

/** Returns the stored decision, or null if none, malformed, expired or from an older policy version. */
export function loadConsent(now: number = Date.now(), jar: CookieJar = browserJar): StoredConsent | null {
  const raw = readCookie(CONSENT_COOKIE, jar);
  if (!raw) return null;
  try {
    const c = JSON.parse(raw);
    if (
      typeof c !== 'object' || c === null ||
      c.version !== CONSENT_VERSION ||
      typeof c.timestamp !== 'number' ||
      typeof c.preferences !== 'boolean' ||
      typeof c.analytics !== 'boolean'
    ) return null;
    if (now - c.timestamp > CONSENT_MAX_AGE_DAYS * DAY_MS) return null;
    return c as StoredConsent;
  } catch {
    return null;
  }
}

/** Persists the decision and immediately enforces it (removes the analytics cookie if not allowed). */
export function saveConsent(choices: ConsentChoices, now: number = Date.now(), jar: CookieJar = browserJar): StoredConsent {
  const stored: StoredConsent = {
    version: CONSENT_VERSION,
    timestamp: now,
    preferences: !!choices.preferences,
    analytics: !!choices.analytics,
  };
  writeCookie(CONSENT_COOKIE, JSON.stringify(stored), CONSENT_MAX_AGE_DAYS * 86_400, jar);
  if (!stored.analytics) deleteCookie(VISITOR_COOKIE, jar);
  return stored;
}

/** Withdraw = reject everything optional and remove optional cookies. */
export function withdrawConsent(now: number = Date.now(), jar: CookieJar = browserJar): StoredConsent {
  return saveConsent(REJECT_ALL, now, jar);
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/**
 * Returns the random analytics visitor id, creating it only if analytics consent is currently granted.
 * Returns null otherwise - nothing is ever written without consent.
 */
export function getVisitorId(consent: StoredConsent | null, jar: CookieJar = browserJar): string | null {
  if (!consent?.analytics) return null;
  let id = readCookie(VISITOR_COOKIE, jar);
  if (!id) {
    id = uuid();
    writeCookie(VISITOR_COOKIE, id, 86_400, jar); // 24 h
  }
  return id;
}
