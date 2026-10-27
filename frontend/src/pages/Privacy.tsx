import { useConsent } from '../consent/ConsentContext';
import { CONSENT_VERSION, CONSENT_MAX_AGE_DAYS } from '../consent/consentStore';

/** Plain-language data/cookie inventory. Must be reviewed by legal counsel before production use. */
export default function Privacy() {
  const { openSettings } = useConsent();
  return (
    <div className="container">
      <h2>Privacy &amp; cookie information</h2>
      <p className="muted">Policy version {CONSENT_VERSION}. Draft for legal review - not yet legal advice.</p>

      <div className="card">
        <h3>Cookies and similar storage</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Name</th><th>Category</th><th>Purpose</th><th>Duration</th><th>Provider</th></tr>
            </thead>
            <tbody>
              <tr><td><code>cb_token</code> (local storage)</td><td>Strictly necessary</td><td>Holds your sign-in token so you stay logged in.</td><td>Until sign-out or token expiry (1 h default)</td><td>First party</td></tr>
              <tr><td><code>cb_user</code> (local storage)</td><td>Strictly necessary</td><td>Your name and role for display after sign-in.</td><td>Until sign-out</td><td>First party</td></tr>
              <tr><td><code>cb_consent</code> (cookie)</td><td>Strictly necessary</td><td>Remembers your cookie choice, its date and policy version.</td><td>{CONSENT_MAX_AGE_DAYS} days</td><td>First party</td></tr>
              <tr><td><code>cb_vid</code> (cookie)</td><td>Analytics (opt-in)</td><td>Random ID to count page views and daily unique visitors. Set only after you allow analytics; deleted when you withdraw.</td><td>24 hours</td><td>First party</td></tr>
            </tbody>
          </table>
        </div>
        <p>Preference and marketing cookies are not used. No third-party analytics, advertising or tracking services are loaded.</p>
        <button onClick={openSettings}>Change cookie settings</button>
      </div>

      <div className="card">
        <h3>Data processed for service operation</h3>
        <ul>
          <li><strong>Sign-in sessions:</strong> a session record (user id, start, last activity, end) is kept to keep your account secure and to support logout. Deleted 30 days after the session ends.</li>
          <li><strong>Usage events:</strong> counts of logins, logouts, case submissions, document uploads and AI guidance use, linked to your account for administrators' aggregate statistics. Deleted after 90 days.</li>
          <li><strong>Analytics page views:</strong> only with your consent. Stored with a salted hash of the random visitor ID that changes daily; IP addresses and browser fingerprints are not collected.</li>
          <li><strong>Consent records:</strong> your choice, policy version and time. No IP address. Kept for accountability.</li>
        </ul>
        <p className="muted">Retention periods are configurable by the operator and must be confirmed in the final privacy notice.</p>
      </div>
    </div>
  );
}
