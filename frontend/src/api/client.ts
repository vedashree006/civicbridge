import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export const apiClient = axios.create({
  baseURL: API_URL,
});

// Attach the JWT (if present) to every outgoing request.
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('cb_token');
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401, clear the stale session so the app falls back to the login screen.
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Do not force a redirect for credential checks or best-effort telemetry endpoints.
    const url: string = error.config?.url ?? '';
    const passive = url.startsWith('/auth/') || url.startsWith('/analytics/') || url.startsWith('/consent');
    if (error.response?.status === 401 && !passive) {
      localStorage.removeItem('cb_token');
      localStorage.removeItem('cb_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
