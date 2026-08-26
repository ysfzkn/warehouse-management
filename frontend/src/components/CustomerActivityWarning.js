import React, { useCallback, useState } from 'react';
import axios from 'axios';

/**
 * "Bu müşteriye zaten teslimat yapılmış" uyarısı.
 *
 * Warehouse staff hand the same goods over twice more often than anyone likes: once as a manual
 * stock reduction with the customer written into the note, once as a customer-delivery transfer.
 * Before either is saved we look back over the last month and, if something turns up, show it
 * and let the operator decide. The check never blocks — a common surname must not stop work.
 */

const TYPE_META = {
  TRANSFER: { icon: 'fas fa-truck-ramp-box', label: 'Sevkiyat', color: 'primary' },
  STOCK_REMOVAL: { icon: 'fas fa-box-open', label: 'Manuel stok çıkışı', color: 'warning' },
  ORDER: { icon: 'fas fa-receipt', label: 'Sipariş', color: 'info' },
};
// Transfer and order statuses share one table — the codes do not collide.
const STATUS_LABELS = {
  PENDING: 'Bekliyor',
  IN_TRANSIT: 'Yolda',
  COMPLETED: 'Teslim edildi',
  SHIPPED: 'Kargoda',
  DELIVERED: 'Teslim edildi',
  RETURN_REQUESTED: 'İade talebi',
  RETURNED: 'İade edildi',
};

const formatDateTime = (value) =>
  value
    ? new Date(value).toLocaleString('tr-TR', {
        day: '2-digit',
        month: 'long',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—';

const daysAgo = (value) => {
  if (!value) return null;
  const diff = Math.floor((Date.now() - new Date(value).getTime()) / 86400000);
  if (diff <= 0) return 'bugün';
  if (diff === 1) return 'dün';
  return `${diff} gün önce`;
};

/**
 * Runs the look-up and remembers the answer for one save attempt.
 *
 * `confirm()` resolves true when it is safe to continue: either nothing was found, or the
 * operator acknowledged the warning. The acknowledgement is deliberately not sticky — a second
 * save attempt asks again, because by then the form may describe a different customer.
 */
export function useCustomerActivityCheck() {
  const [matches, setMatches] = useState([]);
  const [checking, setChecking] = useState(false);
  const [pending, setPending] = useState(null); // { resolve }
  const [days, setDays] = useState(30);

  const confirm = useCallback(async ({ name, phone, note, excludeTransferId, days: window = 30 }) => {
    const hasInput = [name, phone, note].some((v) => (v || '').trim().length >= 3);
    if (!hasInput) return true;

    setChecking(true);
    let found = [];
    try {
      const res = await axios.get('/api/admin/customer-activity/recent', {
        params: {
          name: name?.trim() || undefined,
          phone: phone?.trim() || undefined,
          note: note?.trim() || undefined,
          days: window,
          excludeTransferId: excludeTransferId || undefined,
        },
      });
      found = res.data?.matches || [];
      setDays(res.data?.days || window);
    } catch (e) {
      // The warning is advisory; a failed look-up must never block the actual operation.
      return true;
    } finally {
      setChecking(false);
    }

    if (found.length === 0) return true;
    setMatches(found);
    return new Promise((resolve) => setPending({ resolve }));
  }, []);

  const settle = useCallback(
    (proceed) => {
      pending?.resolve(proceed);
      setPending(null);
      setMatches([]);
    },
    [pending]
  );

  const dialog = pending ? (
    <CustomerActivityDialog
      matches={matches}
      days={days}
      onCancel={() => settle(false)}
      onProceed={() => settle(true)}
    />
  ) : null;

  // Stays busy while the dialog is open too, so the underlying form's submit button cannot be
  // triggered a second time (Enter key) behind the warning.
  return { confirm, checking: checking || pending !== null, dialog };
}

function CustomerActivityDialog({ matches, days, onCancel, onProceed }) {
  const strong = matches.filter((m) => m.confidence === 'HIGH');
  return (
    <div
      className="modal show d-block"
      role="dialog"
      aria-modal="true"
      aria-labelledby="customer-activity-title"
      style={{ background: 'rgba(15,23,42,0.6)', zIndex: 5500 }}
    >
      {/* No backdrop dismissal — this dialog gates a save, a stray click must not answer it. */}
      <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
        <div className="modal-content border-0 shadow-lg" style={{ borderRadius: 16 }}>
          <div className="modal-header border-0 pb-2">
            <div className="d-flex align-items-start gap-3">
              <span
                className="d-flex align-items-center justify-content-center flex-shrink-0"
                style={{ width: 46, height: 46, borderRadius: 13, background: '#fef3c7', color: '#d97706' }}
              >
                <i className="fas fa-triangle-exclamation" style={{ fontSize: 20 }} />
              </span>
              <div>
                <h5 id="customer-activity-title" className="modal-title fw-bold mb-1">
                  Bu müşteriye yakın zamanda teslimat yapılmış
                </h5>
                <p className="text-muted small mb-0">
                  Son <strong>{days} gün</strong> içinde eşleşen <strong>{matches.length} hareket</strong>{' '}
                  bulundu. Mükerrer çıkış olmadığından emin olun.
                </p>
              </div>
            </div>
          </div>
          <div className="modal-body pt-2">
            {strong.length > 0 && (
              <div className="alert alert-warning d-flex align-items-center gap-2 py-2 small">
                <i className="fas fa-circle-exclamation" />
                <span>
                  {strong.length} kayıt <strong>kesin eşleşme</strong> (telefon veya müşteri adı ile).
                </span>
              </div>
            )}
            <div className="d-flex flex-column gap-2">
              {matches.map((m) => {
                const meta = TYPE_META[m.type] || TYPE_META.TRANSFER;
                const high = m.confidence === 'HIGH';
                return (
                  <div
                    key={`${m.type}-${m.referenceId}`}
                    className="border rounded-3 p-3"
                    style={{
                      borderColor: high ? '#fbbf24' : '#e5e7eb',
                      background: high ? '#fffbeb' : '#fff',
                    }}
                  >
                    <div className="d-flex justify-content-between align-items-start gap-3 flex-wrap">
                      <div className="min-w-0">
                        <div className="d-flex align-items-center gap-2 flex-wrap mb-1">
                          <span className={`badge bg-${meta.color}`}>
                            <i className={`${meta.icon} me-1`} />
                            {meta.label}
                          </span>
                          <span className="fw-semibold">{m.referenceLabel}</span>
                          {m.status && (
                            <span className="badge bg-light text-dark border">
                              {STATUS_LABELS[m.status] || m.status}
                            </span>
                          )}
                          <span
                            className={`badge ${high ? 'bg-danger-subtle text-danger' : 'bg-secondary-subtle text-secondary'} border`}
                          >
                            {high ? 'Kesin eşleşme' : 'Olası eşleşme'} · {m.matchedOn}
                          </span>
                        </div>
                        <div className="small">
                          <i className="fas fa-user text-muted me-1" />
                          <strong>{m.customerName || 'Ad kaydedilmemiş'}</strong>
                          {m.customerPhone ? ` · ${m.customerPhone}` : ''}
                        </div>
                        {(m.productName || m.warehouseName) && (
                          <div className="small text-muted mt-1">
                            <i className="fas fa-box me-1" />
                            {m.productName || 'Ürün bilgisi yok'}
                            {m.quantity ? ` · ${m.quantity} adet` : ''}
                            {m.warehouseName ? ` · ${m.warehouseName}` : ''}
                          </div>
                        )}
                        {m.note && (
                          <div className="small text-muted mt-1 fst-italic text-truncate" title={m.note}>
                            “{m.note}”
                          </div>
                        )}
                      </div>
                      <div className="text-end flex-shrink-0">
                        <div className="fw-semibold small">{daysAgo(m.occurredAt)}</div>
                        <div className="text-muted" style={{ fontSize: 11 }}>
                          {formatDateTime(m.occurredAt)}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          <div className="modal-footer border-0 pt-0 flex-wrap gap-2">
            <span className="text-muted small me-auto">
              <i className="fas fa-circle-info me-1" />
              Bu bir uyarıdır; işlemi yine de kaydedebilirsiniz.
            </span>
            <button type="button" className="btn btn-outline-secondary px-4" onClick={onCancel}>
              Vazgeç, kontrol edeyim
            </button>
            <button type="button" className="btn btn-warning px-4 fw-semibold" onClick={onProceed}>
              <i className="fas fa-check me-2" />
              Mükerrer değil, devam et
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default CustomerActivityDialog;
