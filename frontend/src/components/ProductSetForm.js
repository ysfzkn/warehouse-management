import React, { useState, useEffect, useCallback, useRef } from 'react';
import axios from 'axios';
import BundleMemberPicker from './BundleMemberPicker';
import confirmDialog from '../utils/confirmDialog';
import { toUploadableImage, reencodeImageToPng } from '../utils/imageConvert';

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

/**
 * Gallery image = displayable: not a showcase slot, not a hidden per-member
 * AI cover input (aiRole COVER_INPUT). The AI-generated cover (AI_COVER) IS a
 * normal gallery image.
 */
const isGallery = (img) => !img.slot && img.aiRole !== 'COVER_INPUT';

/**
 * Extracts the most meaningful Turkish message from an API error response:
 * field-level validation errors first, then the backend's domain message,
 * then the caller's fallback.
 */
const apiErrorMessage = (e, fallback) => {
  const data = e?.response?.data;
  if (data?.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
    return Object.values(data.fieldErrors).join(' • ');
  }
  if (data?.message) return data.message;
  if (e?.code === 'ECONNABORTED') return 'İstek zaman aşımına uğradı. Lütfen tekrar deneyin.';
  return fallback;
};

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

  // AI cover: per-member input selection + generation state
  const [coverBusy, setCoverBusy] = useState(null); // memberProductId | 'fill' | null
  const [generating, setGenerating] = useState(false);
  const [pickingFor, setPickingFor] = useState(null); // memberProductId of the open picker
  const [pickerImages, setPickerImages] = useState([]);
  const [pickerLoading, setPickerLoading] = useState(false);
  const [lightboxId, setLightboxId] = useState(null); // image id shown full-size in the lightbox

  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [flash, setFlash] = useState(null);

  const editId = product?.id || savedId;

  // Toast notification: top-right, auto-dismissing; errors stay visible longer.
  const flashTimer = useRef(null);
  const showFlash = (message, variant = 'success') => {
    setFlash({ message, variant });
    if (flashTimer.current) clearTimeout(flashTimer.current);
    const duration = variant === 'success' ? 3500 : variant === 'warning' ? 6500 : 8000;
    flashTimer.current = setTimeout(() => setFlash(null), duration);
  };
  useEffect(() => () => flashTimer.current && clearTimeout(flashTimer.current), []);

  // Close the image lightbox with Esc.
  useEffect(() => {
    if (!lightboxId) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') setLightboxId(null);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [lightboxId]);

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
    const ok = await confirmDialog({
      title: 'Yeni Kategori',
      message: `"${nm}" adında yeni bir kategori oluşturulsun mu?`,
      confirmText: 'Oluştur',
      variant: 'primary',
      icon: 'fa-folder-plus',
    });
    if (!ok) return;
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
    if (Object.keys(errs).length > 0) {
      showFlash(Object.values(errs).join(' • '), 'danger');
      return;
    }

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
      showFlash(apiErrorMessage(e, 'Set kaydedilemedi. Lütfen tekrar deneyin.'), 'danger');
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
      const galleryCount = images.filter(isGallery).length;
      for (let i = 0; i < files.length; i += 1) {
        // eslint-disable-next-line no-await-in-loop
        const uploadable = await toUploadableImage(files[i]);
        const fd = new FormData();
        fd.append('file', uploadable);
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
        // eslint-disable-next-line no-await-in-loop
        const uploadable = await toUploadableImage(files[i]);
        const fd = new FormData();
        fd.append('file', uploadable);
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

  // ---- AI cover: per-member input photos + generation ----
  const coverInputs = images.filter((i) => i.aiRole === 'COVER_INPUT');
  const coverInputFor = (memberProductId) =>
    coverInputs.find((i) => i.memberProductId === memberProductId) || null;
  const allInputsSelected = members.length > 0 && members.every((m) => coverInputFor(m.productId));
  const existingAiCover = images.find((i) => i.aiRole === 'AI_COVER') || null;

  const coverError = (e, fallback) => showFlash(apiErrorMessage(e, fallback), 'danger');

  const uploadCoverInput = async (memberProductId, file) => {
    if (!file || !editId) return;
    setCoverBusy(memberProductId);
    try {
      // Convert AVIF/HEIC (and other browser-decodable formats) to PNG before
      // upload so the backend never rejects an otherwise-fine photo.
      const uploadable = await toUploadableImage(file);
      const fd = new FormData();
      fd.append('file', uploadable);
      await axios.put(`/api/products/${editId}/cover-inputs/${memberProductId}`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      loadImages(editId);
    } catch (e) {
      coverError(e, 'Fotoğraf yüklenemedi. Üye listesini değiştirdiyseniz önce seti kaydedin.');
    } finally {
      setCoverBusy(null);
    }
  };

  const openPicker = async (memberProductId) => {
    setPickingFor(memberProductId);
    setPickerLoading(true);
    setPickerImages([]);
    try {
      const r = await axios.get(`/api/products/${memberProductId}/images`);
      setPickerImages((r.data || []).filter((i) => !i.slot && !i.aiRole));
    } catch {
      setPickerImages([]);
    } finally {
      setPickerLoading(false);
    }
  };

  // True when the backend rejected a stored photo it couldn't decode (AICOVER_008
  // — e.g. AVIF bytes saved under a .jpg name from crawling).
  const isUnsupportedFormatError = (e) => e?.response?.data?.errorCode === 'AICOVER_008';

  // Rescue path: fetch a stored image's bytes, re-encode them to PNG in the browser
  // (which can decode AVIF/HEIC the server can't), and upload as the member's cover
  // input. Returns true on success, false if even the browser can't decode it.
  const uploadRescuedCoverInput = async (memberProductId, imageId) => {
    const res = await axios.get(`/api/admin/products/images/${imageId}/view`, {
      responseType: 'blob',
    });
    const png = await reencodeImageToPng(res.data, `cover-${memberProductId}.png`);
    if (!png) return false;
    const fd = new FormData();
    fd.append('file', png);
    await axios.put(`/api/products/${editId}/cover-inputs/${memberProductId}`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return true;
  };

  // For members the server skipped (primary photo in an undecodable format), rescue
  // each one in the browser. Returns the ids still unresolved.
  const rescueMissingFromPrimaries = async (missingIds) => {
    const stillMissing = [];
    for (let i = 0; i < missingIds.length; i += 1) {
      const memberId = missingIds[i];
      try {
        // eslint-disable-next-line no-await-in-loop
        const imgs = (await axios.get(`/api/products/${memberId}/images`)).data || [];
        const candidates = imgs.filter((im) => !im.slot && !im.aiRole);
        const best = candidates.find((im) => im.primary) || candidates[0];
        if (!best) {
          stillMissing.push(memberId);
          continue;
        }
        // eslint-disable-next-line no-await-in-loop
        const ok = await uploadRescuedCoverInput(memberId, best.id);
        if (!ok) stillMissing.push(memberId);
      } catch {
        stillMissing.push(memberId);
      }
    }
    return stillMissing;
  };

  const pickCoverInput = async (imageId) => {
    if (!pickingFor || !editId) return;
    setCoverBusy(pickingFor);
    try {
      await axios.put(`/api/products/${editId}/cover-inputs/${pickingFor}/from-image/${imageId}`);
      loadImages(editId);
      setPickingFor(null);
    } catch (e) {
      // Server can't decode this stored photo — rescue it via the browser.
      if (isUnsupportedFormatError(e)) {
        try {
          if (await uploadRescuedCoverInput(pickingFor, imageId)) {
            loadImages(editId);
            setPickingFor(null);
            return;
          }
        } catch {
          /* fall through to the generic error below */
        }
      }
      coverError(e, 'Fotoğraf seçilemedi.');
    } finally {
      setCoverBusy(null);
    }
  };

  const removeCoverInput = async (memberProductId) => {
    setCoverBusy(memberProductId);
    try {
      await axios.delete(`/api/products/${editId}/cover-inputs/${memberProductId}`);
      loadImages(editId);
    } catch (e) {
      coverError(e, 'Fotoğraf kaldırılamadı.');
    } finally {
      setCoverBusy(null);
    }
  };

  const fillCoverInputsFromPrimaries = async () => {
    setCoverBusy('fill');
    try {
      const r = await axios.post(`/api/products/${editId}/cover-inputs/fill-from-primaries`);
      // Members whose primary photo the server couldn't decode (e.g. AVIF) — try to
      // rescue each in the browser before giving up.
      let missing = r.data?.missingMemberIds || [];
      if (missing.length > 0) {
        missing = await rescueMissingFromPrimaries(missing);
      }
      loadImages(editId);
      if (missing.length > 0) {
        const names = members
          .filter((m) => missing.includes(m.productId))
          .map((m) => m.name)
          .join(', ');
        showFlash(
          `Fotoğrafı olmayan veya okunamayan üyeler atlandı: ${names}. Bu üyeler için elle JPEG/PNG seçin.`,
          'warning'
        );
      } else {
        showFlash('Tüm üyelerin fotoğrafları dolduruldu.');
      }
    } catch (e) {
      coverError(e, 'Fotoğraflar doldurulamadı. Üye listesini değiştirdiyseniz önce seti kaydedin.');
    } finally {
      setCoverBusy(null);
    }
  };

  const generateAiCover = async () => {
    if (!editId || generating) return;
    if (existingAiCover) {
      const ok = await confirmDialog({
        title: 'Kapak Yeniden Oluşturulsun mu?',
        message: 'Mevcut yapay zeka kapak fotoğrafı silinecek ve yenisi oluşturulacak.',
        confirmText: 'Evet, Oluştur',
        variant: 'primary',
        icon: 'fa-magic',
      });
      if (!ok) return;
    }
    setGenerating(true);
    try {
      await axios.post(`/api/products/${editId}/generate-cover`, null, { timeout: 180000 });
      loadImages(editId);
      showFlash('Yapay zeka kapak fotoğrafı oluşturuldu ve birincil görsel olarak ayarlandı. 🎉');
    } catch (e) {
      coverError(e, 'Kapak fotoğrafı oluşturulamadı. Lütfen tekrar deneyin.');
    } finally {
      setGenerating(false);
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

  // ---- Drag-and-drop image ordering (gallery images only; slot + AI input images are fixed) ----
  const persistImageOrder = async (orderedGallery) => {
    const fixedImgs = images.filter((i) => !isGallery(i));
    setImages([...orderedGallery, ...fixedImgs]);
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
    const sorted = images.filter(isGallery).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
    const [moved] = sorted.splice(dragIndex, 1);
    sorted.splice(targetIndex, 0, moved);
    const reindexed = sorted.map((img, i) => ({ ...img, sortOrder: i }));
    setDragIndex(null);
    persistImageOrder(reindexed);
  };

  return (
    <div>
      {/* Lightbox — full-size preview of a gallery image (incl. the AI cover) */}
      {lightboxId && (
        <div
          onClick={() => setLightboxId(null)}
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 3000,
            background: 'rgba(0,0,0,0.85)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 24,
            cursor: 'zoom-out',
          }}
        >
          <button
            type="button"
            className="btn btn-light position-absolute"
            style={{ top: 16, right: 16, borderRadius: '50%', width: 40, height: 40 }}
            title="Kapat (Esc)"
            onClick={(e) => {
              e.stopPropagation();
              setLightboxId(null);
            }}
          >
            <i className="fas fa-times" />
          </button>
          <img
            src={`/api/admin/products/images/${lightboxId}/view`}
            alt="Önizleme"
            onClick={(e) => e.stopPropagation()}
            style={{
              maxWidth: '100%',
              maxHeight: '100%',
              objectFit: 'contain',
              borderRadius: 8,
              boxShadow: '0 10px 40px rgba(0,0,0,0.5)',
              cursor: 'default',
            }}
          />
        </div>
      )}

      {/* Toast notification — fixed top-right; all success/warning/error feedback lands here */}
      {flash && (
        <div
          className="position-fixed"
          style={{ zIndex: 2060, top: 76, right: 16, maxWidth: 420, minWidth: 280 }}
        >
          <div
            className={`toast show border-0 shadow-lg ${
              flash.variant === 'success'
                ? 'bg-success text-white'
                : flash.variant === 'warning'
                  ? 'bg-warning'
                  : 'bg-danger text-white'
            }`}
            role="alert"
            style={{ borderRadius: 10 }}
          >
            <div className="d-flex align-items-start">
              <div className="toast-body fw-semibold" style={{ fontSize: 13 }}>
                <i
                  className={`fas ${
                    flash.variant === 'success'
                      ? 'fa-check-circle'
                      : flash.variant === 'warning'
                        ? 'fa-exclamation-triangle'
                        : 'fa-times-circle'
                  } me-2`}
                />
                {flash.message}
              </div>
              <button
                type="button"
                className={`btn-close ${flash.variant !== 'warning' ? 'btn-close-white' : ''} me-2 mt-2 flex-shrink-0`}
                aria-label="Kapat"
                onClick={() => setFlash(null)}
              />
            </div>
          </div>
        </div>
      )}

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
              {images.filter(isGallery).length > 1 && (
                <p className="text-muted small mb-2">
                  <i className="fas fa-arrows-alt me-1" />
                  Sıralamak için görselleri sürükleyip bırakın.
                </p>
              )}
              <div className="d-flex flex-wrap gap-2">
                {images
                  .filter(isGallery)
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
                        onClick={() => setLightboxId(img.id)}
                        title="Büyütmek için tıklayın"
                        style={{ width: '100%', height: '100%', objectFit: 'cover', cursor: 'zoom-in' }}
                      />
                      {img.primary && (
                        <span
                          className="badge bg-success position-absolute top-0 start-0"
                          style={{ fontSize: 8 }}
                        >
                          Birincil
                        </span>
                      )}
                      {img.aiRole === 'AI_COVER' && (
                        <span
                          className="badge position-absolute top-0 end-0"
                          style={{ fontSize: 8, background: '#7c3aed' }}
                          title="Yapay Zeka ile üretilen kapak fotoğrafı"
                        >
                          <i className="fas fa-magic me-1" style={{ fontSize: 7 }} />
                          YZ Kapak
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
                          title="Büyüt"
                          onClick={() => setLightboxId(img.id)}
                        >
                          <i className="fas fa-search-plus" style={{ fontSize: 11 }} />
                        </button>
                        <button
                          type="button"
                          className="btn btn-sm text-white p-0 px-1"
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

      {/* AI cover photo — one input photo per member, combined by OpenAI into the set's primary image */}
      {editId && members.length > 0 && (
        <div
          className="card border-0 shadow-sm mb-3"
          style={{ borderRadius: 12, border: '1px solid #ede9fe' }}
        >
          <div className="card-body">
            <div className="d-flex justify-content-between align-items-start flex-wrap gap-2">
              <div>
                <h6 className="fw-bold mb-1" style={{ color: '#7c3aed' }}>
                  <i className="fas fa-magic me-2" />
                  Yapay Zeka Kapak Fotoğrafı
                </h6>
                <p className="text-muted small mb-0">
                  Her üye ürün için bir fotoğraf seçin; yapay zeka bunları tek bir profesyonel kapak
                  fotoğrafında birleştirir ve birincil set görseli yapar.
                </p>
              </div>
              <button
                type="button"
                className="btn btn-sm btn-outline-secondary"
                onClick={fillCoverInputsFromPrimaries}
                disabled={coverBusy !== null || generating}
                title="Her üyenin birincil fotoğrafını otomatik seç"
              >
                {coverBusy === 'fill' ? (
                  <span className="spinner-border spinner-border-sm me-1" />
                ) : (
                  <i className="fas fa-bolt me-1" />
                )}
                Tümünü Birincil Fotoğraflardan Doldur
              </button>
            </div>

            {/* One tile per member */}
            <div className="row g-2 mt-2">
              {members.map((m) => {
                const input = coverInputFor(m.productId);
                const busy = coverBusy === m.productId;
                return (
                  <div key={m.productId} className="col-6 col-md-3">
                    <div
                      className={`position-relative border rounded d-flex align-items-center justify-content-center ${
                        input ? '' : 'border-2 border-dashed'
                      }`}
                      style={{
                        aspectRatio: '1 / 1',
                        overflow: 'hidden',
                        background: input ? '#f8f9fa' : '#fff',
                      }}
                    >
                      {busy ? (
                        <span className="spinner-border spinner-border-sm text-muted" />
                      ) : input ? (
                        <>
                          <img
                            src={`/api/admin/products/images/${input.id}/view?thumbnail=true`}
                            alt={m.name}
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                          />
                          <span
                            className="badge bg-success position-absolute top-0 start-0 m-1"
                            style={{ fontSize: 8 }}
                          >
                            <i className="fas fa-check" style={{ fontSize: 7 }} />
                          </span>
                          <button
                            type="button"
                            className="btn btn-sm btn-danger position-absolute top-0 end-0 m-1 p-0 px-1"
                            title="Kaldır"
                            onClick={() => removeCoverInput(m.productId)}
                            disabled={coverBusy !== null || generating}
                          >
                            <i className="fas fa-times" style={{ fontSize: 10 }} />
                          </button>
                        </>
                      ) : (
                        <div className="text-center text-muted small px-1">
                          <i className="fas fa-image d-block mb-1" style={{ fontSize: 18, opacity: 0.4 }} />
                          Fotoğraf seçilmedi
                        </div>
                      )}
                    </div>
                    <div
                      className="small text-truncate mt-1 fw-semibold"
                      title={m.name}
                      style={{ fontSize: 11 }}
                    >
                      {m.name}
                    </div>
                    <div className="d-flex gap-1">
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-secondary flex-fill p-0 py-1"
                        style={{ fontSize: 10 }}
                        title="Ürünün kendi fotoğraflarından seç"
                        onClick={() => openPicker(m.productId)}
                        disabled={coverBusy !== null || generating}
                      >
                        <i className="fas fa-images me-1" />
                        Üründen Seç
                      </button>
                      <label
                        className="btn btn-sm btn-outline-secondary flex-fill p-0 py-1 m-0"
                        style={{
                          fontSize: 10,
                          cursor: coverBusy !== null || generating ? 'not-allowed' : 'pointer',
                        }}
                        title="Bilgisayardan yükle"
                      >
                        <i className="fas fa-upload me-1" />
                        Yükle
                        <input
                          type="file"
                          accept="image/*"
                          className="d-none"
                          disabled={coverBusy !== null || generating}
                          onChange={(e) => {
                            uploadCoverInput(m.productId, e.target.files?.[0]);
                            e.target.value = '';
                          }}
                        />
                      </label>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Inline picker: member product's own photos */}
            {pickingFor !== null && (
              <div className="border rounded p-2 mt-3" style={{ background: '#faf5ff' }}>
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <strong className="small">
                    <i className="fas fa-images me-1" />
                    {members.find((m) => m.productId === pickingFor)?.name} — fotoğraf seçin
                  </strong>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary py-0"
                    onClick={() => setPickingFor(null)}
                  >
                    Kapat
                  </button>
                </div>
                {pickerLoading ? (
                  <div className="text-muted small">
                    <span className="spinner-border spinner-border-sm me-2" />
                    Fotoğraflar yükleniyor...
                  </div>
                ) : pickerImages.length === 0 ? (
                  <div className="text-muted small">
                    Bu ürünün fotoğrafı yok. &quot;Yükle&quot; ile bilgisayardan ekleyebilirsiniz.
                  </div>
                ) : (
                  <div className="d-flex flex-wrap gap-2">
                    {pickerImages.map((pi) => (
                      <button
                        key={pi.id}
                        type="button"
                        className="border rounded p-0 bg-transparent"
                        style={{ width: 72, height: 72, overflow: 'hidden', cursor: 'pointer' }}
                        title={pi.primary ? 'Birincil fotoğraf' : 'Seç'}
                        onClick={() => pickCoverInput(pi.id)}
                        disabled={coverBusy !== null}
                      >
                        <img
                          src={`/api/admin/products/images/${pi.id}/view?thumbnail=true`}
                          alt=""
                          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                        />
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Generate */}
            <div className="d-flex align-items-center flex-wrap gap-2 mt-3">
              <button
                type="button"
                className="btn text-white"
                style={{
                  background: 'linear-gradient(135deg, #7c3aed, #a855f7)',
                  opacity: !allInputsSelected || generating || coverBusy !== null ? 0.6 : 1,
                }}
                onClick={generateAiCover}
                disabled={!allInputsSelected || generating || coverBusy !== null}
              >
                {generating ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-2" />
                    Oluşturuluyor...
                  </>
                ) : (
                  <>
                    <i className="fas fa-magic me-2" />
                    Yapay Zeka ile Kapak Fotoğrafı Oluştur
                  </>
                )}
              </button>
              {!allInputsSelected && (
                <span className="text-muted small">
                  <i className="fas fa-info-circle me-1" />
                  Tüm üyeler için fotoğraf seçildiğinde aktif olur.
                </span>
              )}
              {existingAiCover && !generating && (
                <span className="badge" style={{ background: '#7c3aed' }}>
                  <i className="fas fa-check me-1" />
                  Mevcut YZ kapak galeride — yeniden oluşturursanız eskisi silinir
                </span>
              )}
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
