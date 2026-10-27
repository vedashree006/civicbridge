import { describe, it, expect } from 'vitest';
import {
  CookieJar, CONSENT_COOKIE, VISITOR_COOKIE, CONSENT_VERSION, CONSENT_MAX_AGE_DAYS,
  loadConsent, saveConsent, withdrawConsent, getVisitorId, readCookie, ACCEPT_ALL, REJECT_ALL,
} from './consentStore';

/** In-memory cookie jar that honours Max-Age=0 deletions. */
function fakeJar(): CookieJar & { names(): string[] } {
  const store = new Map<string, string>();
  return {
    get: () => Array.from(store, ([k, v]) => `${k}=${v}`).join('; '),
    set: (c) => {
      const [pair, ...attrs] = c.split('; ');
      const idx = pair.indexOf('=');
      const name = pair.slice(0, idx), value = pair.slice(idx + 1);
      if (attrs.includes('Max-Age=0')) store.delete(name); else store.set(name, value);
    },
    names: () => Array.from(store.keys()),
  };
}

describe('consent store', () => {
  it('has no consent by default and writes nothing', () => {
    const jar = fakeJar();
    expect(loadConsent(Date.now(), jar)).toBeNull();
    expect(jar.names()).toEqual([]);
  });

  it('never creates the analytics visitor cookie without consent', () => {
    const jar = fakeJar();
    expect(getVisitorId(null, jar)).toBeNull();
    const rejected = saveConsent(REJECT_ALL, Date.now(), jar);
    expect(getVisitorId(rejected, jar)).toBeNull();
    expect(jar.names()).toEqual([CONSENT_COOKIE]);
  });

  it('creates a stable visitor id after analytics consent', () => {
    const jar = fakeJar();
    const c = saveConsent(ACCEPT_ALL, Date.now(), jar);
    const id = getVisitorId(c, jar);
    expect(id).toMatch(/^[0-9a-f-]{36}$/);
    expect(getVisitorId(c, jar)).toBe(id);
  });

  it('withdrawal deletes the analytics cookie and disables analytics', () => {
    const jar = fakeJar();
    const c = saveConsent(ACCEPT_ALL, Date.now(), jar);
    getVisitorId(c, jar);
    expect(readCookie(VISITOR_COOKIE, jar)).not.toBeNull();
    const w = withdrawConsent(Date.now(), jar);
    expect(w.analytics).toBe(false);
    expect(readCookie(VISITOR_COOKIE, jar)).toBeNull();
    expect(loadConsent(Date.now(), jar)?.analytics).toBe(false);
  });

  it('expires after the maximum age', () => {
    const jar = fakeJar();
    const t0 = 1_700_000_000_000;
    saveConsent(ACCEPT_ALL, t0, jar);
    expect(loadConsent(t0 + 1000, jar)).not.toBeNull();
    expect(loadConsent(t0 + (CONSENT_MAX_AGE_DAYS + 1) * 86_400_000, jar)).toBeNull();
  });

  it('re-prompts when the policy version changes or the cookie is malformed', () => {
    const jar = fakeJar();
    jar.set(`${CONSENT_COOKIE}=${encodeURIComponent(JSON.stringify({ version: 'old', timestamp: Date.now(), preferences: true, analytics: true }))}; Max-Age=100`);
    expect(loadConsent(Date.now(), jar)).toBeNull();
    jar.set(`${CONSENT_COOKIE}=not-json; Max-Age=100`);
    expect(loadConsent(Date.now(), jar)).toBeNull();
  });

  it('records the current policy version and timestamp', () => {
    const jar = fakeJar();
    const c = saveConsent({ preferences: false, analytics: true }, 123456, jar);
    expect(c.version).toBe(CONSENT_VERSION);
    expect(c.timestamp).toBe(123456);
  });
});
