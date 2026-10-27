import { createContext, useCallback, useContext, useEffect, useMemo, useState, ReactNode } from 'react';
import { ConsentChoices, StoredConsent, loadConsent, saveConsent, withdrawConsent, ACCEPT_ALL, REJECT_ALL, CONSENT_VERSION } from './consentStore';
import { apiClient } from '../api/client';

interface ConsentContextValue {
  consent: StoredConsent | null;      // null = no valid decision yet (banner shown)
  settingsOpen: boolean;
  openSettings: () => void;
  closeSettings: () => void;
  acceptAll: () => void;
  rejectNonEssential: () => void;
  save: (choices: ConsentChoices) => void;
  withdraw: () => void;
}

const Ctx = createContext<ConsentContextValue | undefined>(undefined);

export function ConsentProvider({ children }: { children: ReactNode }) {
  const [consent, setConsent] = useState<StoredConsent | null>(() => loadConsent());
  const [settingsOpen, setSettingsOpen] = useState(false);

  // Re-check expiry when the tab becomes visible again (long-lived tabs).
  useEffect(() => {
    const onVis = () => {
      if (document.visibilityState === 'visible') setConsent(loadConsent());
    };
    document.addEventListener('visibilitychange', onVis);
    return () => document.removeEventListener('visibilitychange', onVis);
  }, []);

  const persist = useCallback((choices: ConsentChoices, withdrawing = false) => {
    const stored = withdrawing ? withdrawConsent() : saveConsent(choices);
    setConsent(stored);
    setSettingsOpen(false);
    // Best-effort accountability record (no IP / identifiers). Failure never blocks the user's choice.
    apiClient
      .post('/consent', {
        policyVersion: CONSENT_VERSION,
        preferences: stored.preferences,
        analytics: stored.analytics,
        marketing: false,
      })
      .catch(() => undefined);
  }, []);

  const value = useMemo<ConsentContextValue>(
    () => ({
      consent,
      settingsOpen,
      openSettings: () => setSettingsOpen(true),
      closeSettings: () => setSettingsOpen(false),
      acceptAll: () => persist(ACCEPT_ALL),
      rejectNonEssential: () => persist(REJECT_ALL),
      save: (c) => persist(c),
      withdraw: () => persist(REJECT_ALL, true),
    }),
    [consent, settingsOpen, persist]
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useConsent(): ConsentContextValue {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useConsent must be used within ConsentProvider');
  return ctx;
}
