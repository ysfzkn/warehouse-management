import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import FilterChips from '../components/FilterChips';

/**
 * Teslimat makbuzu arşivi.
 *
 * Ekran tek bir liste değil, birkaç kuyruk: imzalı nüshası gelmemişler, taşıyıcısı
 * girilmemiş depo çıkışları, bugün teslim edilecekler ve tarihi geçmiş planlar. Hepsi
 * "makbuz" olduğu için aynı tabloda duruyor ama sorulan soru farklı — o yüzden üstteki
 * kartlar birer görünüm kısayolu, dekorasyon değil.
 *
 * Sayaçlar ile listeler sunucuda aynı Specification'dan üretiliyor; karttaki rakam ile
 * karta tıklayınca gelen satır sayısının ayrışması mümkün değil.
 */

const STATUS_META = {
  ISSUED: {
    label: 'Düzenlendi',
    cls: 'bg-primary-subtle text-primary-emphasis border border-primary-subtle',
  },
  DELIVERED: {
    label: 'Teslim Edildi',
    cls: 'bg-success-subtle text-success-emphasis border border-success-subtle',
  },
  CANCELLED: { label: 'İptal', cls: 'bg-danger-subtle text-danger-emphasis border border-danger-subtle' },
};

const formatDateTime = (value) => {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleString('tr-TR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return value;
  }
};

const formatDate = (value) => {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleDateString('tr-TR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return value;
  }
};

const startOfDay = (value) => {
  const date = new Date(value);
  date.setHours(0, 0, 0, 0);
  return date;
};

/**
 * Açık bir planın geri sayımı.
 *
 * Gün farkı gün başlarından hesaplanıyor, saat farkından değil: bugün 09:00'da bakan biri
 * için bu akşam 18:00'deki teslimat "bugün", yarın 08:00'deki "yarın". Ham saat farkı
 * ikisine de "0 gün" derdi. Sunucudaki kesitler de aynı kuralı kullanıyor.
 */
const planCountdown = (scheduledAt) => {
  if (!scheduledAt) return null;
  const days = Math.round((startOfDay(scheduledAt) - startOfDay(new Date())) / 86400000);
  if (days < 0) {
    const late = Math.abs(days);
    return { days, label: `${late} gün gecikti`, tone: 'danger', icon: 'fa-triangle-exclamation' };
  }
  if (days === 0) return { days, label: 'Bugün', tone: 'danger', icon: 'fa-truck-fast' };
  if (days === 1) return { days, label: 'Yarın', tone: 'warning', icon: 'fa-clock' };
  return { days, label: `${days} gün`, tone: 'warning', icon: 'fa-calendar-day' };
};

const EMPTY_FILTERS = {
  search: '',
  status: '',
  kind: '',
  plan: '',
  hasSignedCopy: '',
  carrierPending: '',
  dateField: 'ISSUED',
  from: '',
  to: '',
  sort: 'issuedAt,desc',
};

const KIND_LABELS = { DELIVERY: 'Teslimat Makbuzu', SERVICE_HANDOVER: 'Depo Çıkışı' };
const PLAN_LABELS = {
  SCHEDULED: 'Planlı (açık)',
  DUE_TODAY: 'Bugün teslim',
  OVERDUE: 'Gecikmiş',
  NONE: 'Planlı değil',
};
const DATE_FIELD_LABELS = {
  ISSUED: 'Düzenleme tarihi',
  SCHEDULED: 'Planlanan teslim',
  DELIVERED: 'Teslim tarihi',
};
const SORT_LABELS = {
  'issuedAt,desc': 'En son düzenlenen',
  'issuedAt,asc': 'En eski düzenlenen',
  'scheduledDeliveryAt,asc': 'En yakın teslim',
  'scheduledDeliveryAt,desc': 'En uzak teslim',
  'deliveredAt,desc': 'En son teslim edilen',
  'receiptNo,desc': 'Makbuz no (Z→A)',
};

export default function AdminDeliveryReceipts() {
  const [receipts, setReceipts] = useState([]);
  const [stats, setStats] = useState(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [downloading, setDownloading] = useState(null);
  const [selected, setSelected] = useState([]);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [applied, setApplied] = useState(EMPTY_FILTERS);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params = { page, size: pageSize, sort: applied.sort };
      if (applied.search.trim()) params.search = applied.search.trim();
      if (applied.status) params.status = applied.status;
      if (applied.kind) params.kind = applied.kind;
      if (applied.plan) params.plan = applied.plan;
      if (applied.hasSignedCopy !== '') params.hasSignedCopy = applied.hasSignedCopy;
      if (applied.carrierPending !== '') params.carrierPending = applied.carrierPending;
      // Tarih alanı yalnızca bir aralık verildiğinde anlamlı; tek başına gönderilirse
      // sunucuda hiçbir yükleme dönüşmüyor ama istek gereksiz yere karışıyor.
      if (applied.from || applied.to) params.dateField = applied.dateField;
      if (applied.from) params.from = `${applied.from}T00:00:00`;
      if (applied.to) params.to = `${applied.to}T23:59:59`;

      const res = await axios.get('/api/admin/delivery-receipts', { params });
      setReceipts(res.data?.content || []);
      setTotalPages(res.data?.totalPages || 0);
      setTotalElements(res.data?.totalElements || 0);
      setSelected([]);
    } catch (e) {
      setError(e?.response?.data?.message || 'Makbuzlar yüklenemedi.');
      setReceipts([]);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, applied]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    axios
      .get('/api/admin/delivery-receipts/stats')
      .then((res) => setStats(res.data))
      .catch(() => setStats(null));
  }, [applied]);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => setNotice(''), 4000);
    return () => clearTimeout(timer);
  }, [notice]);

  const applyFilters = (event) => {
    event.preventDefault();
    setPage(0);
    setApplied(filters);
  };

  const resetFilters = () => {
    setFilters(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    setPage(0);
  };

  /**
   * Kart kısayolları: filtreleri baştan kurar, üstüne eklemez.
   *
   * Birikmeli olsaydı "Gecikmiş"e tıklayan biri bir önceki görünümün tarih aralığını da
   * taşır ve boş liste görürdü — kartta yazan sayı ile ekrandaki satır sayısı tutmazdı.
   */
  const applyView = (patch) => {
    const next = { ...EMPTY_FILTERS, ...patch };
    setFilters(next);
    setApplied(next);
    setPage(0);
    if (patch.carrierPending !== undefined || patch.plan) setShowAdvanced(true);
  };

  const isActiveView = (patch) => {
    const next = { ...EMPTY_FILTERS, ...patch };
    return JSON.stringify(next) === JSON.stringify(applied);
  };

  const downloadPdf = async (receipt) => {
    setDownloading(receipt.id);
    try {
      const res = await axios.get(`/api/admin/stock-transfers/${receipt.transferId}/receipt/pdf`, {
        responseType: 'blob',
      });
      saveBlob(res.data, `makbuz-${receipt.receiptNo}.pdf`);
    } catch {
      setError('Makbuz indirilemedi.');
    } finally {
      setDownloading(null);
    }
  };

  /** Seçili makbuzlar tek dosyada — klasöre basılacak yığın tek tek indirilmiyor. */
  const downloadSelected = async () => {
    if (selected.length === 0) return;
    setDownloading('bulk');
    try {
      const transferIds = receipts.filter((r) => selected.includes(r.id)).map((r) => r.transferId);
      const res = await axios.post(
        '/api/admin/stock-transfers/receipts/bulk-pdf',
        { transferIds },
        { responseType: 'blob' }
      );
      saveBlob(res.data, `makbuzlar-${selected.length}.pdf`);
      setNotice(`${selected.length} makbuz tek PDF olarak indirildi.`);
    } catch {
      setError('Toplu PDF oluşturulamadı.');
    } finally {
      setDownloading(null);
    }
  };

  const saveBlob = (data, fileName) => {
    const url = window.URL.createObjectURL(new Blob([data], { type: 'application/pdf' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  };

  const toggleSelect = (id) =>
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));

  const toggleSelectAll = (checked) => setSelected(checked ? receipts.map((r) => r.id) : []);

  const hasActiveFilter = useMemo(() => JSON.stringify(applied) !== JSON.stringify(EMPTY_FILTERS), [applied]);

  /** Aktif filtreler tek tek kaldırılabilsin diye; hepsini temizlemek çoğu zaman fazla. */
  const chips = useMemo(() => {
    const clear = (key, value) => () => {
      const next = { ...applied, [key]: value };
      setFilters(next);
      setApplied(next);
      setPage(0);
    };
    const list = [];
    if (applied.search) {
      list.push({
        icon: 'fas fa-magnifying-glass',
        label: `"${applied.search}"`,
        onClear: clear('search', ''),
      });
    }
    if (applied.kind) {
      list.push({ icon: 'fas fa-file-lines', label: KIND_LABELS[applied.kind], onClear: clear('kind', '') });
    }
    if (applied.status) {
      list.push({
        icon: 'fas fa-circle-info',
        label: STATUS_META[applied.status]?.label || applied.status,
        onClear: clear('status', ''),
      });
    }
    if (applied.plan) {
      list.push({
        icon: 'fas fa-calendar-day',
        label: PLAN_LABELS[applied.plan],
        onClear: clear('plan', ''),
      });
    }
    if (applied.hasSignedCopy !== '') {
      list.push({
        icon: 'fas fa-paperclip',
        label: applied.hasSignedCopy === 'true' ? 'İmzalı nüsha var' : 'İmzalı nüsha bekleniyor',
        onClear: clear('hasSignedCopy', ''),
      });
    }
    if (applied.carrierPending !== '') {
      list.push({
        icon: 'fas fa-user-clock',
        label: applied.carrierPending === 'true' ? 'Taşıyıcı bekliyor' : 'Taşıyıcısı belli',
        onClear: clear('carrierPending', ''),
      });
    }
    if (applied.from || applied.to) {
      list.push({
        icon: 'fas fa-calendar',
        label: `${DATE_FIELD_LABELS[applied.dateField]}: ${applied.from || '…'} → ${applied.to || '…'}`,
        onClear: () => {
          const next = { ...applied, from: '', to: '' };
          setFilters(next);
          setApplied(next);
          setPage(0);
        },
      });
    }
    if (applied.sort !== EMPTY_FILTERS.sort) {
      list.push({
        icon: 'fas fa-arrow-down-wide-short',
        label: SORT_LABELS[applied.sort] || applied.sort,
        onClear: clear('sort', EMPTY_FILTERS.sort),
      });
    }
    return list;
  }, [applied]);

  const views = [
    {
      key: 'total',
      label: 'Toplam Makbuz',
      icon: 'fa-file-lines',
      tone: 'secondary',
      patch: {},
      hint: 'Tüm arşiv',
    },
    {
      key: 'scheduled',
      label: 'Planlı Teslimat',
      icon: 'fa-calendar-day',
      tone: 'warning',
      patch: { plan: 'SCHEDULED', sort: 'scheduledDeliveryAt,asc' },
      hint: 'Teslim günü bekleyen',
    },
    {
      key: 'dueToday',
      label: 'Bugün Teslim',
      icon: 'fa-truck-fast',
      tone: 'danger',
      patch: { plan: 'DUE_TODAY', sort: 'scheduledDeliveryAt,asc' },
      hint: 'Bugün çıkacak',
    },
    {
      key: 'overdue',
      label: 'Gecikmiş',
      icon: 'fa-triangle-exclamation',
      tone: 'danger',
      patch: { plan: 'OVERDUE', sort: 'scheduledDeliveryAt,asc' },
      hint: 'Tarihi geçti, kapanmadı',
    },
    {
      key: 'awaitingSignedCopy',
      label: 'İmzalı Nüsha Bekleyen',
      icon: 'fa-hourglass-half',
      tone: 'warning',
      patch: { hasSignedCopy: 'false' },
      hint: 'Kâğıt geri gelmedi',
    },
    {
      key: 'carrierPending',
      label: 'Taşıyıcı Bekliyor',
      icon: 'fa-user-clock',
      tone: 'info',
      patch: { carrierPending: 'true', kind: 'SERVICE_HANDOVER' },
      hint: 'Şoför/plaka girilmedi',
    },
  ];

  const allSelected = receipts.length > 0 && selected.length === receipts.length;

  return (
    <div className="container-fluid py-3">
      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <h4 className="mb-0">
          <i className="fas fa-file-invoice me-2 text-primary"></i>
          Teslimat Makbuzları
        </h4>
        <Link to="/stock" className="btn btn-sm btn-outline-secondary">
          <i className="fas fa-arrow-left me-1"></i>
          Sevkiyatlara Dön
        </Link>
      </div>

      {/* ── Görünüm kısayolları ──
          Altı kart da tıklanabilir ve hepsi aynı görünüyor; eskiden yalnızca biri
          filtreleyebiliyordu ama diğer üçünden ayırt edilemiyordu, dolayısıyla kimse
          denemiyordu. Aktif olan kart çerçeveyle işaretleniyor. */}
      {stats && (
        <div className="row g-2 mb-3">
          {views.map((view) => {
            const active = isActiveView(view.patch);
            return (
              <div className="col-6 col-md-4 col-xl-2" key={view.key}>
                <button
                  type="button"
                  className={`card h-100 w-100 text-start shadow-sm border ${
                    active ? `border-${view.tone} border-2 bg-${view.tone}-subtle` : 'border-light'
                  }`}
                  onClick={() => applyView(view.patch)}
                  title={`${view.label} — listelemek için tıklayın`}
                  aria-pressed={active}
                >
                  <div className="card-body py-3 px-3">
                    <div className="small text-muted text-truncate">{view.label}</div>
                    <div className={`h4 mb-0 text-${view.tone}`}>
                      <i className={`fas ${view.icon} me-2`}></i>
                      {stats[view.key] ?? 0}
                    </div>
                    <div className="text-muted text-truncate" style={{ fontSize: '0.7rem' }}>
                      {view.hint}
                    </div>
                  </div>
                </button>
              </div>
            );
          })}
        </div>
      )}

      <form className="card border-0 shadow-sm mb-3" onSubmit={applyFilters}>
        <div className="card-body py-3">
          <div className="row g-2 align-items-end">
            <div className="col-md-4">
              <label className="form-label small mb-1">Ara</label>
              <input
                type="text"
                className="form-control form-control-sm"
                placeholder="Makbuz no, müşteri, şoför, plaka, sipariş no"
                value={filters.search}
                onChange={(e) => setFilters((p) => ({ ...p, search: e.target.value }))}
              />
            </div>
            <div className="col-md-3 col-lg-2">
              <label className="form-label small mb-1">Belge Tipi</label>
              <select
                className="form-select form-select-sm"
                value={filters.kind}
                onChange={(e) => setFilters((p) => ({ ...p, kind: e.target.value }))}
              >
                <option value="">Tümü</option>
                <option value="DELIVERY">Teslimat Makbuzu</option>
                <option value="SERVICE_HANDOVER">Depo Çıkışı</option>
              </select>
            </div>
            <div className="col-md-3 col-lg-2">
              <label className="form-label small mb-1">Teslim Planı</label>
              <select
                className="form-select form-select-sm"
                value={filters.plan}
                onChange={(e) => setFilters((p) => ({ ...p, plan: e.target.value }))}
              >
                <option value="">Tümü</option>
                <option value="SCHEDULED">Planlı (açık)</option>
                <option value="DUE_TODAY">Bugün teslim</option>
                <option value="OVERDUE">Gecikmiş</option>
                <option value="NONE">Planlı değil</option>
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-1">Durum</label>
              <select
                className="form-select form-select-sm"
                value={filters.status}
                onChange={(e) => setFilters((p) => ({ ...p, status: e.target.value }))}
              >
                <option value="">Tümü</option>
                <option value="ISSUED">Düzenlendi</option>
                <option value="DELIVERED">Teslim Edildi</option>
                <option value="CANCELLED">İptal</option>
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-1">Sıralama</label>
              <select
                className="form-select form-select-sm"
                value={filters.sort}
                onChange={(e) => setFilters((p) => ({ ...p, sort: e.target.value }))}
              >
                {Object.entries(SORT_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Gelişmiş alanlar katlanabilir: altı filtre birden açıkta dururken hiçbiri
              göze çarpmıyordu ve asıl iş olan arama kutusu satırın içinde kayboluyordu. */}
          {showAdvanced && (
            <div className="row g-2 align-items-end mt-1 pt-3 border-top">
              <div className="col-md-3 col-lg-2">
                <label className="form-label small mb-1">İmzalı Nüsha</label>
                <select
                  className="form-select form-select-sm"
                  value={filters.hasSignedCopy}
                  onChange={(e) => setFilters((p) => ({ ...p, hasSignedCopy: e.target.value }))}
                >
                  <option value="">Tümü</option>
                  <option value="true">Yüklendi</option>
                  <option value="false">Bekleniyor</option>
                </select>
              </div>
              <div className="col-md-3 col-lg-2">
                <label className="form-label small mb-1">Taşıyıcı</label>
                <select
                  className="form-select form-select-sm"
                  value={filters.carrierPending}
                  onChange={(e) => setFilters((p) => ({ ...p, carrierPending: e.target.value }))}
                >
                  <option value="">Tümü</option>
                  <option value="true">Bekliyor (depo çıkışı)</option>
                  <option value="false">Belli</option>
                </select>
              </div>
              <div className="col-md-3 col-lg-3">
                {/* Bir makbuzun üç tarihi var ve planlı teslimatta üçü ayrı güne
                    düşebiliyor; "hangi tarih" sorusunu kullanıcı cevaplıyor. */}
                <label className="form-label small mb-1">Tarih Alanı</label>
                <select
                  className="form-select form-select-sm"
                  value={filters.dateField}
                  onChange={(e) => setFilters((p) => ({ ...p, dateField: e.target.value }))}
                >
                  {Object.entries(DATE_FIELD_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-md-3 col-lg-2">
                <label className="form-label small mb-1">Başlangıç</label>
                <input
                  type="date"
                  className="form-control form-control-sm"
                  value={filters.from}
                  onChange={(e) => setFilters((p) => ({ ...p, from: e.target.value }))}
                />
              </div>
              <div className="col-md-3 col-lg-2">
                <label className="form-label small mb-1">Bitiş</label>
                <input
                  type="date"
                  className="form-control form-control-sm"
                  value={filters.to}
                  onChange={(e) => setFilters((p) => ({ ...p, to: e.target.value }))}
                />
              </div>
            </div>
          )}

          <div className="d-flex gap-2 mt-3 flex-wrap align-items-center">
            <button type="submit" className="btn btn-sm btn-primary">
              <i className="fas fa-magnifying-glass me-1"></i>
              Filtrele
            </button>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              onClick={() => setShowAdvanced((v) => !v)}
            >
              <i className={`fas fa-sliders me-1`}></i>
              {showAdvanced ? 'Gelişmiş Filtreleri Gizle' : 'Gelişmiş Filtreler'}
            </button>
            {hasActiveFilter && (
              <button type="button" className="btn btn-sm btn-outline-danger" onClick={resetFilters}>
                <i className="fas fa-xmark me-1"></i>
                Temizle
              </button>
            )}
          </div>

          {chips.length > 0 && <FilterChips chips={chips} onClearAll={resetFilters} className="mt-3 small" />}
        </div>
      </form>

      {error && (
        <div className="alert alert-danger py-2 px-3 small">
          <i className="fas fa-triangle-exclamation me-1"></i>
          {error}
        </div>
      )}
      {notice && (
        <div className="alert alert-success py-2 px-3 small">
          <i className="fas fa-circle-check me-1"></i>
          {notice}
        </div>
      )}

      <div className="card border-0 shadow-sm">
        {selected.length > 0 && (
          <div className="card-header bg-primary-subtle border-0 d-flex flex-wrap align-items-center justify-content-between gap-2 py-2">
            <span className="small fw-semibold text-primary-emphasis">
              <i className="fas fa-check-double me-1"></i>
              {selected.length} makbuz seçildi
            </span>
            <div className="d-flex gap-2">
              <button
                type="button"
                className="btn btn-sm btn-primary"
                disabled={downloading === 'bulk'}
                onClick={downloadSelected}
              >
                <i
                  className={`fas ${downloading === 'bulk' ? 'fa-spinner fa-spin' : 'fa-file-pdf'} me-1`}
                ></i>
                Tek PDF İndir
              </button>
              <button
                type="button"
                className="btn btn-sm btn-outline-secondary"
                onClick={() => setSelected([])}
              >
                Seçimi Bırak
              </button>
            </div>
          </div>
        )}

        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th style={{ width: 36 }}>
                  <input
                    type="checkbox"
                    className="form-check-input"
                    checked={allSelected}
                    onChange={(e) => toggleSelectAll(e.target.checked)}
                    aria-label="Tümünü seç"
                  />
                </th>
                <th className="small">Makbuz No</th>
                <th className="small">Müşteri / Alıcı</th>
                <th className="small d-none d-xl-table-cell">Şoför / Plaka</th>
                <th className="small">Teslim</th>
                <th className="small d-none d-lg-table-cell">Teslim Alan</th>
                <th className="small text-center">Durum</th>
                <th className="small text-center d-none d-md-table-cell">İmzalı Nüsha</th>
                <th className="small text-center" style={{ width: 110 }}>
                  İşlemler
                </th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={9} className="text-center text-muted py-4">
                    <i className="fas fa-spinner fa-spin me-2"></i>
                    Yükleniyor…
                  </td>
                </tr>
              )}
              {!loading && receipts.length === 0 && (
                <tr>
                  <td colSpan={9} className="text-center text-muted py-5">
                    <i className="fas fa-inbox fa-2x d-block mb-2 opacity-50"></i>
                    {hasActiveFilter ? 'Bu filtrelerle kayıt bulunamadı.' : 'Henüz makbuz düzenlenmemiş.'}
                    {hasActiveFilter && (
                      <div className="mt-2">
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-secondary"
                          onClick={resetFilters}
                        >
                          Filtreleri temizle
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              )}
              {!loading &&
                receipts.map((r) => {
                  const status = STATUS_META[r.status] || STATUS_META.ISSUED;
                  const isHandover = r.kind === 'SERVICE_HANDOVER';
                  // Plan yalnızca teslim edilmemiş ve iptal olmamış makbuzda "açık";
                  // teslim edilmiş bir planın geri sayımı artık bir iş değil, tarihçe.
                  const openPlan =
                    r.scheduledDeliveryAt && !r.deliveredAt && r.status !== 'CANCELLED'
                      ? planCountdown(r.scheduledDeliveryAt)
                      : null;
                  const carrierPending = isHandover && !r.driverName && r.status !== 'CANCELLED';
                  return (
                    <tr
                      key={r.id}
                      className={
                        openPlan?.days < 0 ? 'table-danger' : selected.includes(r.id) ? 'table-active' : ''
                      }
                    >
                      <td>
                        <input
                          type="checkbox"
                          className="form-check-input"
                          checked={selected.includes(r.id)}
                          onChange={() => toggleSelect(r.id)}
                          aria-label={`${r.receiptNo} seç`}
                        />
                      </td>
                      <td>
                        <div className="fw-semibold small">{r.receiptNo}</div>
                        <div className="d-flex flex-wrap gap-1 mt-1">
                          {/* Numara zaten seriyi ele veriyor (DC-/TM-), ama arşivde iki belge
                              yan yana duruyor ve ön eki okumak bir rozetten yavaş. */}
                          {isHandover && (
                            <span
                              className="badge rounded-pill bg-info-subtle text-info-emphasis border border-info-subtle"
                              style={{ fontSize: '0.66rem' }}
                            >
                              <i className="fas fa-file-export me-1"></i>
                              Depo çıkışı
                            </span>
                          )}
                          {r.revision > 1 && (
                            <span
                              className="badge rounded-pill bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle"
                              style={{ fontSize: '0.66rem' }}
                              title={`${r.revision}. kez basıldı`}
                            >
                              {r.revision}. basım
                            </span>
                          )}
                        </div>
                        <div className="text-muted mt-1" style={{ fontSize: '0.72rem' }}>
                          {formatDate(r.issuedAt)} · {r.issuedBy || '—'}
                        </div>
                      </td>
                      <td>
                        <div className="small fw-semibold">{r.customerFullName || '—'}</div>
                        <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                          {r.customerPhone || ''}
                          {r.orderNumber ? ` · ${r.orderNumber}` : ''}
                        </div>
                      </td>
                      <td className="d-none d-xl-table-cell">
                        {/* Taşıyıcı sonradan girildiğinde makbuz kaydına da işleniyor
                            (DeliveryReceiptService.noteCarrier), yani bu sütun boşsa
                            taşıyıcı gerçekten henüz belli değil. */}
                        {carrierPending ? (
                          <span
                            className="badge rounded-pill bg-secondary-subtle text-secondary-emphasis border border-secondary-subtle"
                            title="Bu depo çıkışında taşıyıcı henüz belirlenmedi."
                          >
                            <i className="fas fa-user-clock me-1"></i>
                            Taşıyıcı bekliyor
                          </span>
                        ) : (
                          <>
                            <div className="small">{r.driverName || '—'}</div>
                            <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                              {r.vehiclePlate || ''}
                            </div>
                          </>
                        )}
                      </td>
                      {/* ── Teslim ──
                          Planlı teslimat geldiğinden beri bu hücre iki tarihi birden
                          anlatmak zorunda: ne zaman gidecekti, ne zaman gitti. Tek tarih
                          gösteren eski hâl, teslim edilmemiş bir makbuzu teslim edilmiş
                          gibi okutuyordu. */}
                      <td className="small">
                        {r.deliveredAt ? (
                          <>
                            <div className="fw-semibold">{formatDateTime(r.deliveredAt)}</div>
                            {r.scheduledDeliveryAt && (
                              <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                                Planlanan: {formatDate(r.scheduledDeliveryAt)}
                              </div>
                            )}
                          </>
                        ) : openPlan ? (
                          <>
                            <div className="fw-semibold">{formatDateTime(r.scheduledDeliveryAt)}</div>
                            <span
                              className={`badge rounded-pill bg-${openPlan.tone}-subtle text-${openPlan.tone}-emphasis border border-${openPlan.tone}-subtle mt-1`}
                              style={{ fontSize: '0.66rem' }}
                            >
                              <i className={`fas ${openPlan.icon} me-1`}></i>
                              {openPlan.label}
                            </span>
                          </>
                        ) : isHandover ? (
                          <>
                            <div>{formatDateTime(r.transferDate)}</div>
                            <div className="text-muted" style={{ fontSize: '0.72rem' }}>
                              Çıkış tarihi
                            </div>
                          </>
                        ) : (
                          <span className="text-muted">Bekliyor</span>
                        )}
                      </td>
                      {/* Depo çıkışında kâğıdı imzalayan, müşteri değil malı devralan
                          servis olabiliyor; planlı çıkışta ise teslim anındaki alıcı. */}
                      <td className="small d-none d-lg-table-cell">
                        {r.receivedByName || (isHandover ? r.handoverToName : null) || '—'}
                      </td>
                      <td className="text-center">
                        <span className={`badge rounded-pill ${status.cls}`}>{status.label}</span>
                      </td>
                      <td className="text-center d-none d-md-table-cell">
                        {r.signedCopyOnFile ? (
                          <span className="badge rounded-pill bg-success-subtle text-success-emphasis border border-success-subtle">
                            <i className="fas fa-paperclip me-1"></i>
                            {r.attachments?.length || 1}
                          </span>
                        ) : (
                          <span className="badge rounded-pill bg-warning-subtle text-warning-emphasis border border-warning-subtle">
                            Bekleniyor
                          </span>
                        )}
                      </td>
                      <td className="text-center">
                        <div className="d-flex gap-1 justify-content-center">
                          <button
                            type="button"
                            className="btn btn-sm btn-outline-primary"
                            disabled={downloading === r.id}
                            onClick={() => downloadPdf(r)}
                            title="PDF indir"
                          >
                            <i
                              className={`fas ${downloading === r.id ? 'fa-spinner fa-spin' : 'fa-file-pdf'}`}
                            ></i>
                          </button>
                          <Link
                            to={`/stock?highlightTransfer=${r.transferId}`}
                            className="btn btn-sm btn-outline-secondary"
                            title={
                              openPlan
                                ? 'Sevkiyatı aç — teslimatı buradan tamamlarsınız'
                                : 'Sevkiyat detayını aç'
                            }
                          >
                            <i className="fas fa-up-right-from-square"></i>
                          </Link>
                        </div>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>

        <div className="card-footer bg-white d-flex justify-content-between align-items-center flex-wrap gap-2">
          <div className="small text-muted">
            Toplam {totalElements} kayıt
            {totalPages > 1 && ` · Sayfa ${page + 1}/${totalPages}`}
          </div>
          <div className="d-flex align-items-center gap-2">
            <select
              className="form-select form-select-sm"
              style={{ width: 90 }}
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setPage(0);
              }}
            >
              {[10, 20, 50, 100].map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <i className="fas fa-chevron-left"></i>
            </button>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              <i className="fas fa-chevron-right"></i>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
