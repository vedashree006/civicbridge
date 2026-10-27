import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useConsent } from '../consent/ConsentContext';

/** First-visit banner. "Reject" is as prominent as "Accept all". Nothing optional runs until a choice is made. */
export function CookieBanner() {
  const { consent, settingsOpen, acceptAll, rejectNonEssential, openSettings } = useConsent();
  if (consent || settingsOpen) return null;
  return (
    <div className="cookie-banner" role="region" aria-label="Cookie consent">
      <div className="cookie-banner__text">
        <strong>Your privacy choices</strong>
        <p>
          CivicBridge uses strictly necessary storage to keep you signed in and remember this choice. With your
          permission we also use an analytics cookie to count page views. No marketing cookies or third-party trackers
          are used. See our <Link to="/privacy">privacy &amp; cookie information</Link>.
        </p>
      </div>
      <div className="cookie-banner__actions">
        <button className="btn-secondary" onClick={rejectNonEssential}>Reject non-essential</button>
        <button className="btn-secondary" onClick={openSettings}>Customize</button>
        <button onClick={acceptAll}>Accept all</button>
      </div>
    </div>
  );
}

/** Modal to review, change or withdraw consent at any time. Focus is trapped; Escape closes. */
export function CookieSettings() {
  const { consent, settingsOpen, closeSettings, save, acceptAll, rejectNonEssential, withdraw } = useConsent();
  const [preferences, setPreferences] = useState(false);
  const [analytics, setAnalytics] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (settingsOpen) {
      setPreferences(consent?.preferences ?? false);
      setAnalytics(consent?.analytics ?? false);
      dialogRef.current?.focus();
    }
  }, [settingsOpen, consent]);

  useEffect(() => {
    if (!settingsOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeSettings();
      if (e.key === 'Tab' && dialogRef.current) {
        const f = dialogRef.current.querySelectorAll<HTMLElement>('button, input, a[href]');
        if (!f.length) return;
        const first = f[0], last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [settingsOpen, closeSettings]);

  if (!settingsOpen) return null;

  return (
    <div className="modal-backdrop" onClick={closeSettings}>
      <div
        className="modal card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cookie-settings-title"
        tabIndex={-1}
        ref={dialogRef}
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="cookie-settings-title">Cookie settings</h2>
        <p>Choose which optional cookies CivicBridge may use. You can change or withdraw your choice at any time.</p>

        <fieldset className="consent-group">
          <legend>Strictly necessary</legend>
          <label className="consent-row">
            <input type="checkbox" checked disabled aria-describedby="cc-necessary" />
            <span>Always active</span>
          </label>
          <p id="cc-necessary">
            Keeps you signed in (<code>cb_token</code>, <code>cb_user</code> in local storage) and remembers your cookie
            choice (<code>cb_consent</code>, up to 180 days). The app cannot work without them.
          </p>
        </fieldset>

        <fieldset className="consent-group">
          <legend>Preferences</legend>
          <label className="consent-row">
            <input type="checkbox" checked={preferences} onChange={(e) => setPreferences(e.target.checked)} aria-describedby="cc-pref" />
            <span>Allow preference cookies</span>
          </label>
          <p id="cc-pref">Currently CivicBridge sets no preference cookies. This choice is recorded and will apply if such features are added.</p>
        </fieldset>

        <fieldset className="consent-group">
          <legend>Analytics</legend>
          <label className="consent-row">
            <input type="checkbox" checked={analytics} onChange={(e) => setAnalytics(e.target.checked)} aria-describedby="cc-analytics" />
            <span>Allow anonymous usage analytics</span>
          </label>
          <p id="cc-analytics">
            Sets <code>cb_vid</code>, a random identifier valid for 24 hours, used to count page views and unique
            visitors per day. It contains no personal data and is not shared with third parties.
          </p>
        </fieldset>

        <p className="muted">Marketing cookies are not used by CivicBridge.</p>

        <div className="cookie-banner__actions">
          <button className="btn-secondary" onClick={rejectNonEssential}>Reject non-essential</button>
          <button className="btn-secondary" onClick={acceptAll}>Accept all</button>
          <button onClick={() => save({ preferences, analytics })}>Save preferences</button>
        </div>
        {consent && (preferences || analytics || consent.analytics || consent.preferences) && (
          <p>
            <button className="link-button" onClick={withdraw}>Withdraw my consent</button>
          </p>
        )}
        <p className="muted">
          {consent ? `Last saved ${new Date(consent.timestamp).toLocaleString()} (policy ${consent.version}).` : 'No choice saved yet.'}
        </p>
        <button className="btn-secondary" onClick={closeSettings}>Close</button>
      </div>
    </div>
  );
}

export function Footer() {
  const { openSettings } = useConsent();
  return (
    <footer className="site-footer">
      <span>© CivicBridge</span>
      <Link to="/privacy">Privacy &amp; cookies</Link>
      <button className="link-button" onClick={openSettings}>Cookie settings</button>
    </footer>
  );
}
