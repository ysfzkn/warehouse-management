import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';

const AdminAuditDetails = () => {
  const { entityType, entityId } = useParams();
  const navigate = useNavigate();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchLogs = async () => {
      try {
        setLoading(true);
        const res = await axios.get('/api/audit', { params: { entityType, entityId, size: 1000 } });
        setLogs(res.data || []);
      } catch (e) {
        setError('Kayıtlar yüklenemedi');
      } finally {
        setLoading(false);
      }
    };
    fetchLogs();
  }, [entityType, entityId]);

  const formatDate = (iso) => {
    try { return new Date(iso).toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' }); } catch { return iso; }
  };
  const label = entityType === 'Stock' ? 'Stok' : (entityType === 'StockTransfer' ? 'Transfer' : entityType);

  return (
    <div className="container py-3">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h3 className="mb-0">
          <i className="fas fa-clipboard-list me-2"></i>
          Denetim Kaydı
          <small className="text-muted ms-2">{label} {entityId}</small>
        </h3>
        <div>
          <button className="btn btn-outline-secondary" onClick={() => navigate(-1)}>
            <i className="fas fa-arrow-left me-2"></i>
            Geri
          </button>
        </div>
      </div>

      <div className="card">
        <div className="card-body">
          {loading && <div className="text-center py-4"><div className="spinner-border" role="status"></div></div>}
          {error && <div className="alert alert-danger" role="alert">{error}</div>}
          {!loading && !error && (
            logs.length === 0 ? (
              <div className="text-muted">Kayıt bulunamadı.</div>
            ) : (
              <div className="table-responsive">
                <table className="table table-striped">
                  <thead>
                    <tr>
                      <th>Tarih</th>
                      <th>Kullanıcı</th>
                      <th>İşlem</th>
                      <th>Detay</th>
                    </tr>
                  </thead>
                  <tbody>
                    {logs.map(l => (
                      <tr key={l.id}>
                        <td>{formatDate(l.createdAt)}</td>
                        <td>{l.username}</td>
                        <td><span className="badge bg-secondary">{l.action}</span></td>
                        <td style={{ whiteSpace: 'pre-wrap' }}>{l.details}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          )}
        </div>
      </div>
    </div>
  );
};

export default AdminAuditDetails;


