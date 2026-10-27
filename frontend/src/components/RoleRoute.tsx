import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { UserRole } from '../types';

/** UI convenience only - the backend enforces the real authorization on every /api/admin/** call. */
export default function RoleRoute({ role }: { role: UserRole }) {
  const { user } = useAuth();
  return user?.role === role ? <Outlet /> : <Navigate to="/dashboard" replace />;
}
