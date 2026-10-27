import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getCase, uploadDocument } from '../api/cases';
import { CaseResponse } from '../types';

export default function CaseDetail() {
  const { id } = useParams<{ id: string }>();
  const [caseData, setCaseData] = useState<CaseResponse | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (id) getCase(Number(id)).then(setCaseData);
  }, [id]);

  async function handleUpload(e: React.FormEvent) {
    e.preventDefault();
    if (!file || !id) return;
    try {
      await uploadDocument(Number(id), file);
      setMessage('Document uploaded successfully.');
      setFile(null);
    } catch {
      setMessage('Upload failed.');
    }
  }

  if (!caseData) return <div className="container">Loading…</div>;

  return (
    <div className="container">
      <h2>{caseData.title}</h2>
      <div className="card">
        <p>
          <strong>Reference:</strong> {caseData.referenceNo}
        </p>
        <p>
          <strong>Status:</strong> <span className="badge">{caseData.status}</span>
        </p>
        <p>
          <strong>Category:</strong> {caseData.category}
        </p>
        <p>
          <strong>Description:</strong> {caseData.description}
        </p>
      </div>

      <div className="card">
        <h3>Upload supporting document</h3>
        <form onSubmit={handleUpload}>
          <div className="field">
            <input type="file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          </div>
          <button type="submit" disabled={!file}>
            Upload
          </button>
        </form>
        {message && <p>{message}</p>}
      </div>
    </div>
  );
}
