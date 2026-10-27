import { apiClient } from './client';
import { AuthResponse } from '../types';

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/auth/login', { email, password });
  return data;
}

export async function register(email: string, password: string, fullName: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/auth/register', { email, password, fullName });
  return data;
}

/** Ends the server-side session so the JWT stops working immediately. Safe to call without a valid token. */
export async function logoutRequest(): Promise<void> {
  await apiClient.post('/auth/logout');
}
