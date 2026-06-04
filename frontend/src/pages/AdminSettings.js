import { useEffect, useState, useMemo, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';
import ConfirmModal from '../components/ConfirmModal';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';

const CrudTable = ({
  title,
  columns,
  items,
  onCreate,
  onEdit,
  onDelete,
  loading,
  enableSelection = false,
  onBulkDelete,
  hasSelection = false,
  selectedCount = 0,
  onToggleAll,
  searchPlaceholder = 'Ara...',
  searchTerm,
  onSearchChange
}) => (
  <div className="card mb-4">
    <div className="card-header">
      <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-md-between gap-2">
        <div className="d-flex align-items-center gap-2">
          <h5 className="mb-0">{title}</h5>
          {enableSelection && selectedCount > 0 && (
            <span className="badge bg-primary rounded-pill">
              {selectedCount} seçili
            </span>
          )}
        </div>
        <div className="d-flex flex-column flex-sm-row align-items-stretch align-items-sm-center gap-2 w-100 w-md-auto">
          <div className="flex-grow-1 position-relative">
            <input
              type="text"
              className="form-control form-control-sm ps-5"
              placeholder={searchPlaceholder}
              value={searchTerm || ''}
              onChange={e => onSearchChange && onSearchChange(e.target.value)}
            />
            <span
              className="position-absolute top-50 start-0 translate-middle-y ms-3 text-muted"
              style={{ pointerEvents: 'none' }}
            >
              <i className="fas fa-search"></i>
            </span>
          </div>
          {enableSelection && (
            <button
              className="btn btn-outline-danger btn-sm"
              type="button"
              onClick={onBulkDelete}
              disabled={loading || !hasSelection}
            >
              <i className="fas fa-trash-alt me-1"></i>
              Seçilenleri Sil {selectedCount > 0 && `(${selectedCount})`}
            </button>
          )}
          <button className="btn btn-primary btn-sm" onClick={onCreate}>
            <i className="fas fa-plus me-2"></i>Yeni Ekle
          </button>
        </div>
      </div>
    </div>
    <div className="card-body p-0">
      <div className="table-responsive">
        <table className="table table-hover align-middle mb-0">
          <thead>
            <tr>
              {enableSelection && (
                <th style={{ width: 40 }}>
                  <input
                    type="checkbox"
                    className="form-check-input"
                    checked={items.length > 0 && items.every(it => it.__selected)}
                    onChange={e => onToggleAll && onToggleAll(e.target.checked, items)}
                  />
                </th>
              )}
              {columns.map(c => <th key={c.key}>{c.title}</th>)}
              <th style={{ width: 120 }}>İşlemler</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={columns.length + (enableSelection ? 2 : 1)} className="text-center py-4">
                  <span className="spinner-border" role="status" aria-hidden="true"></span>
                </td>
              </tr>
            )}
            {!loading && items.length === 0 && (
              <tr>
                <td colSpan={columns.length + (enableSelection ? 2 : 1)} className="text-center py-4 text-muted">Kayıt bulunamadı</td>
              </tr>
            )}
            {!loading && items.map(item => (
              <tr key={item.id}>
                {enableSelection && (
                  <td>
                    <input
                      type="checkbox"
                      className="form-check-input"
                      checked={!!item.__selected}
                      onChange={() => item.__onToggle && item.__onToggle(item)}
                    />
                  </td>
                )}
                {columns.map(c => (
                  <td key={c.key}>
                    {typeof c.render === 'function' ? c.render(item[c.key], item) : item[c.key]}
                  </td>
                ))}
                <td>
                  <div className="btn-group btn-group-sm">
                    <button className="btn btn-outline-secondary" onClick={() => onEdit(item)}>
                      <i className="fas fa-edit"></i>
                    </button>
                    <button className="btn btn-outline-danger" onClick={() => onDelete(item)}>
                      <i className="fas fa-trash"></i>
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  </div>
);

const EditModal = ({ title, fields, item, onClose, onSave, saving, error }) => {
  const [form, setForm] = useState({ name: '', description: '', active: true, hexCode: '' });

  useEffect(() => {
    if (item) {
      setForm({
        name: item.name || '',
        description: item.description || '',
        active: item.active !== false,
        hexCode: item.hexCode || ''
      });
    } else {
      setForm({ name: '', description: '', active: true, hexCode: '' });
    }
  }, [item]);

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">{title}</h5>
            <button type="button" className="btn-close" onClick={onClose}></button>
          </div>
          <div className="modal-body">
            {error && <div className="alert alert-danger" dangerouslySetInnerHTML={{ __html: error }} />}
            {fields.includes('name') && (
              <div className="mb-3">
                <label className="form-label">İsim</label>
                <input className="form-control" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
              </div>
            )}
            {fields.includes('description') && (
              <div className="mb-3">
                <label className="form-label">Açıklama</label>
                <input className="form-control" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} />
              </div>
            )}
            {fields.includes('hexCode') && (
              <div className="mb-3">
                <label className="form-label d-block">Renk Seçimi</label>
                <div className="d-flex flex-wrap gap-2 mb-2">
                  {[
                    { name: 'Siyah', hex: '#000000' },
                    { name: 'Beyaz', hex: '#FFFFFF' },
                    { name: 'Kırmızı', hex: '#FF0000' },
                    { name: 'Yeşil', hex: '#00FF00' },
                    { name: 'Mavi', hex: '#0000FF' },
                    { name: 'Sarı', hex: '#FFFF00' },
                    { name: 'Turuncu', hex: '#FFA500' },
                    { name: 'Mor', hex: '#800080' },
                    { name: 'Pembe', hex: '#FFC0CB' },
                    { name: 'Kahverengi', hex: '#8B4513' },
                    { name: 'Gri', hex: '#808080' },
                    { name: 'Camgöbeği', hex: '#00FFFF' }
                  ].map((c) => (
                    <button
                      type="button"
                      key={c.hex}
                      className="btn btn-light position-relative"
                      style={{
                        border: form.hexCode === c.hex ? '2px solid var(--bs-primary)' : '1px solid #ddd',
                        padding: 6,
                        borderRadius: 8
                      }}
                      onClick={() => setForm({ ...form, hexCode: c.hex })}
                      title={c.name}
                    >
                      <span
                        style={{
                          display: 'inline-block',
                          width: 24,
                          height: 24,
                          borderRadius: 4,
                          backgroundColor: c.hex,
                          border: '1px solid rgba(0,0,0,0.15)'
                        }}
                      />
                    </button>
                  ))}
                </div>
                <div className="d-flex align-items-center gap-2">
                  <input
                    type="color"
                    className="form-control form-control-color"
                    value={/^#[0-9A-Fa-f]{6}$/.test(form.hexCode || '') ? form.hexCode : '#000000'}
                    title="Özel renk seç"
                    onChange={(e) => setForm({ ...form, hexCode: e.target.value })}
                  />
                  <span className="text-muted small">Seçili: {form.hexCode || '-'}</span>
                </div>
              </div>
            )}
            {fields.includes('active') && (
              <div className="form-check">
                <input type="checkbox" className="form-check-input" id="active" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} />
                <label className="form-check-label" htmlFor="active">Aktif</label>
              </div>
            )}
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={onClose} disabled={saving}>İptal</button>
            <button className="btn btn-primary" onClick={() => onSave(form)} disabled={saving}>
              {saving ? <span className="spinner-border spinner-border-sm" /> : 'Kaydet'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

const UserModal = ({ user, onClose, onSave, saving, error }) => {
  const [form, setForm] = useState({ username: '', password: '', confirmPassword: '', role: 'STOCK_IN' });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [localError, setLocalError] = useState('');

  useEffect(() => {
    if (user) {
      setForm({ username: user.username || '', password: '', confirmPassword: '', role: user.role || 'STOCK_IN' });
    } else {
      setForm({ username: '', password: '', confirmPassword: '', role: 'STOCK_IN' });
    }
    setLocalError('');
  }, [user]);

  const passwordRequired = !user || form.password.length > 0;
  const passwordTooShort = passwordRequired && (!form.password || form.password.length < 5);
  const passwordsMismatch = passwordRequired && form.password !== form.confirmPassword;
  const usernameEmpty = !form.username || form.username.trim().length === 0;
  const canSave = !usernameEmpty && !passwordTooShort && !passwordsMismatch && (!passwordRequired || !!form.password);

  const currentValidationMessage = (() => {
    if (usernameEmpty) return 'Kullanıcı adı zorunludur.';
    if (passwordTooShort) return 'Parola en az 5 karakter olmalıdır.';
    if (passwordsMismatch) return 'Parola ve doğrulama aynı olmalıdır.';
    return '';
  })();

  const handleSaveClick = () => {
    const msg = currentValidationMessage;
    setLocalError(msg);
    if (msg) return;
    onSave(form);
  };

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">{user ? 'Kullanıcı Düzenle' : 'Yeni Kullanıcı'}</h5>
            <button type="button" className="btn-close" onClick={onClose}></button>
          </div>
          <div className="modal-body">
            {(error || localError) && (
              <div className="alert alert-danger" dangerouslySetInnerHTML={{ __html: error || localError }} />
            )}
            <div className="mb-3">
              <label className="form-label">Kullanıcı Adı</label>
              <input className="form-control" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} disabled={!!user} />
            </div>
            <div className="mb-3">
              <label className="form-label">Parola {user && <small className="text-muted">(değiştirmek istemiyorsanız boş bırakın)</small>}</label>
              <div className="input-group">
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="form-control"
                  value={form.password}
                  onChange={e => setForm({ ...form, password: e.target.value })}
                  autoComplete="new-password"
                  name="new-user-password"
                  aria-invalid={passwordTooShort || passwordsMismatch ? 'true' : 'false'}
                />
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={() => setShowPassword(prev => !prev)}
                >
                  <i className={`fas fa-eye${showPassword ? '-slash' : ''}`}></i>
                </button>
              </div>
            </div>
            <div className="mb-3">
              <label className="form-label">Parola Doğrulama</label>
              <div className="input-group">
                <input
                  type={showConfirm ? 'text' : 'password'}
                  className="form-control"
                  value={form.confirmPassword}
                  onChange={e => setForm({ ...form, confirmPassword: e.target.value })}
                  autoComplete="new-password"
                  name="new-user-password-confirm"
                  aria-invalid={passwordTooShort || passwordsMismatch ? 'true' : 'false'}
                />
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={() => setShowConfirm(prev => !prev)}
                >
                  <i className={`fas fa-eye${showConfirm ? '-slash' : ''}`}></i>
                </button>
              </div>
              {(passwordTooShort || passwordsMismatch) && (
                <div className="form-text text-danger">
                  {passwordTooShort
                    ? 'Parola en az 5 karakter olmalıdır.'
                    : 'Parola ve doğrulama aynı olmalıdır.'}
                </div>
              )}
            </div>
            <div className="mb-3">
              <label className="form-label">Yetki</label>
              <select className="form-select" value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}>
                <option value="ADMIN">Yönetici (Tam Yetki)</option>
                <option value="STOCK_IN">Stok Giriş Sorumlusu</option>
                <option value="STOCK_OUT">Stok Çıkış Sorumlusu</option>
              </select>
              <small className="text-muted d-block mt-1">
                {form.role === 'ADMIN' && '✓ Tüm işlemleri yapabilir, Excel ile stok yükleyebilir'}
                {form.role === 'STOCK_IN' && '✓ Sadece stok ekleme talebi oluşturabilir (Yönetici onayı gerekir)'}
                {form.role === 'STOCK_OUT' && '✓ Sadece stok çıkarma talebi oluşturabilir (Yönetici onayı gerekir)'}
              </small>
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={onClose} disabled={saving}>İptal</button>
            <button className="btn btn-primary" onClick={handleSaveClick} disabled={saving || !canSave}>
              {saving ? <span className="spinner-border spinner-border-sm" /> : 'Kaydet'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

const AdminSettings = ({ allowedTabs: allowedTabsProp }) => {
  const location = useLocation();
  const allowedTabsKey = useMemo(() => JSON.stringify(allowedTabsProp ?? []), [allowedTabsProp]);
  const allowedTabs = useMemo(
    () => (Array.isArray(allowedTabsProp) && allowedTabsProp.length ? [...allowedTabsProp] : ['brand', 'color', 'users']),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [allowedTabsKey]
  );
  const [activeTab, setActiveTab] = useState(allowedTabs[0] || 'users');
  const [brands, setBrands] = useState([]);
  const [colors, setColors] = useState([]);
  const [users, setUsers] = useState([]);
  const [brandSearch, setBrandSearch] = useState('');
  const [colorSearch, setColorSearch] = useState('');
  const [userSearch, setUserSearch] = useState('');
  const [selectedBrandIds, setSelectedBrandIds] = useState([]);
  const [selectedColorIds, setSelectedColorIds] = useState([]);
  const roleLabel = (r) => {
    if (r === 'ADMIN') return 'Yönetici';
    if (r === 'STOCK_IN') return 'Stok Giriş';
    if (r === 'STOCK_OUT') return 'Stok Çıkış';
    return r || '-';
  };

  // TOAST
  const showToast = (message, type = 'success') => {
    const toast = document.createElement('div');
    const bgClass =
      type === 'success'
        ? 'text-bg-success'
        : type === 'warning'
          ? 'text-bg-warning'
          : 'text-bg-danger';
    const icon =
      type === 'success'
        ? 'fa-check-circle'
        : type === 'warning'
          ? 'fa-exclamation-triangle'
          : 'fa-times-circle';
    toast.className = `toast align-items-center ${bgClass} border-0 position-fixed top-0 end-0 m-3 show`;
    toast.setAttribute('role', 'alert');
    toast.style.zIndex = '9999';
    toast.innerHTML = `
      <div class="d-flex align-items-center">
        <div class="toast-body d-flex align-items-start">
          <i class="fas ${icon} me-2 mt-1"></i>
          <div class="flex-grow-1">${message}</div>
        </div>
        <button type="button" class="btn-close ${type === 'success' ? 'btn-close-white' : ''
      } me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button>
      </div>
    `;
    document.body.appendChild(toast);
    setTimeout(() => {
      try {
        toast.classList.remove('show');
        setTimeout(() => {
          try {
            document.body.removeChild(toast);
          } catch { }
        }, 300);
      } catch { }
    }, type === 'success' ? 4000 : 7000);
  };


  // Helper to safely extract the error message from the backend along with its details
  const buildErrorMessage = (rawError, fallbackMessage) => {
    console.log(rawError)
    if (!rawError) {
      return fallbackMessage;
    }

    let apiError = rawError;

    // If it came as a JSON string, try to parse it
    if (typeof apiError === 'string') {
      try {
        apiError = JSON.parse(apiError);
      } catch {
        // If it can't be parsed, use it directly as a string
        return apiError || fallbackMessage;
      }
    }

    // From here on we assume it's an object
    const backendMessage =
      apiError && typeof apiError.message === 'string'
        ? apiError.message.trim()
        : null;

    // details may be either an array or a string
    let details = [];
    if (Array.isArray(apiError?.details)) {
      details = apiError.details;
    } else if (typeof apiError?.details === 'string') {
      details = apiError.details.split('\n').map(d => d.trim()).filter(Boolean);
    }

    // Base message
    const base = backendMessage || fallbackMessage;

    if (!details.length) {
      return base;
    }

    // List the details as HTML
    const listHtml = details
      .map(d => `• ${String(d).trim()}`)
      .join('<br/>');

    return `${base}<br/><br/><strong>İlgili ürünler:</strong><br/>${listHtml}`;
  };

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(undefined);
  const [confirmModal, setConfirmModal] = useState({ show: false, title: '', message: '', onConfirm: null });
  const [securityStatus, setSecurityStatus] = useState({ configured: false, loading: true });
  const [securityForm, setSecurityForm] = useState({ currentCode: '', newCode: '', confirmNewCode: '' });
  const [securitySaving, setSecuritySaving] = useState(false);
  const [showSecurityModal, setShowSecurityModal] = useState(false);
  const [showSecurityPassword, setShowSecurityPassword] = useState({
    current: false,
    new: false,
    confirm: false
  });
  const usersRef = useRef([]);
  const { askCode: askSecurityCode, SecurityCodePrompt, closePrompt: closeSecurityPrompt } = useSecurityCodePrompt();
  const adminSecurityErrorCodes = new Set([
    'AUTH_002', 'AUTH_003', 'AUTH_004', 'AUTH_005', 'AUTH_006', 'AUTH_007',
    'ADMIN_SECURITY_CODE_REQUIRED', 'INVALID_ADMIN_SECURITY_CODE', 'ADMIN_SECURITY_CODE_MISMATCH'
  ]);
  const parseSecurityError = (error) => {
    const data = error?.response?.data;
    const code = data?.code || data?.errorCode;
    const msg = data?.message || data?.error || error?.message || 'Beklenmeyen bir hata oluştu';
    return { code, msg };
  };

  const load = async ({ fetchBrands = true, fetchColors = true, fetchUsers = true } = {}) => {
    try {
      setLoading(true);
      const tasks = [];
      if (fetchBrands) {
        tasks.push(
          axios.get('/api/brands')
            .then(res => setBrands(res.data || []))
            .catch(() => { }) // keep the previous state
        );
      }
      if (fetchColors) {
        tasks.push(
          axios.get('/api/colors')
            .then(res => setColors(res.data || []))
            .catch(() => { })
        );
      }
      if (fetchUsers) {
        tasks.push(
          axios.get('/api/users')
            .then(res => {
              const incoming = Array.isArray(res.data) ? res.data : usersRef.current;
              console.log('[AdminSettings] users fetched', { next: incoming.length, prev: usersRef.current.length });
              // Guard: keep the old list on an unexpected drop from the current list's count
              if (incoming.length === 0 && usersRef.current.length > 0) {
                console.warn('[AdminSettings] empty users response, keeping previous list');
                setUsers(usersRef.current);
              } else if (incoming.length < usersRef.current.length - 1) {
                console.warn('[AdminSettings] users length dropped unexpectedly, keeping previous list');
                setUsers(usersRef.current);
              } else {
                setUsers(incoming);
              }
            })
            .catch((err) => {
              console.warn('[AdminSettings] users fetch failed, keeping previous list', err?.response || err);
              setUsers(usersRef.current);
            })
        );
      }
      await Promise.all(tasks);
    } finally {
      setLoading(false);
    }
  };

  const refreshUsers = async () => {
    const previous = usersRef.current;
    try {
      const res = await axios.get('/api/users');
      const list = Array.isArray(res.data) ? res.data : previous;
      console.log('[AdminSettings] refreshUsers', { prev: previous.length, next: list.length });
      if (list.length === 0 && previous.length > 0) {
        console.warn('[AdminSettings] refreshUsers got empty list, keeping previous');
        setUsers(previous);
      } else if (list.length < previous.length - 1) {
        console.warn('[AdminSettings] refreshUsers length dropped unexpectedly, keeping previous');
        setUsers(previous);
      } else {
        setUsers(list);
      }
    } catch (err) {
      console.warn('[AdminSettings] refreshUsers failed, keeping previous list', err?.response || err);
      setUsers(previous);
    }
  };

  // Keep a stable copy of users to avoid accidental drops
  useEffect(() => {
    if (Array.isArray(users)) {
      usersRef.current = users;
    }
  }, [users]);


  useEffect(() => {
    load({
      fetchBrands: allowedTabs.includes('brand'),
      fetchColors: allowedTabs.includes('color'),
      fetchUsers: allowedTabs.includes('users'),
    });
    // allowedTabsKey keeps array reference stable to prevent needless reloads
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allowedTabsKey]);

  useEffect(() => {
    const fetchSecurityStatus = async () => {
      try {
        setSecurityStatus(s => ({ ...s, loading: true }));
        const { data } = await axios.get('/api/admin/security-code/status');
        setSecurityStatus({ configured: !!data?.configured, loading: false });
      } catch {
        setSecurityStatus({ configured: false, loading: false });
      }
    };
    fetchSecurityStatus();
  }, []);

  // Initialize tab from query string (e.g. ?tab=color or ?tab=users)
  useEffect(() => {
    // if allowed tabs change ensure activeTab valid
    if (!allowedTabs.includes(activeTab)) {
      setActiveTab(allowedTabs[0] || 'users');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allowedTabs]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const tab = params.get('tab');
    if (tab && allowedTabs.includes(tab)) {
      setActiveTab(tab);
    }
  }, [location.search, allowedTabs]);

  const handleSave = async (data) => {
    try {
      setSaving(true);
      setError('');
      if (activeTab === 'brand') {
        if (editing) {
          await axios.put(`/api/brands/${editing.id}`, data);
          showToast('Marka başarıyla güncellendi.', 'success');
        } else {
          await axios.post('/api/brands', data);
          showToast('Marka başarıyla oluşturuldu.', 'success');
        }
      } else {
        if (editing) {
          await axios.put(`/api/colors/${editing.id}`, data);
          showToast('Renk başarıyla güncellendi.', 'success');
        } else {
          await axios.post('/api/colors', data);
          showToast('Renk başarıyla oluşturuldu.', 'success');
        }
      }
      setEditing(undefined);
      await load();
    } catch (e) {
      const fallback =
        activeTab === 'brand'
          ? 'Marka kaydedilirken bir hata oluştu. Lütfen bilgileri kontrol edin.'
          : 'Renk kaydedilirken bir hata oluştu. Lütfen bilgileri kontrol edin.';
      console.log(e.response)
      const finalMessage = buildErrorMessage(e.response?.data, fallback);
      setError(finalMessage);
      showToast(finalMessage, 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item) => {
    const itemType = activeTab === 'brand' ? 'Marka' : 'Renk';
    setConfirmModal({
      show: true,
      title: `${itemType} Silme`,
      message: `"${item.name}" ${itemType.toLowerCase()}ını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
      icon: 'trash',
      confirmVariant: 'danger',
      confirmText: 'Sil',
      onConfirm: async () => {
        setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        const url = activeTab === 'brand' ? `/api/brands/${item.id}` : `/api/colors/${item.id}`;
        let lastCode = '';
        let lastErrorMsg = '';
        // eslint-disable-next-line no-constant-condition
        while (true) {
          const code = await askSecurityCode({
            prefill: lastCode,
            errorMessage: lastErrorMsg || (lastCode ? 'Güvenlik şifresi hatalı, tekrar deneyin.' : ''),
            persistOnResolve: true
          });
          if (code === null) {
            closeSecurityPrompt();
            return;
          }
          lastCode = code;
          lastErrorMsg = '';

          try {
            await axios.delete(url, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
            closeSecurityPrompt();
            await load();
            showToast(
              `"${item.name}" ${itemType.toLowerCase()}ı başarıyla silindi.`,
              'success'
            );
            break;
          } catch (e) {
            const { code: errCode, msg } = parseSecurityError(e);
            lastErrorMsg = msg;
            if (!adminSecurityErrorCodes.has(errCode)) {
              closeSecurityPrompt();
              const fallbackMsg =
                `"${item.name}" ${itemType.toLowerCase()} silinirken bir hata oluştu. Bu öğe başka kayıtlar tarafından kullanılıyor olabilir.`;
              const finalMessage = buildErrorMessage(e.response?.data, fallbackMsg);
              showToast(finalMessage, 'error');
              break;
            }
            showToast(msg || 'Güvenlik şifresi hatalı', 'error');
            // security error -> retry with same modal
          }
        }
      }
    });
  };

  const handleBulkDelete = async () => {
    const isBrandTab = activeTab === 'brand';
    const ids = isBrandTab ? selectedBrandIds : selectedColorIds;
    if (!ids.length) return;
    const itemType = isBrandTab ? 'Marka' : 'Renk';

    setConfirmModal({
      show: true,
      title: `${itemType} Toplu Silme`,
      message: `Seçili ${ids.length} ${itemType.toLowerCase()} kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
      icon: 'trash',
      confirmVariant: 'danger',
      confirmText: 'Seçilenleri Sil',
      onConfirm: async () => {
        setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        const baseUrl = isBrandTab ? '/api/brands' : '/api/colors';
        let lastCode = '';
        let lastErrorMsg = '';
        // eslint-disable-next-line no-constant-condition
        while (true) {
          const code = await askSecurityCode({
            prefill: lastCode,
            errorMessage: lastErrorMsg || (lastCode ? 'Güvenlik şifresi hatalı, tekrar deneyin.' : ''),
            persistOnResolve: true
          });
          if (code === null) {
            closeSecurityPrompt();
            return;
          }
          lastCode = code;
          lastErrorMsg = '';

          try {
            await axios.delete(`${baseUrl}/bulk`, { data: ids, headers: { 'X-ADMIN-SECURITY-CODE': code } });
            closeSecurityPrompt();
            await load();
            if (isBrandTab) {
              setSelectedBrandIds([]);
            } else {
              setSelectedColorIds([]);
            }
            const successMsg = isBrandTab
              ? `Seçili ${ids.length} marka başarıyla silindi.`
              : `Seçili ${ids.length} renk başarıyla silindi.`;
            showToast(successMsg, 'success');
            break;
          } catch (e) {
            const { code: errCode, msg } = parseSecurityError(e);
            lastErrorMsg = msg;
            if (!adminSecurityErrorCodes.has(errCode)) {
              closeSecurityPrompt();
              const fallbackMsg =
                'Toplu silme sırasında bir hata oluştu. Lütfen daha sonra tekrar deneyin.';
              const finalMessage = buildErrorMessage(e.response?.data, fallbackMsg);
              showToast(finalMessage, 'error');
              break;
            }
            showToast(msg || 'Güvenlik şifresi hatalı', 'error');
            // security error -> retry with same modal
          }
        }
      }
    });
  };

  const handleDeleteUser = async (user) => {
    setConfirmModal({
      show: true,
      title: 'Kullanıcı Silme',
      message: `"${user.username}" kullanıcısını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
      icon: 'trash',
      confirmVariant: 'danger',
      confirmText: 'Sil',
      onConfirm: async () => {
        setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        let lastCode = '';
        let lastErrorMsg = '';
        // eslint-disable-next-line no-constant-condition
        while (true) {
          const code = await askSecurityCode({
            prefill: lastCode,
            errorMessage: lastErrorMsg || (lastCode ? 'Güvenlik şifresi hatalı, tekrar deneyin.' : ''),
            persistOnResolve: true
          });
          if (code === null) {
            closeSecurityPrompt();
            return;
          }
          lastCode = code;
          lastErrorMsg = '';

          try {
            await axios.delete(`/api/users/${user.id}`, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
            closeSecurityPrompt();
            await load();
            showToast(`"${user.username}" kullanıcısı başarıyla silindi.`, 'success');
            break;
          } catch (e) {
            const { code: errCode, msg } = parseSecurityError(e);
            lastErrorMsg = msg;
            if (!adminSecurityErrorCodes.has(errCode)) {
              closeSecurityPrompt();
              const fallbackMsg =
                `"${user.username}" kullanıcısı silinirken bir hata oluştu. Kullanıcıya ait başka kayıtlar olabilir.`;
              const finalMessage = buildErrorMessage(e.response?.data, fallbackMsg);
              showToast(finalMessage, 'error');
              break;
            }
            // security error -> retry same modal
          }
        }
      }
    });
  };

  const handleSecurityCodeSave = async () => {
    console.log('[AdminSettings] security save start users count', users.length);
    if ((securityForm.newCode || '').length < 5) {
      showToast('Yeni şifre en az 5 karakter olmalıdır.', 'error');
      return;
    }
    if (securityForm.newCode !== securityForm.confirmNewCode) {
      showToast('Yeni şifreler eşleşmiyor.', 'error');
      return;
    }
    try {
      setSecuritySaving(true);
      await axios.put('/api/admin/security-code', securityForm);
      await refreshUsers();
      setSecurityForm({ currentCode: '', newCode: '', confirmNewCode: '' });
      setSecurityStatus((s) => ({ ...s, configured: true }));
      showToast('Güvenlik şifresi güncellendi.', 'success');
      setShowSecurityModal(false);
    } catch (e) {
      const errData = e?.response?.data;
      const code = errData?.code || errData?.errorCode;
      const rawMessage = errData?.message || errData?.error || (typeof errData === 'string' ? errData : null);
      const lower = (rawMessage || '').toLowerCase();
      let friendly = 'Güvenlik şifresi güncellenemedi.';
      if (code === 'AUTH_003' || lower.includes('mevcut güvenlik')) {
        friendly = 'Mevcut güvenlik şifresi doğru değil. Lütfen kontrol edin.';
      } else if (code === 'AUTH_002') {
        friendly = 'Güvenlik şifresi zorunlu. Lütfen mevcut şifreyi girin.';
      } else if (code === 'AUTH_006') {
        friendly = 'Mevcut güvenlik şifresi zorunludur.';
      } else if (code === 'AUTH_007') {
        friendly = 'Yeni güvenlik şifresi zorunludur.';
      } else if (lower.includes('eşleşmiyor') || lower.includes('uyuşmuyor')) {
        friendly = 'Girilen yeni şifreler uyuşmuyor.';
      } else if (lower.includes('en az 5') || code === 'AUTH_005') {
        friendly = 'Yeni şifre en az 5 karakter olmalıdır.';
      } else if (code === 'INVALID_VALUE') {
        friendly = 'Girilen değerler geçersiz. Mevcut ve yeni şifreleri kontrol edin.';
      } else if (rawMessage) {
        friendly = rawMessage;
      }
      showToast(friendly, 'error');
    } finally {
      setSecuritySaving(false);
      console.log('[AdminSettings] security save end users count', users.length);
    }
  };

  return (
    <div>
      <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-md-between mb-4 gap-2">
        <h2 className="mb-0">Yönetici Ayarları</h2>
        <ul className="nav nav-pills flex-wrap">
          {allowedTabs.includes('brand') && (
            <li className="nav-item mb-1">
              <button className={`nav-link ${activeTab === 'brand' ? 'active' : ''}`} onClick={() => setActiveTab('brand')}>Marka</button>
            </li>
          )}
          {allowedTabs.includes('color') && (
            <li className="nav-item ms-0 ms-md-2 mb-1">
              <button className={`nav-link ${activeTab === 'color' ? 'active' : ''}`} onClick={() => setActiveTab('color')}>Renk</button>
            </li>
          )}
          {allowedTabs.includes('users') && (
            <li className="nav-item ms-0 ms-md-2 mb-1">
              <button className={`nav-link ${activeTab === 'users' ? 'active' : ''}`} onClick={() => setActiveTab('users')}>Kullanıcılar</button>
            </li>
          )}
        </ul>
      </div>

      {activeTab === 'brand' && allowedTabs.includes('brand') && (
        <CrudTable
          title="Markalar"
          columns={[
            { key: 'name', title: 'Ad' },
            { key: 'description', title: 'Açıklama' },
            { key: 'active', title: 'Durum', render: (v) => v ? 'Aktif' : 'Pasif' }
          ]}
          items={brands
            .filter(b => !brandSearch || (b.name || '').toLowerCase().includes(brandSearch.toLowerCase()))
            .map(b => ({
              ...b,
              __selected: selectedBrandIds.includes(b.id),
              __onToggle: () => {
                setSelectedBrandIds(prev =>
                  prev.includes(b.id) ? prev.filter(id => id !== b.id) : [...prev, b.id]
                );
              }
            }))
          }
          loading={loading}
          onCreate={() => { setError(''); setEditing(null); }}
          onEdit={(it) => { setError(''); setEditing(it); }}
          onDelete={handleDelete}
          enableSelection
          onBulkDelete={handleBulkDelete}
          hasSelection={selectedBrandIds.length > 0}
          selectedCount={selectedBrandIds.length}
          onToggleAll={(checked, visibleItems) => {
            const visibleIds = visibleItems.map(it => it.id);
            setSelectedBrandIds(prev => {
              if (checked) {
                return Array.from(new Set([...prev, ...visibleIds]));
              }
              return prev.filter(id => !visibleIds.includes(id));
            });
          }}
          searchPlaceholder="Marka ara..."
          searchTerm={brandSearch}
          onSearchChange={setBrandSearch}
        />
      )}

      {activeTab === 'color' && allowedTabs.includes('color') && (
        <CrudTable
          title="Renkler"
          columns={[
            { key: 'name', title: 'Ad' },
            {
              key: 'hexCode',
              title: 'Renk Kodu',
              render: (v) => v ? (
                <span className="d-inline-flex align-items-center gap-2">
                  <span
                    style={{
                      display: 'inline-block',
                      width: 16,
                      height: 16,
                      borderRadius: 3,
                      backgroundColor: v,
                      border: '1px solid rgba(0,0,0,0.15)'
                    }}
                  />
                  <span>{v}</span>
                </span>
              ) : '-'
            },
            { key: 'active', title: 'Durum', render: (v) => v ? 'Aktif' : 'Pasif' }
          ]}
          items={colors
            .filter(c => !colorSearch || (c.name || '').toLowerCase().includes(colorSearch.toLowerCase()))
            .map(c => ({
              ...c,
              __selected: selectedColorIds.includes(c.id),
              __onToggle: () => {
                setSelectedColorIds(prev =>
                  prev.includes(c.id) ? prev.filter(id => id !== c.id) : [...prev, c.id]
                );
              }
            }))
          }
          loading={loading}
          onCreate={() => { setError(''); setEditing(null); }}
          onEdit={(it) => { setError(''); setEditing(it); }}
          onDelete={handleDelete}
          enableSelection
          onBulkDelete={handleBulkDelete}
          hasSelection={selectedColorIds.length > 0}
          selectedCount={selectedColorIds.length}
          onToggleAll={(checked, visibleItems) => {
            const visibleIds = visibleItems.map(it => it.id);
            setSelectedColorIds(prev => {
              if (checked) {
                return Array.from(new Set([...prev, ...visibleIds]));
              }
              return prev.filter(id => !visibleIds.includes(id));
            });
          }}
          searchPlaceholder="Renk ara..."
          searchTerm={colorSearch}
          onSearchChange={setColorSearch}
        />
      )}

      {activeTab === 'users' && allowedTabs.includes('users') && (
        <div className="card mb-4">
          <div className="card-header">
            <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-md-between gap-2">
              <h5 className="mb-0">Kullanıcılar</h5>
              <div className="d-flex flex-column flex-sm-row align-items-stretch align-items-sm-center gap-2 w-100 w-md-auto">
                <div className="flex-grow-1 position-relative">
                  <form autoComplete="off" onSubmit={(e) => e.preventDefault()}>
                    <input
                      type="text"
                      className="form-control form-control-sm ps-5"
                      placeholder="Kullanıcı ara..."
                      value={userSearch}
                      onChange={e => setUserSearch(e.target.value)}
                      autoComplete="new-password"
                      name="user-search"
                    />
                    <span
                      className="position-absolute top-50 start-0 translate-middle-y ms-3 text-muted"
                      style={{ pointerEvents: 'none' }}
                    >
                      <i className="fas fa-search"></i>
                    </span>
                  </form>
                </div>
                <button
                  className="btn btn-outline-secondary btn-sm"
                  onClick={() => {
                    setUserSearch('');
                    setShowSecurityModal(true);
                  }}
                >
                  <i className="fas fa-shield-alt me-2"></i>
                  Yönetici Güvenlik Şifresi
                </button>
                <button className="btn btn-primary btn-sm" onClick={() => { setError(''); setEditing({ __create: true }); }}>
                  <i className="fas fa-user-plus me-2"></i>Yeni Kullanıcı
                </button>
              </div>
            </div>
          </div>
          <div className="card-body p-0">
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead>
                  <tr>
                    <th>Kullanıcı Adı</th>
                    <th>Yetki</th>
                    <th style={{ width: 160 }}>İşlemler</th>
                  </tr>
                </thead>
                <tbody>
                  {loading && (
                    <tr><td colSpan={3} className="text-center py-4"><span className="spinner-border"></span></td></tr>
                  )}
                  {!loading && users.filter(u =>
                    !userSearch ||
                    (u.username || '').toLowerCase().includes(userSearch.toLowerCase())
                  ).length === 0 && (
                      <tr><td colSpan={3} className="text-center py-4 text-muted">Kayıt bulunamadı</td></tr>
                    )}
                  {!loading && users
                    .filter(u =>
                      !userSearch ||
                      (u.username || '').toLowerCase().includes(userSearch.toLowerCase())
                    )
                    .map(u => (
                      <tr key={u.id}>
                        <td>{u.username}</td>
                        <td>
                          <span className={`badge ${u.role === 'ADMIN' ? 'bg-danger' : u.role === 'STOCK_IN' ? 'bg-success' : 'bg-warning'}`}>
                            {roleLabel(u.role)}
                          </span>
                        </td>
                        <td>
                          <div className="btn-group btn-group-sm">
                            <button className="btn btn-outline-secondary" onClick={() => { setError(''); setEditing(u); }}>
                              <i className="fas fa-edit"></i>
                            </button>
                            <button className="btn btn-outline-danger" onClick={() => handleDeleteUser(u)}>
                              <i className="fas fa-trash"></i>
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {(activeTab === 'brand' || activeTab === 'color') && editing !== undefined && (
        <EditModal
          title={activeTab === 'brand' ? (editing ? 'Marka Düzenle' : 'Yeni Marka') : (editing ? 'Renk Düzenle' : 'Yeni Renk')}
          fields={activeTab === 'brand' ? ['name', 'description', 'active'] : ['name', 'hexCode', 'active']}
          item={editing || undefined}
          onClose={() => setEditing(undefined)}
          onSave={handleSave}
          saving={saving}
          error={error}
        />
      )}

      {activeTab === 'users' && editing !== undefined && (
        <UserModal
          user={editing.__create ? null : editing}
          onClose={() => setEditing(undefined)}
          saving={saving}
          error={error}
          onSave={async (form) => {
            try {
              setSaving(true);
              setError('');
              if (editing.__create) {
                // If the new user has the ADMIN role, ask for the admin security code
                let adminHeader = {};
                if (form.role === 'ADMIN') {
                  let lastCode = '';
                  let lastErrorMsg = '';
                  // eslint-disable-next-line no-constant-condition
                  while (true) {
                    const code = await askSecurityCode({
                      prefill: lastCode,
                      errorMessage: lastErrorMsg || (lastCode ? 'Güvenlik şifresi hatalı, tekrar deneyin.' : ''),
                      persistOnResolve: true
                    });
                    if (code === null) {
                      closeSecurityPrompt();
                      setSaving(false);
                      return;
                    }
                    lastCode = code;
                    lastErrorMsg = '';
                    adminHeader = { 'X-ADMIN-SECURITY-CODE': code };
                    break;
                  }
                  closeSecurityPrompt();
                }

                await axios.post('/api/users', {
                  username: form.username,
                  password: form.password,
                  role: form.role
                }, { headers: adminHeader });
                showToast('Kullanıcı başarıyla oluşturuldu.', 'success');
              } else {
                if (form.role && form.role !== editing.role) {
                  // If the role change is to ADMIN, ask for the security code
                  let adminHeader = {};
                  if (form.role === 'ADMIN') {
                    let lastCode = '';
                    let lastErrorMsg = '';
                    // eslint-disable-next-line no-constant-condition
                    while (true) {
                      const code = await askSecurityCode({
                        prefill: lastCode,
                        errorMessage: lastErrorMsg || (lastCode ? 'Güvenlik şifresi hatalı, tekrar deneyin.' : ''),
                        persistOnResolve: true
                      });
                      if (code === null) {
                        closeSecurityPrompt();
                        setSaving(false);
                        return;
                      }
                      lastCode = code;
                      lastErrorMsg = '';
                      adminHeader = { 'X-ADMIN-SECURITY-CODE': code };
                      break;
                    }
                    closeSecurityPrompt();
                  }
                  await axios.put(`/api/users/${editing.id}/role`, { role: form.role }, { headers: adminHeader });
                }
                if (form.password) {
                  await axios.put(`/api/users/${editing.id}/password`, {
                    password: form.password
                  });
                }
                showToast('Kullanıcı bilgileri başarıyla güncellendi.', 'success');
              }
              setEditing(undefined);
              await load();
            } catch (e) {
              if (e?.message === 'ADMIN_SECURITY_CANCELLED') {
                setSaving(false);
                return;
              }
              const fallbackMsg =
                'Kullanıcı kaydedilirken bir hata oluştu. Lütfen girdiğiniz bilgileri kontrol edin.';
              const finalMessage = buildErrorMessage(e.response?.data, fallbackMsg);
              setError(finalMessage);
              showToast(finalMessage, 'error');
            } finally {
              setSaving(false);
            }
          }}
        />
      )}

      {/* Confirm Modal */}
      <ConfirmModal
        show={confirmModal.show}
        title={confirmModal.title}
        message={confirmModal.message}
        icon={confirmModal.icon}
        confirmVariant={confirmModal.confirmVariant}
        confirmText={confirmModal.confirmText}
        onConfirm={confirmModal.onConfirm}
        onCancel={() => setConfirmModal({ show: false, title: '', message: '', onConfirm: null })}
      />

      {showSecurityModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 4000 }}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Yönetici Güvenlik Şifresi</h5>
                <button type="button" className="btn-close" onClick={() => setShowSecurityModal(false)}></button>
              </div>
              <div className="modal-body">
                <p className="text-muted mb-3">
                  Silme işlemleri için yönetici güvenlik şifresi gereklidir. Değiştirmek için mevcut şifreyi doğrulayın.
                </p>
                <div className="mb-3">
                  <label className="form-label">Mevcut Şifre</label>
                  <form autoComplete="off" onSubmit={(e) => e.preventDefault()}>
                    <div className="input-group">
                      <input
                        type={showSecurityPassword.current ? 'text' : 'password'}
                        className="form-control"
                        value={securityForm.currentCode}
                        onChange={(e) => setSecurityForm({ ...securityForm, currentCode: e.target.value })}
                        placeholder={securityStatus.configured ? 'Mevcut şifreyi girin' : 'İlk kez şifre tanımlayın'}
                        disabled={securitySaving}
                        autoComplete="new-password"
                        name="current-security-code"
                      />
                      <button
                        type="button"
                        className="btn btn-outline-secondary"
                        onClick={() => setShowSecurityPassword(prev => ({ ...prev, current: !prev.current }))}
                      >
                        <i className={`fas fa-eye${showSecurityPassword.current ? '-slash' : ''}`}></i>
                      </button>
                    </div>
                  </form>
                </div>
                <div className="mb-3">
                  <label className="form-label">Yeni Şifre</label>
                  <form autoComplete="off" onSubmit={(e) => e.preventDefault()}>
                    <div className="input-group">
                      <input
                        type={showSecurityPassword.new ? 'text' : 'password'}
                        className="form-control"
                        value={securityForm.newCode}
                        onChange={(e) => setSecurityForm({ ...securityForm, newCode: e.target.value })}
                        disabled={securitySaving}
                        autoComplete="new-password"
                        name="new-security-code"
                      />
                      <button
                        type="button"
                        className="btn btn-outline-secondary"
                        onClick={() => setShowSecurityPassword(prev => ({ ...prev, new: !prev.new }))}
                      >
                        <i className={`fas fa-eye${showSecurityPassword.new ? '-slash' : ''}`}></i>
                      </button>
                    </div>
                  </form>
                </div>
                <div className="mb-3">
                  <label className="form-label">Yeni Şifre (Tekrar)</label>
                  <form autoComplete="off" onSubmit={(e) => e.preventDefault()}>
                    <div className="input-group">
                      <input
                        type={showSecurityPassword.confirm ? 'text' : 'password'}
                        className="form-control"
                        value={securityForm.confirmNewCode}
                        onChange={(e) => setSecurityForm({ ...securityForm, confirmNewCode: e.target.value })}
                        disabled={securitySaving}
                        autoComplete="new-password"
                        name="confirm-security-code"
                      />
                      <button
                        type="button"
                        className="btn btn-outline-secondary"
                        onClick={() => setShowSecurityPassword(prev => ({ ...prev, confirm: !prev.confirm }))}
                      >
                        <i className={`fas fa-eye${showSecurityPassword.confirm ? '-slash' : ''}`}></i>
                      </button>
                    </div>
                  </form>
                </div>
                <span className={`badge ${securityStatus.configured ? 'bg-success' : 'bg-secondary'}`}>
                  {securityStatus.loading ? 'Durum kontrol ediliyor...' : securityStatus.configured ? 'Şifre tanımlı' : 'Şifre henüz tanımlı değil'}
                </span>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowSecurityModal(false)} disabled={securitySaving}>
                  Kapat
                </button>
                <button className="btn btn-primary" onClick={handleSecurityCodeSave} disabled={securitySaving}>
                  {securitySaving ? <span className="spinner-border spinner-border-sm" /> : 'Şifreyi Güncelle'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
      {SecurityCodePrompt}
    </div>
  );
};

export default AdminSettings;
