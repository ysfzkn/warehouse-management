import React, { useEffect, useState } from 'react';
import axios from 'axios';

const AuditTimelineModal = ({ entityType, entityId, onClose }) => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchLogs = async () => {
      try {
        setLoading(true);
        const res = await axios.get('/api/audit', { params: { entityType, entityId, size: 500 } });
        setLogs(res.data || []);
      } catch (e) {
        setError('Hareket geçmişi yüklenirken hata oluştu');
      } finally {
        setLoading(false);
      }
    };
    fetchLogs();
  }, [entityType, entityId]);

  const formatDate = (iso) => {
    try {
      const d = new Date(iso);
      return d.toLocaleString('tr-TR', { timeZone: 'Europe/Istanbul' });
    } catch {
      return iso;
    }
  };

  const actionLabel = (action) => {
    const map = {
      STOCK_CREATE: 'Stok Oluşturma',
      STOCK_UPDATE: 'Stok Güncelleme',
      STOCK_ADD: 'Stok Artırma',
      STOCK_REMOVE: 'Stok Azaltma',
      STOCK_DELETE: 'Stok Silme',
      STOCK_RESERVE: 'Rezervasyon',
      STOCK_RELEASE: 'Rezervasyon Çözümü',
      TRANSFER_CREATE: 'Transfer Oluşturma',
      TRANSFER_START: 'Transfer Yola Çıkarma',
      TRANSFER_COMPLETE: 'Transfer Tamamlama',
      TRANSFER_CANCEL: 'Transfer İptali',
      TRANSFER_UPDATE: 'Transfer Güncelleme',
      TRANSFER_DELETE: 'Transfer Silme'
    };
    return map[action] || action;
  };

  const actionMeta = (action) => {
    const base = { variant: 'secondary', icon: 'fa-info-circle' };
    const map = {
      STOCK_CREATE: { variant: 'primary', icon: 'fa-plus-circle' },
      STOCK_UPDATE: { variant: 'info', icon: 'fa-edit' },
      STOCK_ADD: { variant: 'success', icon: 'fa-plus' },
      STOCK_REMOVE: { variant: 'danger', icon: 'fa-minus' },
      STOCK_DELETE: { variant: 'dark', icon: 'fa-trash' },
      STOCK_RESERVE: { variant: 'warning', icon: 'fa-lock' },
      STOCK_RELEASE: { variant: 'secondary', icon: 'fa-unlock' },
      TRANSFER_CREATE: { variant: 'primary', icon: 'fa-exchange-alt' },
      TRANSFER_START: { variant: 'info', icon: 'fa-play' },
      TRANSFER_COMPLETE: { variant: 'success', icon: 'fa-check' },
      TRANSFER_CANCEL: { variant: 'danger', icon: 'fa-ban' },
      TRANSFER_UPDATE: { variant: 'info', icon: 'fa-edit' },
      TRANSFER_DELETE: { variant: 'dark', icon: 'fa-trash' }
    };
    return map[action] || base;
  };

  const translateDetails = (details, action) => {
    if (!details || typeof details !== 'string') return details;
    const patterns = [
      {
        re: /^Added (\d+) to Product=(.*), Warehouse=(.*) \(New=(\d+)\)$/,
        fn: (m) => `Stok artırma: +${m[1]} adet → Yeni=${m[4]} | Depo=${m[3]}, Ürün=${m[2]}`
      },
      {
        re: /^Removed (\d+) from Product=(.*), Warehouse=(.*) \(New=(\d+)\)$/,
        fn: (m) => `Stok azaltma: -${m[1]} adet → Yeni=${m[4]} | Depo=${m[3]}, Ürün=${m[2]}`
      },
      {
        re: /^Deleted stock Product=(.*), Warehouse=(.*)$/,
        fn: (m) => `Stok silme: Depo=${m[2]}, Ürün=${m[1]}`
      },
      {
        re: /^Updated quantities for Product=(.*), Warehouse=(.*)$/,
        fn: (m) => `Stok güncelleme: Depo=${m[2]}, Ürün=${m[1]}`
      },
      {
        re: /^Reserved (\d+) of Product=(.*), Warehouse=(.*) \(Reserved=(\d+)\)$/,
        fn: (m) => `Rezervasyon: ${m[1]} adet ayrıldı (Toplam Ayrılan=${m[4]}) | Depo=${m[3]}, Ürün=${m[2]}`
      },
      {
        re: /^Released (\d+) of Product=(.*), Warehouse=(.*) \(Reserved=(\d+)\)$/,
        fn: (m) => `Rezervasyon çözümü: ${m[1]} adet iade edildi (Kalan Ayrılan=${m[4]}) | Depo=${m[3]}, Ürün=${m[2]}`
      },
      {
        re: /^Started: (.*) -> (.*), Product=(.*), Qty=(\d+)$/,
        fn: (m) => `Transfer başlatma: ${m[1]} → ${m[2]} | Ürün=${m[3]} | Adet=${m[4]} (Stok rezerve edildi)`
      },
      {
        re: /^Completed: (.*) -> (.*), Product=(.*), Qty=(\d+)$/,
        fn: (m) => `Transfer tamamlama: ${m[1]} → ${m[2]} | Ürün=${m[3]} | Adet=${m[4]}`
      },
      {
        re: /^Cancelled: reason=(.*)$/,
        fn: (m) => `Transfer iptali: Sebep=${m[1]}`
      },
      {
        re: /^(.*) -> (.*), Product=(.*), Qty=(\d+)$/,
        fn: (m) => {
          // Fallback for old create details without prefix
          if (action === 'TRANSFER_CREATE') {
            return `Transfer oluşturma: ${m[1]} → ${m[2]} | Ürün=${m[3]} | Adet=${m[4]}`;
          }
          return details;
        }
      }
    ];
    for (const p of patterns) {
      const m = details.match(p.re);
      if (m) return p.fn(m);
    }
    return details;
  };

  const parseStockChange = (details) => {
    if (!details) return null;
    // Matches our normalized messages
    let m = details.match(/^Stok (artırma|azaltma): ([+-]?\d+) adet → Yeni=(\d+) \| Depo=(.*), Ürün=(.*)$/);
    if (m) {
      return {
        type: m[1] === 'artırma' ? 'add' : 'remove',
        delta: parseInt(m[2], 10),
        newQty: parseInt(m[3], 10),
        warehouse: m[4],
        product: m[5]
      };
    }
    m = details.match(/^Rezervasyon: (\d+) adet ayrıldı \(Toplam Ayrılan=(\d+)\) \| Depo=(.*), Ürün=(.*)$/);
    if (m) {
      return { type: 'reserve', amount: parseInt(m[1], 10), totalReserved: parseInt(m[2], 10), warehouse: m[3], product: m[4] };
    }
    m = details.match(/^Rezervasyon çözümü: (\d+) adet iade edildi \(Kalan Ayrılan=(\d+)\) \| Depo=(.*), Ürün=(.*)$/);
    if (m) {
      return { type: 'release', amount: parseInt(m[1], 10), totalReserved: parseInt(m[2], 10), warehouse: m[3], product: m[4] };
    }
    return null;
  };

  const parseTransferChange = (details) => {
    if (!details) return null;
    let m = details.match(/^Transfer oluşturma: (.*) → (.*) \| Ürün=(.*) \| Adet=(\d+)$/);
    if (m) return { kind: 'create', src: m[1], dst: m[2], product: m[3], qty: parseInt(m[4], 10) };
    m = details.match(/^Transfer başlatma: (.*) → (.*) \| Ürün=(.*) \| Adet=(\d+) \(Stok rezerve edildi\)$/);
    if (m) return { kind: 'start', src: m[1], dst: m[2], product: m[3], qty: parseInt(m[4], 10) };
    m = details.match(/^Transfer tamamlama: (.*) → (.*) \| Ürün=(.*) \| Adet=(\d+)$/);
    if (m) return { kind: 'complete', src: m[1], dst: m[2], product: m[3], qty: parseInt(m[4], 10) };
    m = details.match(/^Transfer iptali: Sebep=(.*)$/);
    if (m) return { kind: 'cancel', reason: m[1] };
    return null;
  };

  const renderPrettyDetails = (details) => {
    const parsed = parseStockChange(details) || parseTransferChange(details);
    if (!parsed) {
      return <div className="text-muted mt-1" style={{ whiteSpace: 'pre-wrap' }}>{details}</div>;
    }
    // Stock add/remove
    if (parsed.type === 'add' || parsed.type === 'remove') {
      const isAdd = parsed.type === 'add';
      return (
        <div className="mt-2">
          <div className="d-flex flex-wrap align-items-center gap-2">
            <span className={`badge ${isAdd ? 'bg-success' : 'bg-danger'}`}>
              {isAdd ? '+' : ''}{parsed.delta} adet
            </span>
            <span className="badge bg-secondary">Yeni: {parsed.newQty}</span>
            <span className="badge bg-light text-dark"><i className="fas fa-warehouse me-1"></i>{parsed.warehouse}</span>
            <span className="badge bg-light text-dark"><i className="fas fa-box me-1"></i>{parsed.product}</span>
          </div>
        </div>
      );
    }
    // Stock reserve/release
    if (parsed.type === 'reserve' || parsed.type === 'release') {
      const isReserve = parsed.type === 'reserve';
      return (
        <div className="mt-2">
          <div className="d-flex flex-wrap align-items-center gap-2">
            <span className={`badge ${isReserve ? 'bg-warning text-dark' : 'bg-info'}`}>
              {isReserve ? 'Ayrıldı' : 'İade'}: {parsed.amount}
            </span>
            <span className="badge bg-secondary">Ayrılan: {parsed.totalReserved}</span>
            <span className="badge bg-light text-dark"><i className="fas fa-warehouse me-1"></i>{parsed.warehouse}</span>
            <span className="badge bg-light text-dark"><i className="fas fa-box me-1"></i>{parsed.product}</span>
          </div>
        </div>
      );
    }
    // Transfer events
    if (parsed.kind) {
      const badgeFor = (k) => {
        switch (k) {
          case 'create': return <span className="badge bg-primary">Transfer oluşturma</span>;
          case 'start': return <span className="badge bg-info text-dark">Transfer yola çıkarma</span>;
          case 'complete': return <span className="badge bg-success">Transfer tamamlama</span>;
          case 'cancel': return <span className="badge bg-danger">Transfer iptali</span>;
          default: return null;
        }
      };
      return (
        <div className="mt-2">
          <div className="d-flex flex-wrap align-items-center gap-2">
            {badgeFor(parsed.kind)}
            {'src' in parsed && <span className="badge bg-light text-dark"><i className="fas fa-warehouse me-1"></i>Kaynak: {parsed.src}</span>}
            {'dst' in parsed && <span className="badge bg-light text-dark"><i className="fas fa-warehouse me-1"></i>Hedef: {parsed.dst}</span>}
            {'product' in parsed && <span className="badge bg-light text-dark"><i className="fas fa-box me-1"></i>{parsed.product}</span>}
            {'qty' in parsed && <span className="badge bg-secondary">Adet: {parsed.qty}</span>}
          </div>
          {'reason' in parsed && (
            <div className="text-muted small mt-1">Sebep: {parsed.reason}</div>
          )}
        </div>
      );
    }
    return <div className="text-muted mt-1" style={{ whiteSpace: 'pre-wrap' }}>{details}</div>;
  };

  const label = entityType === 'Stock' ? 'Stok' : (entityType === 'StockTransfer' ? 'Transfer' : entityType);

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-lg modal-dialog-scrollable">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">
              <i className="fas fa-history me-2"></i>
              Hareket Geçmişi
              <small className="text-muted ms-2">{label} {entityId}</small>
            </h5>
            <button type="button" className="btn-close" onClick={onClose}></button>
          </div>
          <div className="modal-body">
            {loading && (
              <div className="text-center py-4">
                <div className="spinner-border" role="status"></div>
              </div>
            )}
            {error && (
              <div className="alert alert-danger" role="alert">{error}</div>
            )}
            {!loading && !error && (
              logs.length === 0 ? (
                <div className="text-muted">Kayıt bulunamadı.</div>
              ) : (
                <ul className="list-group">
                  {logs.map(l => (
                    <li key={l.id} className="list-group-item">
                      <div className="d-flex align-items-start">
                        <div className="me-3 text-primary">
                          <i className="fas fa-dot-circle"></i>
                        </div>
                        <div className="flex-grow-1">
                          <div className="d-flex justify-content-between align-items-center">
                            <div className="d-flex align-items-center flex-wrap gap-2">
                              {(() => { const meta = actionMeta(l.action); return (
                                <span className={`badge rounded-pill bg-${meta.variant} px-3 py-2`} style={{fontSize: '0.9rem'}}>
                                  <i className={`fas ${meta.icon} me-2`}></i>
                                  {actionLabel(l.action)}
                                </span>
                              ); })()}
                              <span className="text-muted">•</span>
                              <strong>{l.username}</strong>
                            </div>
                            <small className="text-muted">{formatDate(l.createdAt)}</small>
                          </div>
                          {l.details && renderPrettyDetails(translateDetails(l.details, l.action))}
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )
            )}
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>Kapat</button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AuditTimelineModal;


