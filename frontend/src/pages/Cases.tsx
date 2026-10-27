import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listCases, createCase } from '../api/cases';
import { CaseResponse } from '../types';

export default function Cases() {
  const [cases, setCases] = useState<CaseResponse[]>([]);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('general');
  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    try {
      const page = await listCases();
      setCases(page.content);
    } catch {
      setError('Could not load cases.');
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await createCase(title, description, category);
      setTitle('');
      setDescription('');
      await refresh();
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Could not create case.');
    }
  }

  return (
    <div className="container">
      <h2>Cases</h2>

      <div className="card">
        <h3>Submit a new case</h3>
        <form onSubmit={handleCreate}>
          <div className="field">
            <label>Title</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} required />
          </div>
          <div className="field">
            <label>Description</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} />
          </div>
          <div className="field">
            <label>Category</label>
            <select value={category} onChange={(e) => setCategory(e.target.value)}>
              <option value="general">General</option>
              <option value="public-works">Public Works</option>
              <option value="permits">Permits</option>
              <option value="social-services">Social Services</option>
            </select>
          </div>
          {error && <p className="error">{error}</p>}
          <button type="submit">Submit case</button>
        </form>
      </div>

      <div className="card">
        <h3>Your cases</h3>
        <table>
          <thead>
            <tr>
              <th>Reference</th>
              <th>Title</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {cases.map((c) => (
              <tr key={c.id}>
                <td>{c.referenceNo}</td>
                <td>{c.title}</td>
                <td>
                  <span className="badge">{c.status}</span>
                </td>
                <td>
                  <Link to={`/cases/${c.id}`}>View</Link>
                </td>
              </tr>
            ))}
            {cases.length === 0 && (
              <tr>
                <td colSpan={4}>No cases yet.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
