import { useEffect, useState, useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';
import ConfirmModal from '../components/ConfirmModal';

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
  onToggleAll,
  searchPlaceholder = 'Ara...',
  searchTerm,
  onSearchChange
}) => (
  <div className="card mb-4">
    <div className="card-header">
      <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-md-between gap-2">
        <h5 className="mb-0">{title}</h5>
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
              Seçilenleri Sil
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
  const [form, setForm] = useState({ username: '', password: '', role: 'STOCK_IN' });

  useEffect(() => {
    if (user) {
      setForm({ username: user.username || '', password: '', role: user.role || 'STOCK_IN' });
    } else {
      setForm({ username: '', password: '', role: 'STOCK_IN' });
    }
  }, [user]);

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">{user ? 'Kullanıcı Düzenle' : 'Yeni Kullanıcı'}</h5>
            <button type="button" className="btn-close" onClick={onClose}></button>
          </div>
          <div className="modal-body">
            {error && <div className="alert alert-danger" dangerouslySetInnerHTML={{ __html: error }} />}
            <div className="mb-3">
              <label className="form-label">Kullanıcı Adı</label>
              <input className="form-control" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} disabled={!!user} />
            </div>
            <div className="mb-3">
              <label className="form-label">Parola {user && <small className="text-muted">(değiştirmek istemiyorsanız boş bırakın)</small>}</label>
              <input type="password" className="form-control" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} />
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
            <button className="btn btn-primary" onClick={() => onSave(form)} disabled={saving}>
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
  const allowedTabs = useMemo(
    () => (Array.isArray(allowedTabsProp) && allowedTabsProp.length ? allowedTabsProp : ['brand', 'color', 'users']),
    [allowedTabsProp]
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
        <button type="button" class="btn-close ${
          type === 'success' ? 'btn-close-white' : ''
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
          } catch {}
        }, 300);
      } catch {}
    }, type === 'success' ? 4000 : 7000);
  };

  // Hata mesajını backend'den detaylarıyla birlikte güvenli şekilde çıkarmak için helper
  const buildErrorMessage = (rawError, fallbackMessage) => {
    console.log(rawError)
    if (!rawError) {
      return fallbackMessage;
    }

    let apiError = rawError;

    // Eğer string JSON geldiyse parse etmeye çalış
    if (typeof apiError === 'string') {
      try {
        apiError = JSON.parse(apiError);
      } catch {
        // Parselenemiyorsa direkt string olarak kullan
        return apiError || fallbackMessage;
      }
    }

    // Buradan sonrası object varsayımı
    const backendMessage =
      apiError && typeof apiError.message === 'string'
        ? apiError.message.trim()
        : null;

    // details hem array hem de string olabilsin
    let details = [];
    if (Array.isArray(apiError?.details)) {
      details = apiError.details;
    } else if (typeof apiError?.details === 'string') {
      details = apiError.details.split('\n').map(d => d.trim()).filter(Boolean);
    }

    // Temel mesaj
    const base = backendMessage || fallbackMessage;

    if (!details.length) {
      return base;
    }

    // Detayları HTML olarak listele
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

  const load = async () => {
    try {
      setLoading(true);
      const [b, c, u] = await Promise.all([
        axios.get('/api/brands').catch(() => ({ data: [] })),
        axios.get('/api/colors').catch(() => ({ data: [] })),
        axios.get('/api/users').catch(() => ({ data: [] }))
      ]);
      setBrands(b.data || []);
      setColors(c.data || []);
      setUsers(u.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  // Initialize tab from query string (e.g. ?tab=color or ?tab=users)
  useEffect(() => {
    // if allowed tabs change ensure activeTab valid
    if (!allowedTabs.includes(activeTab)) {
      setActiveTab(allowedTabs[0] || 'users');
    }
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
        try {
          await axios.delete(url);
             load();
          showToast(
            `"${item.name}" ${itemType.toLowerCase()}ı başarıyla silindi.`,
            'success'
          );
        } catch (e) {
          const fallbackMsg =
            `"${item.name}" ${itemType.toLowerCase()} silinirken bir hata oluştu. Bu öğe başka kayıtlar tarafından kullanılıyor olabilir.`;
          console.log(e.response)
          const finalMessage = buildErrorMessage(e.response?.data, fallbackMsg);
          showToast(finalMessage, 'error');
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
        try {
          const baseUrl = isBrandTab ? '/api/brands' : '/api/colors';
          await axios.delete(`${baseUrl}/bulk`, { data: ids });
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
        } catch (e) {
          const fallbackMsg =
            'Toplu silme sırasında bir hata oluştu. Lütfen daha sonra tekrar deneyin.';
          console.log(e.response)
          const finalMessage = buildErrorMessage(e.response?.data, fallbackMsg);
          showToast(finalMessage, 'error');
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
        try {
          await axios.delete(`/api/users/${user.id}`);
          await load();
          showToast(`"${user.username}" kullanıcısı başarıyla silindi.`, 'success');
        } catch (e) {
          const fallbackMsg =
            `"${user.username}" kullanıcısı silinirken bir hata oluştu. Kullanıcıya ait başka kayıtlar olabilir.`;
          const finalMessage = buildErrorMessage(e.response?.data, fallbackMsg);
          showToast(finalMessage, 'error');
        }
      }
    });
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
                  <input
                    type="text"
                    className="form-control form-control-sm ps-5"
                    placeholder="Kullanıcı ara..."
                    value={userSearch}
                    onChange={e => setUserSearch(e.target.value)}
                  />
                  <span
                    className="position-absolute top-50 start-0 translate-middle-y ms-3 text-muted"
                    style={{ pointerEvents: 'none' }}
                  >
                    <i className="fas fa-search"></i>
                  </span>
                </div>
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
                          {u.role === 'ADMIN' ? 'Yönetici' : u.role === 'STOCK_IN' ? 'Stok Giriş' : 'Stok Çıkış'}
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
                await axios.post('/api/users', {
                  username: form.username,
                  password: form.password,
                  role: form.role
                });
                showToast('Kullanıcı başarıyla oluşturuldu.', 'success');
              } else {
                if (form.role && form.role !== editing.role) {
                  await axios.put(`/api/users/${editing.id}/role`, { role: form.role });
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
    </div>
  );
};

export default AdminSettings;
