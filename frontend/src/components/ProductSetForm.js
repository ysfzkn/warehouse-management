import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import BundleMemberPicker from './BundleMemberPicker';

/**
 * Focused editor for a product set (bundle).
 *
 * Unlike the full ProductForm, this shows ONLY what a set needs: a name, the
 * member products (picked from existing products), the auto-computed members
 * total, the set's own selling price and optional discounted price, a category,
 * and images. All "new product detail" fields (SKU, VAT, description, SEO,
 * dimensions, campaign dates) live behind a collapsible "Detaylı Bilgiler"
 * section so the common case — bundling existing products — stays simple.
 */
const NAME_PRESETS = [
  'Çeyiz Seti',
  'Ankastre Seti',
  'Mutfak Seti',
  'Banyo Seti',
  'Beyaz Eşya Seti',
  'Kampanya Seti',
];

const fmt = (v) =>
  new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(Number(v) || 0);

const effective = (m) =>
  m.salePrice && Number(m.salePrice) > 0 ? Number(m.salePrice) : Number(m.price) || 0;

const mapMember = (b) => ({
  productId: b.productId,
  name: b.name,
  sku: b.sku,
  price: b.price,
  salePrice: b.salePrice,
  quantity: b.quantity || 1,
  isGift: !!b.isGift,
});

/** Accept either a plain array or a paginated { content: [...] } response. */
const pickList = (data) => (Array.isArray(data?.content) ? data.content : Array.isArray(data) ? data : []);

export default function ProductSetForm({ product, onSuccess, onCancel }) {
  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [salePrice, setSalePrice] = useState('');
  const [vatRate, setVatRate] = useState('20');
  const [categoryId, setCategoryId] = useState('');
  const [subcategoryId, setSubcategoryId] = useState('');
  const [creatingCat, setCreatingCat] = useState(false);
  const [newCatName, setNewCatName] = useState('');
  const [creatingSub, setCreatingSub] = useState(false);
  const [newSubName, setNewSubName] = useState('');
  const [description, setDescription] = useState('');
  const [shortDescription, setShortDescription] = useState('');
  const [saleStart, setSaleStart] = useState('');
  const [saleEnd, setSaleEnd] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [members, setMembers] = useState([]);

  const [mainCategories, setMainCategories] = useState([]);
  const [subcategories, setSubcategories] = useState([]);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const [savedId, setSavedId] = useState(product?.id || null);
  const [images, setImages] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [dragIndex, setDragIndex] = useState(null);
  const [dragOverIndex, setDragOverIndex] = useState(null);

  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [flash, setFlash] = useState(null);

  const editId = product?.id || savedId;

  const showFlash = (message, variant = 'success') => {
    setFlash({ message, variant });
    setTimeout(() => setFlash(null), 3000);
  };

  // ---- Load categories ----
  const fetchMainCategories = useCallback(() => {
    return axios
      .get('/api/categories/top-level')
      .then((r) => {
        const list = pickList(r.data);
        setMainCategories(list);
        return list;
      })
      .catch(() => {
        setMainCategories([]);
        return [];
      });
  }, []);

  useEffect(() => {
    fetchMainCategories();
  }, [fetchMainCategories]);

  const loadSubcategories = useCallback((parentId) => {
    if (!parentId) {
      setSubcategories([]);
      return Promise.resolve([]);
    }
    return axios
      .get(`/api/categories/${parentId}/subcategories`)
      .then((r) => {
        const list = pickList(r.data);
        setSubcategories(list);
        return list;
      })
      .catch(() => {
        setSubcategories([]);
        return [];
      });
  }, []);

  const createCategory = async () => {
    const nm = newCatName.trim();
    if (!nm) return;
    // eslint-disable-next-line no-alert
    if (!window.confirm(`"${nm}" adında yeni bir kategori oluşturulsun mu?`)) return;
    try {
      const r = await axios.post('/api/categories', { name: nm, description: null });
      await fetchMainCategories();
      setCategoryId(String(r.data.id));
      setSubcategoryId('');
      setSubcategories([]);
      setCreatingCat(false);
      setNewCatName('');
      showFlash('Kategori oluşturuldu.');
    } catch (e) {
      showFlash(e?.response?.data?.message || 'Kategori oluşturulamadı.', 'danger');
    }
  };

  const createSubcategory = async () => {
    const nm = newSubName.trim();
    if (!nm || !categoryId) return;
    try {
      const r = await axios.post('/api/categories/batch', [{ name: nm, description: null }], {
        params: { parentId: categoryId },
      });
      const created = Array.isArray(r.data) ? r.data[0] : null;
      await loadSubcategories(categoryId);
      if (created?.id) setSubcategoryId(String(created.id));
      setCreatingSub(false);
      setNewSubName('');
      showFlash('Alt kategori oluşturuldu.');
    } catch (e) {
      showFlash(e?.response?.data?.message || 'Alt kategori oluşturulamadı.', 'danger');
    }
  };

  const loadImages = useCallback((id) => {
    if (!id) return;
    axios
      .get(`/api/products/${id}/images`)
      .then((r) => setImages(r.data || []))
      .catch(() => setImages([]));
  }, []);

  // ---- Load existing set on edit ----
  useEffect(() => {
    if (!product?.id) return;
    setName(product.name || '');
    setSku(product.sku || '');
    setSalePrice(product.salePrice != null ? String(product.salePrice) : '');
    setVatRate(product.vatRate != null ? String(product.vatRate) : '20');
    setIsActive(product.active !== false);
    setDescription(product.description || '');
    setShortDescription(product.shortDescription || '');
    setSaleStart(product.saleStart ? String(product.saleStart).substring(0, 16) : '');
    setSaleEnd(product.saleEnd ? String(product.saleEnd).substring(0, 16) : '');
    const mainId = product.categoryParentId || product.categoryId;
    const subId = product.categoryParentId ? product.categoryId : null;
    setCategoryId(mainId != null ? String(mainId) : '');
    setSubcategoryId(subId != null ? String(subId) : '');
    if (mainId) loadSubcategories(mainId);
    loadImages(product.id);
    // Members: use embedded bundleItems or fetch detail
    if (Array.isArray(product.bundleItems) && product.bundleItems.length > 0) {
      setMembers(product.bundleItems.map(mapMember));
    } else {
      axios
        .get(`/api/products/${product.id}`)
        .then((r) => setMembers((r.data?.bundleItems || []).map(mapMember)))
        .catch(() => {});
    }
  }, [product, loadSubcategories, loadImages]);

  // Set base price is ALWAYS the sum of member prices (auto, read-only). The admin only
  // optionally enters a discounted (campaign) price.
  // Gift members are free → excluded from the set's price total.
  const membersTotal = members.reduce((sum, m) => sum + (m.isGift ? 0 : effective(m) * (m.quantity || 1)), 0);
  const hasSale = salePrice !== '' && Number(salePrice) > 0;
  const savings = hasSale && membersTotal > 0 ? membersTotal - Number(salePrice) : 0;

  const handleSubmit = async () => {
    const errs = {};
    if (!name.trim()) errs.name = 'Set adı gereklidir.';
    if (members.length === 0) errs.members = 'En az bir üye ürün ekleyin.';
    if (!(membersTotal > 0)) errs.members = 'Üye ürünlerin fiyatları toplamı sıfır olamaz.';
    if (!categoryId) errs.category = 'Kategori seçin.';
    if (hasSale && Number(salePrice) >= membersTotal)
      errs.salePrice = 'İndirimli fiyat, set fiyatından düşük olmalıdır.';
    setErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setLoading(true);
    try {
      // Keep a valid existing SKU; if missing or out of the 3..50 range auto-generate one,
      // so the set always satisfies the backend's @Size(3..50) rule.
      const trimmedSku = (sku || '').trim();
      const validSku = trimmedSku.length >= 3 && trimmedSku.length <= 50;
      const finalSku = (validSku ? trimmedSku : `SET-${Date.now().toString(36)}`).toUpperCase();
      const data = {
        name: name.trim(),
        sku: finalSku,
        price: Number(membersTotal.toFixed(2)),
        salePrice: hasSale ? parseFloat(salePrice) : null,
        saleStart: saleStart ? saleStart + ':00' : null,
        saleEnd: saleEnd ? saleEnd + ':00' : null,
        vatRate: vatRate ? parseFloat(vatRate) : 20,
        description: description || null,
        shortDescription: shortDescription || null,
        category: { id: parseInt(subcategoryId || categoryId, 10) },
        isActive,
        productType: 'BUNDLE',
        bundleMemberRefs: members.map((m, i) => ({
          productId: m.productId,
          quantity: Math.max(1, m.quantity || 1),
          sortOrder: i,
          isGift: !!m.isGift,
        })),
      };

      if (editId) {
        await axios.put(`/api/products/${editId}`, data);
        showFlash('Set güncellendi.');
        if (onSuccess) onSuccess();
      } else {
        const resp = await axios.post('/api/products', data);
        const newId = resp.data?.id;
        setSavedId(newId);
        setSku(finalSku);
        showFlash('Set oluşturuldu. Şimdi görsel ekleyebilirsiniz.');
        loadImages(newId);
      }
    } catch (e) {
      setErrors({ general: e?.response?.data?.message || 'Set kaydedilemedi.' });
    } finally {
      setLoading(false);
    }
  };

  // ---- Images ----
  const handleUpload = async (e) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0 || !editId) return;
    setUploading(true);
    try {
      const galleryCount = images.filter((img) => !img.slot).length;
      for (let i = 0; i < files.length; i += 1) {
        const fd = new FormData();
        fd.append('file', files[i]);
        if (galleryCount === 0 && i === 0) fd.append('primary', 'true');
        // eslint-disable-next-line no-await-in-loop
        await axios.post(`/api/products/${editId}/images`, fd, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
      }
      loadImages(editId);
    } catch {
      showFlash('Görsel yüklenemedi.', 'danger');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  // Add one or more showcase ("set vitrin") images. Each file is uploaded into the
  // next free slot number, so a set can have any number of showcase images.
  const handleAddShowcaseImages = async (e) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0 || !editId) return;
    setUploading(true);
    try {
      let nextSlot = images.filter((i) => i.slot).reduce((max, i) => Math.max(max, i.slot), 0) + 1;
      for (let i = 0; i < files.length; i += 1) {
        const fd = new FormData();
        fd.append('file', files[i]);
        // eslint-disable-next-line no-await-in-loop
        await axios.post(`/api/products/${editId}/images?slot=${nextSlot}`, fd, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        nextSlot += 1;
      }
      loadImages(editId);
    } catch {
      showFlash('Görsel yüklenemedi.', 'danger');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  const setPrimary = async (imageId) => {
    try {
      await axios.put(`/api/products/images/${imageId}/set-primary`);
      loadImages(editId);
    } catch {
      showFlash('İşlem başarısız.', 'danger');
    }
  };

  const deleteImage = async (imageId) => {
    try {
      await axios.delete(`/api/products/images/${imageId}`);
      loadImages(editId);
    } catch {
      showFlash('Görsel silinemedi.', 'danger');
    }
  };

  // ---- Drag-and-drop image ordering (gallery images only; slot images are fixed) ----
  const persistImageOrder = async (orderedGallery) => {
    const slotImgs = images.filter((i) => i.slot);
    setImages([...orderedGallery, ...slotImgs]);
    if (!editId) return;
    try {
      await axios.put(
        `/api/products/${editId}/images/reorder`,
        orderedGallery.map((i) => i.id)
      );
    } catch {
      showFlash('Sıralama kaydedilemedi, lütfen tekrar deneyin.', 'danger');
      loadImages(editId);
    }
  };

  const handleImageDrop = (targetIndex) => {
    if (dragIndex === null || dragIndex === targetIndex) {
      setDragIndex(null);
      return;
    }
    const sorted = images.filter((i) => !i.slot).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
    const [moved] = sorted.splice(dragIndex, 1);
    sorted.splice(targetIndex, 0, moved);
    const reindexed = sorted.map((img, i) => ({ ...img, sortOrder: i }));
    setDragIndex(null);
    persistImageOrder(reindexed);
  };

  return (
    <div>
      {errors.general && <div className="alert alert-danger">{errors.general}</div>}

      {/* Set name + presets */}
      <div className="mb-3">
        <label className="form-label fw-semibold">
          Set Adı <span className="text-danger">*</span>
        </label>
        <input
          type="text"
          className={`form-control form-control-lg ${errors.name ? 'is-invalid' : ''}`}
          placeholder="ör: Ankastre Set, Çeyiz Seti"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        {errors.name && <div className="invalid-feedback">{errors.name}</div>}
        <div className="d-flex flex-wrap gap-1 mt-2">
          {NAME_PRESETS.map((p) => (
            <button
              key={p}
              type="button"
              className="btn btn-sm btn-outline-secondary"
              onClick={() => setName(p)}
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      {/* Members */}
      <BundleMemberPicker members={members} onChange={setMembers} excludeProductId={editId} />
      {errors.members && <div className="text-danger small mb-2">{errors.members}</div>}

      {/* Pricing */}
      <div className="card border-0 shadow-sm mb-3" style={{ borderRadius: 12 }}>
        <div className="card-body">
          <h6 className="fw-bold mb-3">
            <i className="fas fa-tag me-2 text-success" />
            Fiyatlandırma
          </h6>
          <div className="row g-3">
            <div className="col-md-6 d-flex flex-column">
              <label className="form-label d-flex align-items-center gap-2 mb-1" style={{ minHeight: 24 }}>
                Set Fiyatı
                <span className="badge bg-secondary-subtle text-secondary">otomatik</span>
              </label>
              <div className="input-group">
                <span className="input-group-text bg-light">₺</span>
                <input
                  type="text"
                  className="form-control form-control-lg fw-bold text-dark bg-light"
                  value={fmt(membersTotal).replace('₺', '').trim()}
                  readOnly
                  disabled
                />
              </div>
              <small className="text-muted mt-1">
                Üye ürünler değiştikçe otomatik güncellenir; elle değiştirilemez.
              </small>
            </div>
            <div className="col-md-6 d-flex flex-column">
              <label className="form-label mb-1" style={{ minHeight: 24 }}>
                İndirimli Fiyat (₺) <span className="text-muted small">— opsiyonel / kampanya</span>
              </label>
              <input
                type="number"
                step="0.01"
                min="0"
                className={`form-control form-control-lg ${errors.salePrice ? 'is-invalid' : ''}`}
                value={salePrice}
                onChange={(e) => setSalePrice(e.target.value)}
                placeholder="İndirim uygulamak için girin"
              />
              {errors.salePrice ? (
                <div className="invalid-feedback d-block">{errors.salePrice}</div>
              ) : (
                <small className="text-muted mt-1">Boş bırakılırsa indirim uygulanmaz.</small>
              )}
            </div>
          </div>
          {savings > 0 && (
            <div className="mt-3">
              <span className="badge bg-success px-2 py-1">
                Müşteri {fmt(savings)} tasarruf ediyor — {fmt(membersTotal)} yerine {fmt(salePrice)}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Category + active */}
      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <div className="d-flex justify-content-between align-items-center">
            <label className="form-label mb-1">
              Kategori <span className="text-danger">*</span>
            </label>
            <button
              type="button"
              className="btn btn-sm btn-link text-decoration-none p-0"
              onClick={() => {
                if (!creatingCat) setNewCatName((prev) => prev || name.trim());
                setCreatingCat((v) => !v);
              }}
            >
              <i className="fas fa-plus me-1" />
              Yeni Kategori
            </button>
          </div>
          {creatingCat ? (
            <div className="input-group">
              <input
                type="text"
                className="form-control"
                placeholder="Yeni kategori adı"
                value={newCatName}
                onChange={(e) => setNewCatName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && createCategory()}
              />
              <button type="button" className="btn btn-success" onClick={createCategory}>
                Ekle
              </button>
              <button
                type="button"
                className="btn btn-outline-secondary"
                onClick={() => setCreatingCat(false)}
              >
                İptal
              </button>
            </div>
          ) : (
            <select
              className={`form-select ${errors.category ? 'is-invalid' : ''}`}
              value={categoryId}
              onChange={(e) => {
                setCategoryId(e.target.value);
                setSubcategoryId('');
                loadSubcategories(e.target.value);
              }}
            >
              <option value="">Seçiniz...</option>
              {mainCategories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          )}
          {errors.category && !creatingCat && <div className="text-danger small mt-1">{errors.category}</div>}
        </div>
        <div className="col-md-6">
          <div className="d-flex justify-content-between align-items-center">
            <label className="form-label mb-1">Alt Kategori (opsiyonel)</label>
            <button
              type="button"
              className="btn btn-sm btn-link text-decoration-none p-0"
              disabled={!categoryId}
              onClick={() => setCreatingSub((v) => !v)}
            >
              <i className="fas fa-plus me-1" />
              Yeni Alt Kategori
            </button>
          </div>
          {creatingSub ? (
            <div className="input-group">
              <input
                type="text"
                className="form-control"
                placeholder="Yeni alt kategori adı"
                value={newSubName}
                onChange={(e) => setNewSubName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && createSubcategory()}
              />
              <button type="button" className="btn btn-success" onClick={createSubcategory}>
                Ekle
              </button>
              <button
                type="button"
                className="btn btn-outline-secondary"
                onClick={() => setCreatingSub(false)}
              >
                İptal
              </button>
            </div>
          ) : (
            <select
              className="form-select"
              value={subcategoryId}
              onChange={(e) => setSubcategoryId(e.target.value)}
              disabled={subcategories.length === 0}
            >
              <option value="">{subcategories.length === 0 ? 'Alt kategori yok' : 'Yok'}</option>
              {subcategories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      <div className="form-check form-switch mb-3">
        <input
          className="form-check-input"
          type="checkbox"
          id="setActive"
          checked={isActive}
          onChange={(e) => setIsActive(e.target.checked)}
        />
        <label className="form-check-label" htmlFor="setActive">
          Mağazada aktif (satışta)
        </label>
      </div>

      {/* Images — only after the set exists */}
      <div className="card border-0 shadow-sm mb-3" style={{ borderRadius: 12 }}>
        <div className="card-body">
          <h6 className="fw-bold mb-3">
            <i className="fas fa-image me-2 text-primary" />
            Set Görselleri
          </h6>
          {!editId ? (
            <p className="text-muted small mb-0">Görsel eklemek için önce seti kaydedin.</p>
          ) : (
            <>
              <input
                type="file"
                accept="image/*"
                multiple
                className="form-control mb-3"
                onChange={handleUpload}
                disabled={uploading}
              />
              {uploading && (
                <div className="text-muted small mb-2">
                  <span className="spinner-border spinner-border-sm me-2" />
                  Yükleniyor...
                </div>
              )}
              {images.filter((i) => !i.slot).length > 1 && (
                <p className="text-muted small mb-2">
                  <i className="fas fa-arrows-alt me-1" />
                  Sıralamak için görselleri sürükleyip bırakın.
                </p>
              )}
              <div className="d-flex flex-wrap gap-2">
                {images
                  .filter((i) => !i.slot)
                  .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
                  .map((img, index) => (
                    <div
                      key={img.id}
                      draggable
                      onDragStart={() => setDragIndex(index)}
                      onDragOver={(e) => {
                        e.preventDefault();
                        if (dragIndex !== null && dragOverIndex !== index) setDragOverIndex(index);
                      }}
                      onDragLeave={() => setDragOverIndex((cur) => (cur === index ? null : cur))}
                      onDrop={() => {
                        handleImageDrop(index);
                        setDragOverIndex(null);
                      }}
                      onDragEnd={() => {
                        setDragIndex(null);
                        setDragOverIndex(null);
                      }}
                      className={`position-relative border rounded ${dragIndex === index ? 'opacity-50' : ''}`}
                      style={{
                        width: 90,
                        height: 90,
                        overflow: 'hidden',
                        cursor: 'grab',
                        ...(dragIndex !== null && dragOverIndex === index && dragIndex !== index
                          ? { outline: '2px dashed var(--store-primary, #2563eb)', outlineOffset: 2 }
                          : {}),
                      }}
                    >
                      <img
                        src={`/api/admin/products/images/${img.id}/view?thumbnail=true`}
                        alt=""
                        draggable={false}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                      {img.primary && (
                        <span
                          className="badge bg-success position-absolute top-0 start-0"
                          style={{ fontSize: 8 }}
                        >
                          Birincil
                        </span>
                      )}
                      <div
                        className="position-absolute bottom-0 start-0 end-0 d-flex justify-content-between"
                        style={{ background: 'rgba(0,0,0,0.55)' }}
                      >
                        {!img.primary && (
                          <button
                            type="button"
                            className="btn btn-sm text-white p-0 px-1"
                            title="Birincil yap"
                            onClick={() => setPrimary(img.id)}
                          >
                            <i className="fas fa-star" style={{ fontSize: 11 }} />
                          </button>
                        )}
                        <button
                          type="button"
                          className="btn btn-sm text-white p-0 px-1 ms-auto"
                          title="Sil"
                          onClick={() => deleteImage(img.id)}
                        >
                          <i className="fas fa-trash" style={{ fontSize: 11 }} />
                        </button>
                      </div>
                    </div>
                  ))}
              </div>
            </>
          )}
        </div>
      </div>

      {/* Set showcase images — any number of featured images shown on the storefront */}
      {editId && (
        <div className="card border-0 shadow-sm mb-3" style={{ borderRadius: 12 }}>
          <div className="card-body">
            <h6 className="fw-bold mb-1">
              <i className="fas fa-th-large me-2 text-primary" />
              Set Vitrin Görselleri
            </h6>
            <p className="text-muted small mb-3">
              Set sayfasında öne çıkan görseller. İstediğin kadar ekleyebilirsin; normal galeriden ayrıdır.
            </p>
            <div className="row g-2">
              {images
                .filter((i) => i.slot)
                .sort((a, b) => a.slot - b.slot)
                .map((slotImg, idx) => (
                  <div key={slotImg.id} className="col-4 col-md-3">
                    <div
                      className="position-relative border rounded d-flex align-items-center justify-content-center"
                      style={{ aspectRatio: '1 / 1', overflow: 'hidden', background: '#f8f9fa' }}
                    >
                      <img
                        src={`/api/admin/products/images/${slotImg.id}/view?thumbnail=true`}
                        alt={`Vitrin ${idx + 1}`}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                      <span
                        className="badge bg-dark position-absolute top-0 start-0 m-1"
                        style={{ fontSize: 9 }}
                      >
                        {idx + 1}
                      </span>
                      <button
                        type="button"
                        className="btn btn-sm btn-danger position-absolute top-0 end-0 m-1 p-0 px-1"
                        title="Sil"
                        onClick={() => deleteImage(slotImg.id)}
                        disabled={uploading}
                      >
                        <i className="fas fa-trash" style={{ fontSize: 10 }} />
                      </button>
                    </div>
                  </div>
                ))}
              {/* Add tile */}
              <div className="col-4 col-md-3">
                <label
                  className="position-relative border rounded border-2 border-dashed d-flex flex-column align-items-center justify-content-center text-center text-muted small m-0 w-100"
                  style={{
                    aspectRatio: '1 / 1',
                    cursor: uploading ? 'not-allowed' : 'pointer',
                    background: '#fff',
                  }}
                >
                  {uploading ? (
                    <span className="spinner-border spinner-border-sm" />
                  ) : (
                    <>
                      <i className="fas fa-plus mb-1" />
                      Görsel Ekle
                    </>
                  )}
                  <input
                    type="file"
                    accept="image/*"
                    multiple
                    className="d-none"
                    onChange={handleAddShowcaseImages}
                    disabled={uploading}
                  />
                </label>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Advanced (hidden by default) */}
      <button
        type="button"
        className="btn btn-link text-decoration-none px-0 mb-2"
        onClick={() => setShowAdvanced((v) => !v)}
      >
        <i className={`fas fa-chevron-${showAdvanced ? 'up' : 'down'} me-2`} />
        Detaylı Bilgiler {showAdvanced ? '(gizle)' : '(SKU, KDV, açıklama, kampanya tarihleri)'}
      </button>
      {showAdvanced && (
        <div className="card border-0 bg-light mb-3" style={{ borderRadius: 12 }}>
          <div className="card-body">
            <div className="row g-3">
              <div className="col-md-6">
                <label className="form-label">Stok Kodu (SKU)</label>
                <input
                  type="text"
                  className="form-control"
                  value={sku}
                  onChange={(e) => setSku(e.target.value)}
                  placeholder="Boş bırakılırsa otomatik üretilir"
                />
              </div>
              <div className="col-md-6">
                <label className="form-label">KDV Oranı (%)</label>
                <input
                  type="number"
                  step="0.01"
                  className="form-control"
                  value={vatRate}
                  onChange={(e) => setVatRate(e.target.value)}
                />
              </div>
              <div className="col-md-6">
                <label className="form-label">Kampanya Başlangıç</label>
                <input
                  type="datetime-local"
                  className="form-control"
                  value={saleStart}
                  onChange={(e) => setSaleStart(e.target.value)}
                />
              </div>
              <div className="col-md-6">
                <label className="form-label">Kampanya Bitiş</label>
                <input
                  type="datetime-local"
                  className="form-control"
                  value={saleEnd}
                  onChange={(e) => setSaleEnd(e.target.value)}
                />
              </div>
              <div className="col-12">
                <label className="form-label">Kısa Açıklama</label>
                <input
                  type="text"
                  className="form-control"
                  value={shortDescription}
                  onChange={(e) => setShortDescription(e.target.value)}
                  placeholder="Mağaza kartında ve detayda görünür"
                />
              </div>
              <div className="col-12">
                <label className="form-label">Açıklama</label>
                <textarea
                  className="form-control"
                  rows="3"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {flash && <div className={`alert alert-${flash.variant} py-2`}>{flash.message}</div>}

      {/* Actions */}
      <div className="d-flex justify-content-end gap-2">
        <button
          type="button"
          className="btn btn-secondary"
          onClick={editId && !product?.id ? onSuccess : onCancel}
          disabled={loading}
        >
          {editId && !product?.id ? 'Bitir' : 'İptal'}
        </button>
        <button type="button" className="btn btn-primary" onClick={handleSubmit} disabled={loading}>
          {loading ? (
            <>
              <span className="spinner-border spinner-border-sm me-2" />
              Kaydediliyor...
            </>
          ) : editId ? (
            'Güncelle'
          ) : (
            'Oluştur'
          )}
        </button>
      </div>
    </div>
  );
}
