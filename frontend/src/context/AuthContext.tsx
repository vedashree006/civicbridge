import React, { createContext, useContext, useState, ReactNode } from 'react';
import { AuthResponse } from '../types';

interface AuthContextValue {
  user: Omit<AuthResponse, 'token'> | null;
  isAuthenticated: boolean;
  login: (auth: AuthResponse) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function loadStoredUser(): Omit<AuthResponse, 'token'> | null {
  const raw = localStorage.getItem('cb_user');
  return raw ? JSON.parse(raw) : null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Omit<AuthResponse, 'token'> | null>(loadStoredUser());

  const login = (auth: AuthResponse) => {
    localStorage.setItem('cb_token', auth.token);
    const { token: _token, ...rest } = auth;
    localStorage.setItem('cb_user', JSON.stringify(rest));
    setUser(rest);
  };

  const logout = () => {
    localStorage.removeItem('cb_token');
    localStorage.removeItem('cb_user');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
