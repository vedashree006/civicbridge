import { useEffect, useState } from 'react';
import { getDashboardSummary } from '../api/dashboard';
import { DashboardSummary } from '../types';

export default function Dashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getDashboardSummary()
      .then(setSummary)
      .catch(() => setError('Could not load dashboard summary.'));
  }, []);

  return (
    <div className="container">
      <h2>Dashboard</h2>
      {error && <p className="error">{error}</p>}
      {!summary && !error && <p>Loading…</p>}
      {summary && (
        <div className="card">
          <p>
            <strong>Total cases:</strong> {summary.totalCases}
          </p>
          <p>
            <strong>Unread notifications:</strong> {summary.unreadNotifications}
          </p>
          <h3>Cases by status</h3>
          <table>
            <tbody>
              {Object.entries(summary.casesByStatus).map(([status, count]) => (
                <tr key={status}>
                  <td>
                    <span className="badge">{status}</span>
                  </td>
                  <td>{count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
