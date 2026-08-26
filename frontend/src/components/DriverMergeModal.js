import React, { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { formatPhoneForDisplay } from '../utils/phone';

/**
 * "Mükerrer şoförleri birleştir" dialog.
 *
 * The directory fills itself from transfers, so the same driver ends up in it several times —
 * one row per phone number they ever used, each spelled slightly differently. This finds those
 * look-alikes and folds them into one record.
 *
 * Nothing is automatic. Two real people can share a name, so every group is opt-in, the survivor
 * is chosen explicitly, and the dialog states plainly how many transfers will be moved before
 * anything is written.
 */

const formatDate = (value) =>
  value
    ? new Date(value).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })
    : '—';

export default function DriverMergeModal({ onClose, onMerged, askCode, toast }) {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selection, setSelection] = useState({}); // groupKey -> { primaryId, memberIds: Set }
  const [merging, setMerging] = useState(false);
  // Deciding a group collapses it, so a long list of duplicates stays walkable: what is left
  // expanded is exactly what still needs a decision.
  const [collapsed, setCollapsed] = useState({});

  const groupKey = (g, i) => `${g.matchedOn}-${g.matchedValue}-${i}`;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/admin/drivers/duplicates');
      const found = res.data || [];
      setGroups(found);
      // Everything starts selected with the busiest record as survivor — the common case is
      // "yes, all of these are the same person".
      const initial = {};
      found.forEach((g, i) => {
        initial[groupKey(g, i)] = {
          primaryId: g.suggestedPrimaryId,
          memberIds: new Set(g.candidates.map((c) => c.id)),
        };
      });
      setSelection(initial);
    } catch (e) {
      toast.error(e.response?.data?.message || 'Mükerrer kayıtlar alınamadı.');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    load();
  }, [load]);

  const setPrimary = (key, id) => {
    setSelection((prev) => ({
      ...prev,
      // Choosing a survivor implies keeping it in the group.
      [key]: { primaryId: id, memberIds: new Set([...prev[key].memberIds, id]) },
    }));
    setCollapsed((prev) => ({ ...prev, [key]: true }));
  };

  const toggleCollapsed = (key) => setCollapsed((prev) => ({ ...prev, [key]: !prev[key] }));

  const toggleMember = (key, id) =>
    setSelection((prev) => {
      const next = new Set(prev[key].memberIds);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      let primaryId = prev[key].primaryId;
      // The survivor cannot be a record we just excluded.
      if (!next.has(primaryId)) primaryId = next.values().next().value ?? null;
      return { ...prev, [key]: { primaryId, memberIds: next } };
    });

  /** Groups that would actually change something: a survivor plus at least one record to fold in. */
  const plan = groups
    .map((g, i) => {
      const key = groupKey(g, i);
      const chosen = selection[key];
      if (!chosen?.primaryId) return null;
      const duplicateIds = [...chosen.memberIds].filter((id) => id !== chosen.primaryId);
      if (duplicateIds.length === 0) return null;
      const movedTransfers = g.candidates
        .filter((c) => duplicateIds.includes(c.id))
        .reduce((sum, c) => sum + (c.linkedTransfers || 0), 0);
      return { key, group: g, primaryId: chosen.primaryId, duplicateIds, movedTransfers };
    })
    .filter(Boolean);

  const totalRecords = plan.reduce((sum, p) => sum + p.duplicateIds.length, 0);
  const totalTransfers = plan.reduce((sum, p) => sum + p.movedTransfers, 0);

  const merge = async () => {
    if (plan.length === 0) return;
    const code = await askCode({
      title: 'Yönetici Güvenlik Şifresi',
      description: `${totalRecords} şoför kaydı birleştirilecek. Devam etmek için güvenlik şifresini girin.`,
    });
    if (code === null) return;

    setMerging(true);
    let merged = 0;
    let moved = 0;
    const failures = [];
    // One request per group: a failure on one group must not roll back the ones that worked.
    for (const item of plan) {
      try {
        const res = await axios.post(
          '/api/admin/drivers/merge',
          { primaryId: item.primaryId, duplicateIds: item.duplicateIds },
          { headers: { 'X-ADMIN-SECURITY-CODE': code } }
        );
        merged += res.data?.mergedRecords || 0;
        moved += res.data?.repointedTransfers || 0;
      } catch (e) {
        failures.push(e.response?.data?.message || 'Bir grup birleştirilemedi.');
      }
    }
    setMerging(false);

    if (merged > 0) {
      toast.success(`${merged} kayıt birleştirildi, ${moved} transfer bağlandı.`);
    }
    if (failures.length > 0) {
      toast.error(failures[0]);
      load();
      onMerged();
      return;
    }
    onMerged();
    onClose();
  };

  return (
    <div className="modal show d-block" style={{ background: 'rgba(15,23,42,0.6)', zIndex: 3100 }}>
      {/* No backdrop dismissal — this dialog gates a destructive action. */}
      <div className="modal-dialog modal-xl modal-dialog-centered modal-dialog-scrollable">
        <div className="modal-content border-0 shadow-lg" style={{ borderRadius: 16 }}>
          <div className="modal-header">
            <div>
              <h5 className="modal-title fw-bold">
                <i className="fas fa-object-group text-primary me-2" />
                Mükerrer Şoförleri Birleştir
              </h5>
              <p className="text-muted small mb-0 mt-1">
                Aynı kişiye ait görünen kayıtlar. Geçmiş transferlerin şoför bilgileri değişmez; yalnızca
                hangi rehber kaydına bağlı oldukları güncellenir.
              </p>
            </div>
            <button className="btn-close" onClick={onClose} disabled={merging} />
          </div>

          <div className="modal-body">
            {loading ? (
              <div className="text-center py-5">
                <div className="spinner-border text-primary" />
              </div>
            ) : groups.length === 0 ? (
              <div className="text-center text-muted py-5">
                <i className="fas fa-circle-check d-block fs-1 mb-3 text-success opacity-75" />
                <h6 className="fw-semibold">Mükerrer kayıt bulunamadı</h6>
                <p className="small mb-0">Şoför rehberinde birleştirilecek bir şey yok.</p>
              </div>
            ) : (
              <>
                <div className="alert alert-info d-flex align-items-start gap-2 py-2 small">
                  <i className="fas fa-circle-info mt-1" />
                  <span>
                    Her grupta <strong>kalacak kaydı</strong> seçin. Aynı adı taşıyan iki farklı kişi varsa
                    grubu tamamen dışarıda bırakabilir veya tek tek işaretini kaldırabilirsiniz.
                  </span>
                </div>

                <div className="d-flex flex-column gap-3">
                  {groups.map((g, i) => {
                    const key = groupKey(g, i);
                    const chosen = selection[key] || { primaryId: null, memberIds: new Set() };
                    const isCollapsed = Boolean(collapsed[key]);
                    const survivor = g.candidates.find((c) => c.id === chosen.primaryId);
                    const foldCount = [...chosen.memberIds].filter((id) => id !== chosen.primaryId).length;
                    return (
                      <div
                        key={key}
                        className="border rounded-3"
                        style={isCollapsed ? { borderColor: '#a7f3d0' } : undefined}
                      >
                        <div
                          className="d-flex justify-content-between align-items-center flex-wrap gap-2 px-3 py-2 border-bottom"
                          style={{ background: isCollapsed ? '#f0fdf4' : '#f8f9fa', cursor: 'pointer' }}
                          onClick={() => toggleCollapsed(key)}
                        >
                          <div className="small d-flex align-items-center gap-2 flex-wrap">
                            <i
                              className={`fas fa-chevron-${isCollapsed ? 'right' : 'down'} text-muted`}
                              style={{ fontSize: 11 }}
                            />
                            <span className="badge bg-secondary">
                              {g.matchedOn === 'telefon' ? 'Aynı telefon' : 'Aynı ad'}
                            </span>
                            {isCollapsed && survivor ? (
                              <span>
                                <i className="fas fa-check-circle text-success me-1" />
                                <strong>{survivor.name}</strong> kalacak
                                {foldCount > 0 ? ` · ${foldCount} kayıt birleşecek` : ' · birleştirme yok'}
                              </span>
                            ) : (
                              <span className="text-muted">{g.candidates.length} kayıt</span>
                            )}
                          </div>
                          <div className="small text-muted">
                            {isCollapsed
                              ? 'Değiştirmek için tıklayın'
                              : `Toplam ${g.affectedTransfers} bağlı transfer`}
                          </div>
                        </div>
                        <div className="table-responsive" hidden={isCollapsed}>
                          <table className="table table-sm align-middle mb-0">
                            <thead>
                              <tr className="small text-muted">
                                <th style={{ width: 90 }}>Kalacak</th>
                                <th style={{ width: 70 }}>Dahil</th>
                                <th>Şoför</th>
                                <th>Telefon</th>
                                <th>TC</th>
                                <th>Plaka</th>
                                <th className="text-center">Transfer</th>
                                <th>Son Kullanım</th>
                              </tr>
                            </thead>
                            <tbody>
                              {g.candidates.map((c) => {
                                const included = chosen.memberIds.has(c.id);
                                const isPrimary = chosen.primaryId === c.id;
                                return (
                                  <tr
                                    key={c.id}
                                    className={!included ? 'text-muted' : ''}
                                    style={isPrimary ? { background: '#f0f7ff' } : undefined}
                                  >
                                    <td>
                                      <div className="form-check">
                                        <input
                                          className="form-check-input"
                                          type="radio"
                                          name={`primary-${key}`}
                                          checked={isPrimary}
                                          disabled={!included}
                                          onChange={() => setPrimary(key, c.id)}
                                        />
                                      </div>
                                    </td>
                                    <td>
                                      <div className="form-check">
                                        <input
                                          className="form-check-input"
                                          type="checkbox"
                                          checked={included}
                                          onChange={() => toggleMember(key, c.id)}
                                        />
                                      </div>
                                    </td>
                                    <td>
                                      <span className="fw-semibold">{c.name}</span>
                                      {isPrimary && (
                                        <span className="badge bg-primary ms-2">Kalacak kayıt</span>
                                      )}
                                    </td>
                                    <td className="small">
                                      {c.phone ? formatPhoneForDisplay(c.phone) : '—'}
                                    </td>
                                    <td className="small">{c.tcId || '—'}</td>
                                    <td className="small">{c.vehiclePlate || '—'}</td>
                                    <td className="text-center small">{c.transferCount || 0}</td>
                                    <td className="small">{formatDate(c.lastUsedAt)}</td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </>
            )}
          </div>

          <div className="modal-footer flex-wrap gap-2">
            {plan.length > 0 && (
              <span className="me-auto small">
                <i className="fas fa-arrow-right-arrow-left text-primary me-2" />
                <strong>{totalRecords} kayıt</strong> silinip {plan.length} şoförde birleşecek,{' '}
                <strong>{totalTransfers} transfer</strong> yeni kayda bağlanacak.
              </span>
            )}
            <button className="btn btn-outline-secondary" onClick={onClose} disabled={merging}>
              Vazgeç
            </button>
            <button className="btn btn-primary px-4" onClick={merge} disabled={merging || plan.length === 0}>
              {merging ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2" />
                  Birleştiriliyor…
                </>
              ) : (
                <>
                  <i className="fas fa-object-group me-2" />
                  Seçilenleri Birleştir
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
