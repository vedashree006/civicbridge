import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  if (!isAuthenticated) return null;

  return (
    <nav>
      <strong style={{ color: 'white' }}>CivicBridge</strong>
      <Link to="/dashboard">Dashboard</Link>
      <Link to="/cases">Cases</Link>
      {user?.role === 'ADMIN' && <Link to="/admin/analytics">Analytics</Link>}
      <span style={{ marginLeft: 'auto', color: '#9ca3af' }}>
        {user?.fullName} ({user?.role})
      </span>
      <button
        onClick={async () => {
          await logout();
          navigate('/login');
        }}
      >
        Log out
      </button>
    </nav>
  );
}
