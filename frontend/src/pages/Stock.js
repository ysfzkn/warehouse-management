import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';
import { formatPhoneForDisplay } from '../utils/phone';
import StockForm from '../components/StockForm';
import QuickStockAdjustModal from '../components/QuickStockAdjustModal';
import StockSettingsModal from '../components/StockSettingsModal';
import StockTransferModal from '../components/StockTransferModal';
import StockRequestApprovalModal from '../components/StockRequestApprovalModal';
import SearchableSelect from '../components/SearchableSelect';
import FilterChips from '../components/FilterChips';
import ConfirmModal from '../components/ConfirmModal';
import NotesModal from '../components/NotesModal';
import AuditTimelineModal from '../components/AuditTimelineModal';
import MyStockRequestsModal from '../components/MyStockRequestsModal';
import PaginationControls from '../components/PaginationControls';

// Helper function to format dates in Turkey timezone
const formatDateInTurkeyTimezone = (isoDateString, options = {}) => {
  if (!isoDateString) return '-';
  try {
    const date = new Date(isoDateString);
    // Force Turkey timezone display
    return date.toLocaleString('tr-TR', {
      timeZone: 'Europe/Istanbul',
      ...options
    });
  } catch (error) {
    console.error('Date formatting error:', error);
    return '-';
  }
};

const PAGE_SIZE_OPTIONS = [20, 50, 100, 250];

// Stable filters bar component to prevent input remount/focus loss
const StockFiltersBar = ({
  searchTerm,
  setSearchTerm,
  selectedWarehouseId,
  setSelectedWarehouseId,
  selectedWarehouseOpt,
  setSelectedWarehouseOpt,
  brandId,
  setBrandId,
  brandOpt,
  setBrandOpt,
  colorId,
  setColorId,
  colorOpt,
  setColorOpt,
  categories,
  subcategories,
  setSubcategories,
  selectedCategory,
  setSelectedCategory,
  selectedSubcategory,
  setSelectedSubcategory,
  showReserved,
  setShowReserved,
  showConsigned,
  setShowConsigned,
  getWarehouseById
}) => {
  const searchInputRef = useRef(null);

  useEffect(() => {
    if (searchInputRef.current && document.activeElement !== searchInputRef.current) {
      searchInputRef.current.focus();
      const len = searchInputRef.current.value.length;
      try { searchInputRef.current.setSelectionRange(len, len); } catch { }
    }
  }, [searchTerm]);

  return (
    <>
      <style>{`
        @media (max-width: 1155px) {
          .stock-mobile-card,
          .transfer-mobile-card {
            border-radius: 22px;
            border: 1px solid rgba(15,23,42,0.12);
            background: linear-gradient(135deg, #ffffff, #f8fafc);
            box-shadow: 0 16px 40px rgba(15,23,42,0.12);
          }
          .stock-mobile-card.is-selected,
          .transfer-mobile-card.is-selected {
            border-color: #3b82f6 !important;
            box-shadow: 0 20px 45px rgba(59,130,246,0.22);
          }
          .stock-mobile-card__header,
          .transfer-mobile-card__header {
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
            padding-bottom: 0.75rem;
            border-bottom: 1px solid rgba(15,23,42,0.08);
            margin-bottom: 1rem;
          }
          .stock-mobile-card__warehouse {
            font-size: 0.8rem;
            text-transform: uppercase;
            letter-spacing: 0.08em;
            color: #94a3b8;
          }
          .stock-mobile-card__title,
          .transfer-mobile-card__title {
            font-size: 1.05rem;
            font-weight: 600;
            color: #0f172a;
            margin-bottom: 0.1rem;
          }
          .transfer-mobile-card__header .text-muted.small {
            display: block;
          }
          .transfer-mobile-card__badges {
            text-align: right;
            margin-top: 0.5rem;
          }
          .transfer-mobile-card__badges small {
            color: #64748b;
            font-size: 0.75rem;
            display: block;
            margin-bottom: 0.5rem;
          }
          .transfer-mobile-card__badges .d-flex {
            gap: 0.5rem !important;
            justify-content: flex-end;
            flex-wrap: wrap;
            align-items: center;
          }
        .mobile-selection-toolbar {
          border: 1px solid rgba(15, 23, 42, 0.08);
          border-radius: 18px;
          padding: 0.75rem 1rem;
          background: #fff;
          box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08);
        }
        .mobile-selection-toolbar .btn {
          min-width: 110px;
          }
          .mobile-chip {
            border-radius: 999px;
            padding: 0.4rem 0.85rem;
            font-size: 0.7rem;
            font-weight: 600;
            border: none;
            display: inline-flex;
            align-items: center;
            white-space: nowrap;
            box-shadow: 0 2px 4px rgba(0,0,0,0.08);
          }
          .mobile-chip.badge {
            border: none;
          }
          .mobile-chip.bg-warning {
            background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%) !important;
            color: #fff !important;
          }
          .mobile-chip.bg-info {
            background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%) !important;
            color: #fff !important;
          }
          .mobile-chip.bg-success {
            background: linear-gradient(135deg, #10b981 0%, #059669 100%) !important;
            color: #fff !important;
          }
          .mobile-chip.bg-danger {
            background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%) !important;
            color: #fff !important;
          }
          .mobile-card-checkbox-wrapper {
            width: 36px;
            height: 36px;
            border-radius: 12px;
            background: rgba(15,23,42,0.04);
            display: inline-flex;
            align-items: center;
            justify-content: center;
          }
          .mobile-card-checkbox {
            appearance: none;
            width: 18px;
            height: 18px;
            border-radius: 6px;
            border: 2px solid rgba(15,23,42,0.35);
            background: #fff;
            position: relative;
            cursor: pointer;
            transition: all 0.2s ease;
          }
          .mobile-card-checkbox:checked {
            background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
            border-color: transparent;
            box-shadow: 0 4px 10px rgba(37,99,235,0.35);
          }
          .mobile-card-checkbox:checked::after {
            content: '';
            position: absolute;
            left: 4px;
            top: 1px;
            width: 6px;
            height: 10px;
            border: solid #fff;
            border-width: 0 2px 2px 0;
            transform: rotate(45deg);
          }
          .mobile-card-checkbox:focus-visible {
            outline: none;
            box-shadow: 0 0 0 4px rgba(59,130,246,0.35);
          }
          .stock-mobile-card__tags {
            display: flex;
            justify-content: space-between;
            gap: 0.75rem;
            align-items: center;
            flex-wrap: wrap;
            margin-bottom: 1rem;
          }
          .stock-mobile-card__tags-left {
            display: inline-flex;
            flex-wrap: wrap;
            gap: 0.4rem;
            align-items: center;
          }
          .stock-mobile-card__tags-right {
            margin-left: auto;
            display: flex;
            justify-content: flex-end;
            min-height: 36px;
          }
          .mobile-chip-note {
            background: rgba(15,23,42,0.06);
            color: #475569;
            max-width: 180px;
            text-overflow: ellipsis;
            overflow: hidden;
            display: inline-flex;
            align-items: center;
            white-space: nowrap;
          }
          .mobile-stat-grid .mobile-stat-tile {
            border-radius: 16px;
            padding: 0.8rem;
            border: 1px solid rgba(15,23,42,0.08);
            background: #fff;
            text-align: center;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
          }
          .mobile-stat-grid .col-6,
          .mobile-stat-grid .col-4 {
            display: flex;
          }
          .mobile-stat-grid .col-6 .mobile-stat-tile,
          .mobile-stat-grid .col-4 .mobile-stat-tile {
            width: 100%;
            height: 100%;
          }
          .mobile-stat-tile .label {
            font-size: 0.7rem;
            text-transform: uppercase;
            letter-spacing: 0.08em;
            color: #94a3b8;
            white-space: nowrap;
          }
          .mobile-stat-tile .value {
            font-size: 1.15rem;
            font-weight: 600;
            color: #0f172a;
          }
          .stock-mobile-card__footer,
          .transfer-mobile-card__footer {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 0.75rem;
          }
          .stock-mobile-card__actions,
          .transfer-mobile-card__actions {
            display: flex;
            flex-wrap: wrap;
            gap: 0.6rem;
            justify-content: center;
            align-items: center;
            width: 100%;
            margin-top: 0.5rem;
            padding-top: 0.75rem;
            border-top: 1px solid rgba(15,23,42,0.08);
          }
          .mobile-action-btn {
            width: 44px;
            height: 44px;
            border-radius: 12px !important;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: 0 !important;
            box-shadow: 0 2px 6px rgba(0,0,0,0.1);
            transition: all 0.2s ease;
          }
          .mobile-action-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(0,0,0,0.15);
          }
          .mobile-action-btn i {
            font-size: 0.95rem;
          }
          .transfer-mobile-card__primary-actions {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            margin-bottom: 0.75rem;
            align-items: center;
          }
          .transfer-mobile-card__primary-actions .btn {
            border-radius: 14px;
            font-weight: 600;
            width: 100%;
            max-width: 100%;
          }
          .transfer-mobile-card__summary {
            border-radius: 16px;
            border: 1px solid rgba(15,23,42,0.08);
            background: #fff;
            padding: 0.9rem;
            margin-top: 1rem;
          }
          .transfer-type-pill {
            border-radius: 999px;
            padding: 0.4rem 0.85rem;
            font-size: 0.7rem;
            font-weight: 600;
            background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
            color: #475569;
            border: 1px solid rgba(148,163,184,0.3);
            display: inline-flex;
            align-items: center;
            white-space: nowrap;
            box-shadow: 0 2px 4px rgba(0,0,0,0.06);
          }
        }

        .stock-filter-card {
          border: 1px solid rgba(15,23,42,0.08);
          border-radius: 18px;
          padding: 0.85rem 1rem;
          background: #fff;
          height: 100%;
          box-shadow: 0 10px 25px rgba(15, 23, 42, 0.05);
        }
        .stock-filter-card small {
          text-transform: uppercase;
          font-size: 0.7rem;
          color: #94a3b8;
          letter-spacing: 0.08em;
        }
        .stock-filter-card select,
        .stock-filter-card .input-group,
        .stock-filter-card .form-select {
          margin-top: 0.4rem;
        }
        .stock-filter-toggle-modern {
          display: flex;
          flex-wrap: wrap;
          gap: 0.5rem;
        }
        .stock-filter-toggle-modern .btn {
          border-radius: 999px;
          padding: 0.35rem 0.9rem;
        }

        .approval-mobile-list {
          border-radius: 18px;
          border: 1px solid rgba(15,23,42,0.08);
          background: #fff;
          max-height: 60vh;
          overflow-y: auto;
        }
        .approval-mobile-card {
          border-bottom: 1px solid rgba(15,23,42,0.07);
          padding: 0.85rem 1rem;
        }
        .approval-mobile-card:last-child {
          border-bottom: none;
        }
        .approval-pill {
          border-radius: 999px;
          padding: 0.3rem 0.75rem;
          font-size: 0.72rem;
          font-weight: 600;
          background: #f1f5f9;
          color: #0f172a;
        }
      `}</style>
      <div className="row g-3 align-items-stretch mb-3">
        <div className="col-12 col-md-6 col-xl-3">
          <div className="stock-filter-card">
            <small>Arama</small>
            <div className="input-group mt-2">
              <span className="input-group-text bg-transparent border-end-0"><i className="fas fa-search text-secondary"></i></span>
              <input
                ref={searchInputRef}
                type="text"
                className="form-control border-start-0"
                placeholder="Ürün adı, stok kodu, depo, müşteri adı veya telefon ara..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>
        </div>
        <div className="col-12 col-md-6 col-xl-3">
          <div className="stock-filter-card">
            <small>Depo</small>
            <div className="mt-2">
              <SearchableSelect
                label=""
                value={selectedWarehouseId}
                onChange={(id, opt) => { setSelectedWarehouseId(id); setSelectedWarehouseOpt(opt || null); }}
                searchEndpoint="/api/warehouses"
                placeholder="Depo ara..."
                allowClear={true}
                clearText="Temizle"
                wrapperClassName="mb-0"
                renderOption={(w) => w.name}
              />
            </div>
          </div>
        </div>
        <div className="col-12 col-md-6 col-xl-3">
          <div className="stock-filter-card">
            <small>Marka</small>
            <div className="mt-2">
              <SearchableSelect
                label=""
                value={brandId}
                onChange={(id, opt) => { setBrandId(id); setBrandOpt(opt || null); }}
                searchEndpoint="/api/brands/search"
                placeholder="Marka ara..."
                allowClear={true}
                clearText="Temizle"
                wrapperClassName="mb-0"
              />
            </div>
          </div>
        </div>
        <div className="col-12 col-md-6 col-xl-3">
          <div className="stock-filter-card">
            <small>Renk</small>
            <div className="mt-2">
              <SearchableSelect
                label=""
                value={colorId}
                onChange={(id, opt) => { setColorId(id); setColorOpt(opt || null); }}
                searchEndpoint="/api/colors/search"
                placeholder="Renk ara..."
                allowClear={true}
                clearText="Temizle"
                wrapperClassName="mb-0"
              />
            </div>
          </div>
        </div>
      </div>

      {/* Category filters */}
      <div className="row g-3 mb-3">
        <div className="col-12 col-md-6">
          <div className="stock-filter-card">
            <small>Ana Kategori</small>
            <select
              className="form-select mt-2"
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
            >
              <option value="">Tüm Ana Kategoriler</option>
              {Array.isArray(categories) && categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="col-12 col-md-6">
          <div className="stock-filter-card">
            <small>Alt Kategori</small>
            <select
              className="form-select mt-2"
              value={selectedSubcategory}
              onChange={(e) => setSelectedSubcategory(e.target.value)}
              disabled={!selectedCategory}
            >
              <option value="">{selectedCategory ? 'Tüm Alt Kategoriler' : 'Önce ana kategori seçin'}</option>
              {Array.isArray(subcategories) && subcategories.map((subcategory) => (
                <option key={subcategory.id} value={subcategory.id}>
                  {subcategory.name}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Additional filters */}
      <div className="stock-filter-toggle-modern mb-3">
        <div>
          <input
            className="btn-check"
            type="checkbox"
            id="showReserved"
            checked={showReserved}
            onChange={(e) => setShowReserved(e.target.checked)}
          />
          <label className={`btn btn-sm btn-outline-secondary ${showReserved ? 'active' : ''}`} htmlFor="showReserved">
            <i className="fas fa-lock me-1"></i>
            Rezerve
          </label>
        </div>
        <div>
          <input
            className="btn-check"
            type="checkbox"
            id="showConsigned"
            checked={showConsigned}
            onChange={(e) => setShowConsigned(e.target.checked)}
          />
          <label className={`btn btn-sm btn-outline-secondary ${showConsigned ? 'active' : ''}`} htmlFor="showConsigned">
            <i className="fas fa-handshake me-1"></i>
            Emanet
          </label>
        </div>
      </div>

      <FilterChips
        className="mb-3"
        chips={[
          searchTerm ? { icon: 'fas fa-search', label: `Arama: "${searchTerm}"`, onClear: () => setSearchTerm('') } : null,
          selectedWarehouseId ? { icon: 'fas fa-warehouse', label: `Depo: ${selectedWarehouseOpt?.name || getWarehouseById(selectedWarehouseId)?.name || selectedWarehouseId}`, onClear: () => { setSelectedWarehouseId(null); setSelectedWarehouseOpt(null); } } : null,
          selectedCategory ? { icon: 'fas fa-tag', label: `Ana Kategori: ${Array.isArray(categories) ? categories.find(c => c.id.toString() === selectedCategory)?.name || selectedCategory : selectedCategory}`, onClear: () => { setSelectedCategory(''); setSelectedSubcategory(''); setSubcategories([]); } } : null,
          selectedSubcategory ? { icon: 'fas fa-tags', label: `Alt Kategori: ${subcategories.find(c => c.id.toString() === selectedSubcategory)?.name || selectedSubcategory}`, onClear: () => setSelectedSubcategory('') } : null,
          brandId ? { icon: 'fas fa-copyright', label: `Marka: ${brandOpt?.name || brandId}`, onClear: () => { setBrandId(null); setBrandOpt(null); } } : null,
          colorId ? { icon: 'fas fa-palette', label: `Renk: ${colorOpt?.name || colorId}`, onClear: () => { setColorId(null); setColorOpt(null); } } : null,
          showReserved ? { icon: 'fas fa-lock', label: 'Rezerve Olanlar', onClear: () => setShowReserved(false) } : null,
          showConsigned ? { icon: 'fas fa-handshake', label: 'Emanet Olanlar', onClear: () => setShowConsigned(false) } : null,
        ].filter(Boolean)}
        onClearAll={() => {
          setSearchTerm('');
          setSelectedWarehouseId(null);
          setSelectedWarehouseOpt(null);
          setSelectedCategory('');
          setSelectedSubcategory('');
          setSubcategories([]);
          setBrandId(null);
          setColorId(null);
          setBrandOpt(null);
          setColorOpt(null);
          setShowReserved(false);
          setShowConsigned(false);
        }}
      />
    </>
  );
};

const transferStatusMap = {
  PENDING: { label: 'Beklemede', bootstrap: 'warning', icon: 'clock' },
  PENDING_APPROVAL: { label: 'Beklemede', bootstrap: 'warning', icon: 'clock' },
  IN_TRANSIT: { label: 'Yolda', bootstrap: 'info', icon: 'truck' },
  COMPLETED: { label: 'Tamamlandı', bootstrap: 'success', icon: 'check-circle' },
  CANCELLED: { label: 'İptal Edildi', bootstrap: 'danger', icon: 'times-circle' },
  APPROVED: { label: 'Onaylandı', bootstrap: 'success', icon: 'check' },
  REJECTED: { label: 'Reddedildi', bootstrap: 'danger', icon: 'times' },
  DEFAULT: { label: 'İşlemde', bootstrap: 'secondary', icon: 'info-circle' },
};

const getTransferStatusMeta = (status) => {
  const key = (status || '').toUpperCase();
  return transferStatusMap[key] || transferStatusMap.DEFAULT;
};

const getTransferTypeLabel = (type) =>
  (type || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'Müşteri Sevkiyatı' : 'Depo Transferi';

const Stock = () => {
  const location = useLocation();
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const isAdmin = role === 'ADMIN';
  const canTransfer = isAdmin || role === 'STOCK_IN' || role === 'STOCK_OUT';
  const [stocks, setStocks] = useState([]);
  const [stockPage, setStockPage] = useState(0);
  const [stockPageSize, setStockPageSize] = useState(100);
  const [stockTotalPages, setStockTotalPages] = useState(0);
  const [totalStockCount, setTotalStockCount] = useState(0);
  const [products, setProducts] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [quickAdjustModal, setQuickAdjustModal] = useState({ show: false, stock: null, type: null });
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [showTransferHistory, setShowTransferHistory] = useState(false);
  const [transferDetailModal, setTransferDetailModal] = useState({ show: false, transfer: null });
  const [selectedStock, setSelectedStock] = useState(null);
  const [transfers, setTransfers] = useState([]);
  const [transferPage, setTransferPage] = useState(0);
  const [transferPageSize, setTransferPageSize] = useState(100);
  const [transferTotalPages, setTransferTotalPages] = useState(0);
  const [transferTotalCount, setTransferTotalCount] = useState(0);
  const [transferStatusCounts, setTransferStatusCounts] = useState({});
  const [transferTypeCounts, setTransferTypeCounts] = useState({});
  const [filter, setFilter] = useState('all'); // all, low-stock, out-of-stock
  const [brandId, setBrandId] = useState(null);
  const [colorId, setColorId] = useState(null);
  const [brandOpt, setBrandOpt] = useState(null);
  const [colorOpt, setColorOpt] = useState(null);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(null);
  const [selectedWarehouseOpt, setSelectedWarehouseOpt] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [showReserved, setShowReserved] = useState(false);
  const [showConsigned, setShowConsigned] = useState(false);
  const [transferStatusFilter, setTransferStatusFilter] = useState('ALL');
  // Transfer filters
  const [transferProductName, setTransferProductName] = useState('');
  const [transferSku, setTransferSku] = useState('');
  const [transferDriver, setTransferDriver] = useState('');
  const [transferNotes, setTransferNotes] = useState('');
  const [transferSourceWarehouseId, setTransferSourceWarehouseId] = useState(null);
  const [transferDestinationWarehouseId, setTransferDestinationWarehouseId] = useState(null);
  const [transferTypeFilter, setTransferTypeFilter] = useState('ALL');
  // Category filters
  const [categories, setCategories] = useState([]);
  const [subcategories, setSubcategories] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedSubcategory, setSelectedSubcategory] = useState('');
  // Stock list sorting
  const [stockSortBy, setStockSortBy] = useState('lastUpdated'); // 'warehouse' | 'lastUpdated' | 'quantity'
  const [stockSortDir, setStockSortDir] = useState('desc'); // 'asc' | 'desc'

  // Modal states
  const [confirmModal, setConfirmModal] = useState({ show: false, title: '', message: '', onConfirm: null });
  const [errorModal, setErrorModal] = useState({ show: false, title: '', message: '' });
  const [errorDetailsModal, setErrorDetailsModal] = useState({ show: false, title: '', errors: [] });
  const [notesModal, setNotesModal] = useState({ show: false, notes: '', transferId: null, title: '' });
  const [cancellationModal, setCancellationModal] = useState({ show: false, transferId: null, reason: '' });
  const [completionModal, setCompletionModal] = useState({ show: false, transferId: null, note: '', message: '', transfer: null });
  const [auditModal, setAuditModal] = useState({ show: false, entityType: null, entityId: null });
  const [pendingStockId, setPendingStockId] = useState(null);
  const [showExcelModal, setShowExcelModal] = useState(false);
  const [excelUploading, setExcelUploading] = useState(false);
  const [excelFile, setExcelFile] = useState(null);
  const [excelWarehouseId, setExcelWarehouseId] = useState(null);
  const [excelResult, setExcelResult] = useState(null);
  const [showApprovalModal, setShowApprovalModal] = useState(false);
  const [showMyRequestsModal, setShowMyRequestsModal] = useState(false);
  const [lockCustomerTransfer, setLockCustomerTransfer] = useState(false);
  const [pendingRequestsCount, setPendingRequestsCount] = useState(0);
  const [selectedStocks, setSelectedStocks] = useState([]);
  const [selectedTransfers, setSelectedTransfers] = useState([]);
  const [successToast, setSuccessToast] = useState({ show: false, message: '' });
  const toastTimeoutRef = useRef(null);

  const fetchStocks = useCallback(async (pageOverride = 0, pageSizeOverride) => {
    const size = pageSizeOverride ?? stockPageSize;
    const categoryId = selectedCategory ? Number(selectedCategory) : undefined;
    const subCategoryId = selectedSubcategory ? Number(selectedSubcategory) : undefined;
    const sortByParam = stockSortBy === 'lastUpdated'
      ? 'lastUpdated'
      : (stockSortBy === 'quantity' ? 'quantity' : 'warehouse');
    const params = {
      page: pageOverride,
      size,
      brandId: brandId || undefined,
      colorId: colorId || undefined,
      warehouseId: selectedWarehouseId || undefined,
      categoryId: Number.isNaN(categoryId) ? undefined : categoryId,
      subCategoryId: Number.isNaN(subCategoryId) ? undefined : subCategoryId,
      reservedOnly: showReserved || undefined,
      consignedOnly: showConsigned || undefined,
      status: filter,
      search: searchTerm ? searchTerm.trim() : undefined,
      sortBy: sortByParam,
      sortDir: stockSortDir
    };

    const response = await axios.get('/api/stocks', { params });
    const data = response.data || {};
    const content = Array.isArray(data.content) ? data.content : [];

    setStocks(content);
    setStockPage(data.page ?? pageOverride);
    setTotalStockCount(typeof data.totalElements === 'number' ? data.totalElements : content.length);
    setStockTotalPages(typeof data.totalPages === 'number' ? data.totalPages : 0);
    return data;
  }, [stockPageSize, brandId, colorId, selectedWarehouseId, selectedCategory, selectedSubcategory, showReserved, showConsigned, filter, searchTerm, stockSortBy, stockSortDir]);

  const fetchTransfers = useCallback(async (page = 0, append = false, pageSizeOverride) => {
    const size = pageSizeOverride ?? transferPageSize;
    try {
      const normalizedProductName = transferProductName ? transferProductName.toLocaleLowerCase('tr-TR') : undefined;
      const normalizedSku = transferSku ? transferSku.toLocaleLowerCase('tr-TR') : undefined;
      const normalizedDriver = transferDriver ? transferDriver.toLocaleLowerCase('tr-TR') : undefined;
      const normalizedNotes = transferNotes ? transferNotes.toLocaleLowerCase('tr-TR') : undefined;
      const params = {
        page,
        size,
        status: transferStatusFilter !== 'ALL' ? transferStatusFilter : undefined,
        transferType: transferTypeFilter !== 'ALL' ? transferTypeFilter : undefined,
        productName: normalizedProductName,
        sku: normalizedSku,
        driverName: normalizedDriver,
        notes: normalizedNotes,
        sourceWarehouseId: transferSourceWarehouseId || undefined,
        destinationWarehouseId: transferDestinationWarehouseId || undefined,
        sort: 'updatedAt,desc'
      };
      const endpoint = isAdmin ? '/api/stock-transfers' : '/api/stock-transfers/current-user';
      const response = await axios.get(endpoint, { params });
      const data = response.data || {};
      const content = Array.isArray(data.content)
        ? data.content
        : (Array.isArray(data) ? data : []);
      setTransfers(prev => append ? [...prev, ...content] : content);
      setTransferPage(data.page ?? page);
      setTransferTotalCount(prevCount => {
        if (typeof data.totalElements === 'number') {
          return data.totalElements;
        }
        return append ? prevCount + content.length : content.length;
      });
      setTransferTotalPages(typeof data.totalPages === 'number' ? data.totalPages : 0);

      const metadata = data.metadata || {};
      setTransferStatusCounts(metadata.statusCounts || {});
      setTransferTypeCounts(metadata.transferTypeCounts || {});
      return data;
    } catch (error) {
      console.error('Error fetching transfers:', error);
      throw error;
    }
  }, [isAdmin, transferStatusFilter, transferTypeFilter, transferProductName, transferSku, transferDriver, transferNotes, transferSourceWarehouseId, transferDestinationWarehouseId, transferPageSize]);

  const fetchAllData = useCallback(async () => {
    try {
      setLoading(true);
      const calls = [
        axios.get('/api/products'),
        axios.get('/api/warehouses')
      ];

      // Fetch pending requests count for admins
      if (role === 'ADMIN') {
        calls.push(
          axios.get('/api/stock-requests/pending/count'),
          axios.get('/api/stock-transfers/approvals/count')
        );
      }

      const results = await Promise.all(calls);

      let index = 0;
      const productsData = results[index++].data;
      const warehousesData = results[index++].data;
      // Handle paginated response
      setProducts(Array.isArray(productsData) ? productsData : (Array.isArray(productsData?.content) ? productsData.content : []));
      setWarehouses(Array.isArray(warehousesData) ? warehousesData : (Array.isArray(warehousesData?.content) ? warehousesData.content : []));

      if (role === 'ADMIN') {
        const stockPendingResult = results[index] || null;
        const transferPendingResult = results[index + 1] || null;
        index += 2;
        const stockPending = stockPendingResult?.data?.count || 0;
        const transferPending = transferPendingResult?.data?.count || 0;
        setPendingRequestsCount(stockPending + transferPending);
      }
    } catch (error) {
      console.error('Error fetching data:', error);
      setError('Veriler yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  }, [role]);
  const handleStockPageSizeChange = async (e) => {
    const newSize = Number(e.target.value);
    setStockPageSize(newSize);
    setStockPage(0);
    try {
      await fetchStocks(0, newSize);
    } catch (error) {
      console.error('Error changing stock page size:', error);
      setError('Stok verileri yüklenirken hata oluştu');
    }
  };

  const handleTransferPageSizeChange = async (e) => {
    const newSize = Number(e.target.value);
    setTransferPageSize(newSize);
    setTransferPage(0);
    try {
      await fetchTransfers(0, false, newSize);
    } catch (error) {
      console.error('Error changing transfer page size:', error);
      setError('Transfer verileri yüklenirken hata oluştu');
    }
  };

  const openTransferDetailModal = (transfer) => {
    setTransferDetailModal({ show: true, transfer });
  };

  const closeTransferDetailModal = () => {
    setTransferDetailModal({ show: false, transfer: null });
  };

  const handleStockPageChange = (newPage) => {
    const total = stockTotalPages || 0;
    if (newPage < 0 || (total > 0 && newPage >= total) || newPage === stockPage) {
      return;
    }
    setStockPage(newPage);
  };

  const handleTransferPageChange = (newPage) => {
    const total = transferTotalPages || 0;
    if (newPage < 0 || (total > 0 && newPage >= total) || newPage === transferPage) {
      return;
    }
    setTransferPage(newPage);
  };


  useEffect(() => {
    fetchStocks(stockPage);
  }, [fetchStocks, stockPage]);

  useEffect(() => {
    fetchAllData();
  }, [fetchAllData]);

  useEffect(() => {
    if (showTransferHistory) {
      fetchTransfers(transferPage, false);
    }
  }, [showTransferHistory, fetchTransfers, transferPage]);

  useEffect(() => {
    setStockPage(0);
  }, [filter, searchTerm, selectedCategory, selectedSubcategory, showReserved, showConsigned, brandId, colorId, selectedWarehouseId, stockSortBy, stockSortDir]);

  useEffect(() => {
    setTransferPage(0);
  }, [transferStatusFilter, transferTypeFilter, transferProductName, transferSku, transferDriver, transferNotes, transferSourceWarehouseId, transferDestinationWarehouseId]);

  // Fetch main categories on mount
  useEffect(() => {
    const fetchMainCategories = async () => {
      try {
        const response = await axios.get('/api/categories/top-level');
        const categoriesData = response.data;
        const list = Array.isArray(categoriesData?.content)
          ? categoriesData.content
          : (Array.isArray(categoriesData) ? categoriesData : []);
        const normalized = list.map(cat => ({
          ...cat,
          children: Array.isArray(cat.children) ? cat.children : (Array.isArray(cat.subcategories) ? cat.subcategories : [])
        }));
        setCategories(normalized);
      } catch (error) {
        // noop
      }
    };
    fetchMainCategories();
  }, []);

  // Fetch subcategories when a main category is selected
  useEffect(() => {
    const fetchSubs = async () => {
      if (!selectedCategory) {
        setSubcategories([]);
        setSelectedSubcategory('');
        return;
      }
      const parent = categories.find(cat => cat.id?.toString() === String(selectedCategory));
      if (parent && Array.isArray(parent.children) && parent.children.length > 0) {
        setSubcategories(parent.children);
        setSelectedSubcategory('');
        return;
      }
      try {
        const response = await axios.get(`/api/categories/${selectedCategory}/subcategories`);
        setSubcategories(response.data);
        setSelectedSubcategory('');
      } catch (error) {
        setSubcategories([]);
        setSelectedSubcategory('');
      }
    };
    fetchSubs();
  }, [selectedCategory, categories]);

  // Initialize filters from query params
  // React to query string changes (navigation within app)
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const f = params.get('filter');
    const b = params.get('brandId');
    const c = params.get('colorId');
    const w = params.get('warehouseId');
    const stockIdParam = params.get('stockId');
    const transferIdParam = params.get('transferId');
    const auditStockIdParam = params.get('auditStockId');
    const auditTransferIdParam = params.get('auditTransferId');
    if (f === 'low-stock' || f === 'out-of-stock' || f === 'all') setFilter(f);
    if (b) setBrandId(Number(b));
    if (c) setColorId(Number(c));
    if (w) setSelectedWarehouseId(Number(w));
    if (stockIdParam) setPendingStockId(Number(stockIdParam));
    if (transferIdParam) {
      // Open transfer history and highlight by filtering to ALL
      setShowTransferHistory(true);
      // No direct single transfer view page exists; focus by opening history
      setTimeout(() => { fetchTransfers(0, false); }, 0);
    }
    if (auditStockIdParam) {
      const idNum = Number(auditStockIdParam);
      if (!Number.isNaN(idNum)) {
        setAuditModal({ show: true, entityType: 'Stock', entityId: idNum });
      }
    }
    if (auditTransferIdParam) {
      const idNum = Number(auditTransferIdParam);
      if (!Number.isNaN(idNum)) {
        setAuditModal({ show: true, entityType: 'StockTransfer', entityId: idNum });
      }
    }
  }, [location.search]);

  // Listen to global event to open audit directly from Navbar without navigation
  useEffect(() => {
    const handler = (e) => {
      const detail = e?.detail || {};
      if (detail.entityType && detail.entityId) {
        setAuditModal({ show: true, entityType: detail.entityType, entityId: Number(detail.entityId) });
      }
    };
    window.addEventListener('open-audit', handler);
    return () => window.removeEventListener('open-audit', handler);
  }, []);

  // Listen to global event to open stock approval modal from Navbar
  const [approvalModalTab, setApprovalModalTab] = useState('stock');

  useEffect(() => {
    const handler = (event) => {
      const tab = event.detail?.tab || 'stock';
      setApprovalModalTab(tab);
      setShowApprovalModal(true);
    };
    window.addEventListener('open-stock-approval', handler);
    return () => window.removeEventListener('open-stock-approval', handler);
  }, []);

  // Check URL parameter to open approval modal
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get('openApproval') === 'true') {
      const tab = params.get('tab') || 'stock';
      setApprovalModalTab(tab);
      setShowApprovalModal(true);
      // Clean up URL parameter
      params.delete('openApproval');
      params.delete('tab');
      const newSearch = params.toString();
      const newUrl = newSearch ? `${location.pathname}?${newSearch}` : location.pathname;
      window.history.replaceState({}, '', newUrl);
    }
  }, [location.search]);

  // Open quick adjustment modal when stocks loaded and pendingStockId exists
  useEffect(() => {
    if (!pendingStockId || !Array.isArray(stocks) || stocks.length === 0) return;
    const s = stocks.find(x => x.id === pendingStockId);
    if (s) {
      setQuickAdjustModal({ show: true, stock: s, type: 'add' });
      setPendingStockId(null);
    }
  }, [stocks, pendingStockId]);

  useEffect(() => {
    if (!selectedStocks.length) return;
    setSelectedStocks(prev => prev.filter(id => stocks.some(stock => stock.id === id)));
  }, [stocks, selectedStocks.length]);

  const getEffectiveMin = useCallback((stock) => {
    const val = Number(stock?.minStockLevel);
    if (!Number.isFinite(val) || val <= 0) return 10;
    return val;
  }, []);

  const allVisibleStockIds = useMemo(() => stocks.map(stock => stock.id), [stocks]);
  const areAllVisibleSelected = stocks.length > 0 && allVisibleStockIds.every(id => selectedStocks.includes(id));
  const selectedStockCount = selectedStocks.length;

  const toggleSelectAllVisible = () => {
    if (!stocks.length) return;
    setSelectedStocks(prev => {
      if (areAllVisibleSelected) {
        return prev.filter(id => !allVisibleStockIds.includes(id));
      }
      const merged = new Set(prev);
      allVisibleStockIds.forEach(id => merged.add(id));
      return Array.from(merged);
    });
  };

  const toggleStockSelection = (id) => {
    setSelectedStocks(prev =>
      prev.includes(id) ? prev.filter(existingId => existingId !== id) : [...prev, id]
    );
  };

  const clearSelectedStocks = () => setSelectedStocks([]);
  const clearSelectedTransfers = () => setSelectedTransfers([]);

  const toggleTransferSelection = (id) => {
    setSelectedTransfers(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    );
  };

  const toggleSelectAllVisibleTransfers = () => {
    const allVisibleTransferIds = transfers.map(t => t.id);
    const allSelected = allVisibleTransferIds.every(id => selectedTransfers.includes(id));
    if (allSelected) {
      setSelectedTransfers(prev => prev.filter(id => !allVisibleTransferIds.includes(id)));
    } else {
      setSelectedTransfers(prev => [...new Set([...prev, ...allVisibleTransferIds])]);
    }
  };

  const areAllVisibleTransfersSelected = transfers.length > 0 && transfers.every(t => selectedTransfers.includes(t.id));
  const selectedTransferCount = selectedTransfers.length;

  const showSuccessToast = (message) => {
    setSuccessToast({ show: true, message });
    if (toastTimeoutRef.current) {
      clearTimeout(toastTimeoutRef.current);
    }
    toastTimeoutRef.current = setTimeout(() => {
      setSuccessToast({ show: false, message: '' });
    }, 4000);
  };

  useEffect(() => {
    return () => {
      if (toastTimeoutRef.current) {
        clearTimeout(toastTimeoutRef.current);
      }
    };
  }, []);



  const handleCreateStock = () => {
    setSelectedStock(null);
    setShowForm(true);
  };

  const handleQuickAdd = (stock) => {
    setQuickAdjustModal({ show: true, stock, type: 'add' });
  };

  const handleQuickRemove = (stock) => {
    setQuickAdjustModal({ show: true, stock, type: 'remove' });
  };

  const handleStockSettings = (stock) => {
    setSelectedStock(stock);
    setShowSettingsModal(true);
  };

  const handleDeleteStock = async (id) => {
    setConfirmModal({
      show: true,
      title: 'Stok Kaydını Sil',
      message: 'Bu stok kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.',
      confirmText: 'Evet, Sil',
      confirmVariant: 'danger',
      icon: 'trash',
      onConfirm: async () => {
        setConfirmModal({ show: false });
        try {
          await axios.delete(`/api/stocks/${id}`);
          showSuccessToast('Stok kaydı silindi.');
        } catch (error) {
          const errorData = error?.response?.data;
          const msg = errorData?.message || errorData?.error || 'Beklenmeyen bir durum oluştu';
          setErrorModal({
            show: true,
            title: 'Stok Silme Hatası',
            message: `Stok silinirken hata oluştu: ${msg}`
          });
        } finally {
          // Hata olsa bile, kısmen silinmiş stoklar varsa listeyi yenile
          await Promise.all([fetchAllData(), fetchStocks(stockPage)]);
        }
      }
    });
  };

  const handleBatchDeleteStocks = (ids) => {
    if (!ids || ids.length === 0) {
      return;
    }
    setConfirmModal({
      show: true,
      title: 'Seçili Stokları Sil',
      message: `${ids.length} stok kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
      confirmText: 'Evet, Sil',
      confirmVariant: 'danger',
      icon: 'trash',
      onConfirm: async () => {
        setConfirmModal({ show: false });
        
        try {
          // Backend'den toplu silme endpoint'ini kullan
          const response = await axios.delete('/api/stocks/bulk', { data: ids });
          const result = response.data;
          
          // Başarılı silinen stoklar için toast göster
          if (result.successCount > 0) {
            showSuccessToast(`${result.successCount} stok kaydı başarıyla silindi.`);
          }
          
          // Hata alan stoklar varsa detaylı hata listesi göster
          if (result.errors && result.errors.length > 0) {
            const formattedErrors = result.errors.map(err => ({
              stockId: err.id,
              stockInfo: err.name || `Stok #${err.id}`,
              error: err.errorMessage || 'Bilinmeyen hata',
              errorCode: err.errorCode || null,
              sku: err.sku || null
            }));
            
            setErrorDetailsModal({
              show: true,
              title: 'Silinemeyen Stoklar',
              errors: formattedErrors
            });
          }
          
          // Seçili stokları temizle (sadece başarıyla silinenleri kaldır)
          const errorIds = new Set((result.errors || []).map(err => err.id));
          setSelectedStocks(prev => prev.filter(id => {
            // Eğer bu ID silinmeye çalışılan ID'ler arasındaysa
            if (ids.includes(id)) {
              // Hata alan stokları seçimde tut
              return errorIds.has(id);
            }
            // Diğer stokları olduğu gibi tut
            return true;
          }));
        } catch (error) {
          // Backend hatası (örneğin network hatası)
          const errorData = error?.response?.data;
          const msg = errorData?.message || errorData?.error || error.message || 'Stoklar silinirken hata oluştu';
          setErrorModal({
            show: true,
            title: 'Toplu Silme Hatası',
            message: msg
          });
        } finally {
          // Listeyi yenile
          await Promise.all([fetchAllData(), fetchStocks(stockPage)]);
        }
      }
    });
  };

  const handleBatchDeleteTransfers = (ids) => {
    if (!ids || ids.length === 0) {
      return;
    }

    setConfirmModal({
      show: true,
      title: 'Seçili Transferleri Sil',
      message: `${ids.length} transferi silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
      confirmText: 'Evet, Sil',
      confirmVariant: 'danger',
      icon: 'trash',
      onConfirm: async () => {
        setConfirmModal({ show: false });
        
        try {
          // Backend'den toplu silme endpoint'ini kullan
          const response = await axios.delete('/api/stock-transfers/bulk', { data: ids });
          const result = response.data;
          
          // Başarılı silinen transferler için toast göster
          if (result.successCount > 0) {
            showSuccessToast(`${result.successCount} transfer başarıyla silindi.`);
          }
          
          // Hata alan transferler varsa detaylı hata listesi göster
          if (result.errors && result.errors.length > 0) {
            const formattedErrors = result.errors.map(err => ({
              transferId: err.id,
              transferInfo: err.name || `Transfer #${err.id}`,
              error: err.errorMessage || 'Bilinmeyen hata',
              errorCode: err.errorCode || null
            }));
            
            setErrorDetailsModal({
              show: true,
              title: 'Silinemeyen Transferler',
              errors: formattedErrors
            });
          }
          
          // Seçili transferleri temizle (sadece başarıyla silinenleri kaldır)
          const errorIds = new Set((result.errors || []).map(err => err.id));
          setSelectedTransfers(prev => prev.filter(id => {
            // Eğer bu ID silinmeye çalışılan ID'ler arasındaysa
            if (ids.includes(id)) {
              // Hata alan transferleri seçimde tut
              return errorIds.has(id);
            }
            // Diğer transferleri olduğu gibi tut
            return true;
          }));
        } catch (error) {
          // Backend hatası (örneğin network hatası)
          const errorData = error?.response?.data;
          const msg = errorData?.message || errorData?.error || error.message || 'Transferler silinirken hata oluştu';
          setErrorModal({
            show: true,
            title: 'Toplu Silme Hatası',
            message: msg
          });
        } finally {
          // Listeyi yenile
          await fetchTransfers(0, false);
        }
      }
    });
  };

  const handleFormSuccess = async (options = {}) => {
    const shouldClose = options.close !== false;
    if (shouldClose) {
      setShowForm(false);
    }
    await Promise.all([fetchAllData(), fetchStocks(0)]);
    if (options.message) {
      showSuccessToast(options.message);
    }
  };

  const handleQuickAdjustSuccess = async () => {
    setQuickAdjustModal({ show: false, stock: null, type: null });
    await Promise.all([fetchAllData(), fetchStocks(stockPage)]);
  };

  const handleSettingsSuccess = async () => {
    setShowSettingsModal(false);
    setSelectedStock(null);
    await Promise.all([fetchAllData(), fetchStocks(stockPage)]);
  };

  const handleStockTransfer = (stock, lockToCustomer = false) => {
    setSelectedStock(stock);
    setLockCustomerTransfer(lockToCustomer);
    setShowTransferModal(true);
  };

  const handleTransferSuccess = async () => {
    setShowTransferModal(false);
    setSelectedStock(null);
    setLockCustomerTransfer(false);
    await Promise.all([fetchAllData(), fetchStocks(stockPage)]);
    if (showTransferHistory) {
      fetchTransfers(0, false);
    }
  };

  const handleShowTransferHistory = () => {
    setShowTransferHistory(!showTransferHistory);
  };

  const handleTransferStatusChange = async (transferId, action, payload = undefined) => {
    try {
      const body = payload && Object.keys(payload).length > 0 ? payload : undefined;
      const response = await axios.post(`/api/stock-transfers/${transferId}/${action}`, body);
      const updatedTransfer = response?.data;
      await Promise.all([fetchTransfers(0, false), fetchAllData(), fetchStocks(stockPage)]);
      if (action === 'start' && updatedTransfer?.approvalStatus === 'PENDING') {
        setErrorModal({
          show: true,
          title: 'Onay Talebi Oluşturuldu',
          message: 'Transferi başlatmak için yönetici onayı bekleniyor.'
        });
      }
    } catch (error) {
      const errorData = error?.response?.data;
      let errorMessage = 'Beklenmeyen bir durum oluştu';

      if (errorData) {
        if (errorData.message) {
          errorMessage = errorData.message;
        } else if (errorData.error) {
          errorMessage = errorData.error;
        } else if (typeof errorData === 'string') {
          errorMessage = errorData;
        }
      }

      const actionNames = {
        'cancel': 'iptal',
        'start': 'başlatma',
        'complete': 'tamamlama'
      };
      const actionName = actionNames[action] || action;

      setErrorModal({
        show: true,
        title: 'Transfer İşlemi Hatası',
        message: `Transfer ${actionName} işlemi sırasında hata oluştu: ${errorMessage}`
      });
    }
  };

  const handleDeleteTransfer = async (transferId) => {
    setConfirmModal({
      show: true,
      title: 'Transfer Kaydını Sil',
      message: 'Bu transfer kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.',
      confirmText: 'Evet, Sil',
      confirmVariant: 'danger',
      icon: 'trash',
      onConfirm: async () => {
        setConfirmModal({ show: false });
        try {
          await axios.delete(`/api/stock-transfers/${transferId}`);
          showSuccessToast('Transfer kaydı silindi.');
        } catch (error) {
          const errorData = error?.response?.data;
          const msg = errorData?.message || errorData?.error || error.message || 'Beklenmeyen bir durum oluştu';
          setErrorModal({
            show: true,
            title: 'Transfer Silme Hatası',
            message: `Transfer silinirken hata oluştu: ${msg}`
          });
        } finally {
          // Hata olsa bile, kısmen silinmiş olabilir; transfer listesini yenile
          await fetchTransfers(0, false);
        }
      }
    });
  };

  const openCompletionFlow = (transfer, message) => {
    if ((transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY') {
      setCompletionModal({
        show: true,
        transferId: transfer.id,
        note: '',
        message,
        transfer
      });
      return;
    }

    setConfirmModal({
      show: true,
      title: 'Transferi Tamamla',
      message,
      confirmText: 'Evet, Tamamla',
      confirmVariant: 'success',
      icon: 'check-circle',
      onConfirm: () => {
        setConfirmModal({ show: false });
        handleTransferStatusChange(transfer.id, 'complete');
      }
    });
  };

  const getProductById = (id) => {
    if (!Array.isArray(products)) return null;
    return products.find(p => p.id === id);
  };

  const getWarehouseById = (id) => {
    return warehouses.find(w => w.id === id);
  };

  const getTransferItemsList = (transfer) => {
    if (Array.isArray(transfer.items) && transfer.items.length > 0) {
      return transfer.items;
    }
    if (transfer.product) {
      return [{
        product: transfer.product,
        quantity: transfer.quantity
      }];
    }
    return [];
  };

  const getTransferTotalQuantity = (transfer) => {
    if (typeof transfer.totalQuantity === 'number' && transfer.totalQuantity > 0) {
      return transfer.totalQuantity;
    }
    return getTransferItemsList(transfer).reduce((sum, item) => sum + (item.quantity || 0), 0);
  };

  const getStatusCount = (status) => {
    if (transferStatusCounts && Object.prototype.hasOwnProperty.call(transferStatusCounts, status)) {
      return transferStatusCounts[status];
    }
    return transfers.filter(t => t.status === status).length;
  };

  const getTransferTypeCount = (type) => {
    if (transferTypeCounts && Object.prototype.hasOwnProperty.call(transferTypeCounts, type)) {
      return transferTypeCounts[type];
    }
    if (type === 'CUSTOMER_DELIVERY') {
      return transfers.filter(t => t.transferType === 'CUSTOMER_DELIVERY').length;
    }
    return transfers.filter(t => (t.transferType || 'WAREHOUSE') === 'WAREHOUSE').length;
  };

  const getStockStatus = (stock) => {
    const available = (stock.quantity || 0) - (stock.reservedQuantity || 0) - (stock.consignedQuantity || 0);
    if (available <= 0) return { status: 'out', label: 'Stok Dışı', class: 'danger' };
    if (available <= getEffectiveMin(stock)) return { status: 'low', label: 'Düşük Stok', class: 'warning' };
    return { status: 'normal', label: 'Normal', class: 'success' };
  };

  const totalTransfers = typeof transferTotalCount === 'number' ? transferTotalCount : transfers.length;
  const warehouseTransferCount = getTransferTypeCount('WAREHOUSE');
  const customerTransferCount = getTransferTypeCount('CUSTOMER_DELIVERY');

  if (loading) {
    return (
      <div className="text-center">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Yükleniyor...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="alert alert-danger" role="alert">
        {error}
      </div>
    );
  }

  return (
    <div>
      <style>{`
        .stock-page-header {
          gap: 1.5rem;
        }
        .stock-page-header h2 {
          font-weight: 600;
          letter-spacing: -0.02em;
        }
        .stock-page-header p {
          color: #94a3b8;
          margin: 0;
        }
        .stock-page-actions {
          display: flex;
          flex-wrap: wrap;
          gap: 0.75rem;
        }
        @media (max-width: 1155px) {
          .stock-page-actions .btn {
            width: 100%;
          }
        }
        .table-actions {
          white-space: nowrap;
        }
        .table-actions .btn-group {
          gap: 0.125rem;
        }
        .table-actions .btn {
          min-width: 28px;
          padding: 0.25rem 0.375rem;
          font-size: 0.75rem;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .table-actions .btn i {
          margin: 0;
          line-height: 1;
        }
        @media (max-width: 1400px) {
          .table-actions .btn {
            min-width: 26px;
            padding: 0.2rem 0.3rem;
            font-size: 0.7rem;
          }
        }
        @media (max-width: 1200px) {
          .table-actions .btn {
            min-width: 24px;
            padding: 0.175rem 0.25rem;
            font-size: 0.65rem;
          }
        }
        .breakpoint-1155-desktop table {
          font-size: 0.9rem;
        }
        @media (max-width: 1400px) {
          .breakpoint-1155-desktop table {
            font-size: 0.85rem;
          }
        }
        @media (max-width: 1200px) {
          .breakpoint-1155-desktop table {
            font-size: 0.8rem;
          }
        }
      `}</style>
      <div className="stock-page-header d-flex flex-column flex-lg-row align-items-lg-center justify-content-between mb-4">
        <div>
          <h2 className="mb-1">
            <i className="fas fa-boxes me-2"></i>
            Stok Yönetimi
          </h2>
          <p>Depo stoklarını yönet, transferleri takip et ve onay süreçlerini kontrol et.</p>
        </div>
        <div className="stock-page-actions w-100 w-lg-auto justify-content-lg-end">
          {isAdmin ? (
            <div className="d-flex flex-wrap gap-2 justify-content-lg-end w-100">
              <button className="btn btn-outline-success" onClick={async () => {
                try {
                  const res = await axios.get('/api/stock-imports/template', { responseType: 'blob' });
                  const url = window.URL.createObjectURL(new Blob([res.data]));
                  const link = document.createElement('a');
                  link.href = url;
                  link.setAttribute('download', 'stok_sablon.xlsx');
                  document.body.appendChild(link);
                  link.click();
                  link.remove();
                  window.URL.revokeObjectURL(url);
                } catch (e) {
                  setError('Excel şablonu indirilirken hata oluştu');
                }
              }}>
                <i className="fas fa-download me-2"></i>
                Excel Şablonunu İndir
              </button>
              <button className="btn btn-outline-primary" onClick={() => { setExcelResult(null); setExcelFile(null); setExcelWarehouseId(null); setShowExcelModal(true); }}>
                <i className="fas fa-file-import me-2"></i>
                Excel'den Yükle
              </button>
              <button
                className="btn btn-warning position-relative"
                onClick={() => { setApprovalModalTab('stock'); setShowApprovalModal(true); }}
                title="Stok talep onayları"
                style={{ zIndex: 1 }}
              >
                <i className="fas fa-tasks me-2"></i>
                Talepler
                {pendingRequestsCount > 0 && (
                  <span
                    className="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger"
                    style={{ zIndex: 10 }}
                  >
                    {pendingRequestsCount}
                    <span className="visually-hidden">bekleyen talepler</span>
                  </span>
                )}
              </button>
              <button className="btn btn-primary" onClick={handleCreateStock}>
                <i className="fas fa-plus me-2"></i>
                Yeni Stok Kaydı
              </button>
              <button className="btn btn-success" onClick={handleShowTransferHistory}>
                <i className={`fas fa-${showTransferHistory ? 'cubes' : 'exchange-alt'} me-2`}></i>
                {showTransferHistory ? 'Stok Listesi' : 'Transfer Geçmişi'}
              </button>
            </div>
          ) : (
            <div className="d-flex flex-column flex-lg-row align-items-lg-center gap-2 w-100">
              <div className="alert alert-info mb-0 py-2 px-3 flex-grow-1">
                <i className="fas fa-info-circle me-2"></i>
                <span>
                  {role === 'STOCK_IN' && 'Stok ekleme talepleri oluşturabilir, müşteri sevkiyat transferlerinizi görüntüleyebilirsiniz.'}
                  {role === 'STOCK_OUT' && 'Stok çıkarma talepleri oluşturabilir, müşteri sevkiyat transferlerinizi görüntüleyebilirsiniz.'}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center px-3 py-2 flex-wrap gap-2">
                <small className="text-muted">
                  Gösterilen kayıt: {transfers.length}/{totalTransfers || transfers.length}
                </small>
                <PaginationControls
                  page={transferPage}
                  totalPages={transferTotalPages}
                  onPageChange={handleTransferPageChange}
                />
              </div>
              <div className="d-flex flex-wrap gap-2 justify-content-lg-end">
                <button className="btn btn-success" onClick={handleShowTransferHistory}>
                  <i className={`fas fa-${showTransferHistory ? 'cubes' : 'exchange-alt'} me-2`}></i>
                  {showTransferHistory ? 'Stok Listesi' : 'Transferlerim'}
                </button>
                <button
                  className="btn btn-outline-primary"
                  onClick={() => setShowMyRequestsModal(true)}
                >
                  <i className="fas fa-clipboard-list me-2"></i>
                  Taleplerim
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Filters Bar - Only show when not in transfer history mode */}
      {!showTransferHistory && (
        <StockFiltersBar
          searchTerm={searchTerm}
          setSearchTerm={setSearchTerm}
          selectedWarehouseId={selectedWarehouseId}
          setSelectedWarehouseId={setSelectedWarehouseId}
          selectedWarehouseOpt={selectedWarehouseOpt}
          setSelectedWarehouseOpt={setSelectedWarehouseOpt}
          brandId={brandId}
          setBrandId={setBrandId}
          brandOpt={brandOpt}
          setBrandOpt={setBrandOpt}
          colorId={colorId}
          setColorId={setColorId}
          colorOpt={colorOpt}
          setColorOpt={setColorOpt}
          categories={categories}
          subcategories={subcategories}
          setSubcategories={setSubcategories}
          selectedCategory={selectedCategory}
          setSelectedCategory={setSelectedCategory}
          selectedSubcategory={selectedSubcategory}
          setSelectedSubcategory={setSelectedSubcategory}
          showReserved={showReserved}
          setShowReserved={setShowReserved}
          showConsigned={showConsigned}
          setShowConsigned={setShowConsigned}
          getWarehouseById={getWarehouseById}
        />
      )}

      {successToast.show && (
        <div className="toast-container position-fixed top-0 end-0 p-3" style={{ zIndex: 2000 }}>
          <div className="toast show text-bg-success border-0 shadow" role="alert">
            <div className="d-flex align-items-center">
              <div className="toast-body">
                <i className="fas fa-check-circle me-2"></i>
                {successToast.message}
              </div>
              <button
                type="button"
                className="btn-close btn-close-white me-2 m-auto"
                onClick={() => setSuccessToast({ show: false, message: '' })}
              ></button>
            </div>
          </div>
        </div>
      )}

      {/* Filter Tabs */}
      {!showTransferHistory && (
        <div className="mb-4">
          <div className="btn-group" role="group">
            <input
              type="radio"
              className="btn-check"
              name="filter"
              id="all"
              value="all"
              checked={filter === 'all'}
              onChange={(e) => setFilter(e.target.value)}
            />
            <label className="btn btn-outline-primary" htmlFor="all">
              Tüm Stok ({stocks.length})
            </label>

            <input
              type="radio"
              className="btn-check"
              name="filter"
              id="low-stock"
              value="low-stock"
              checked={filter === 'low-stock'}
              onChange={(e) => setFilter(e.target.value)}
            />
            <label className="btn btn-outline-warning" htmlFor="low-stock">
              Düşük Stok ({stocks.filter(s => s.quantity <= getEffectiveMin(s) && s.quantity > 0).length})
            </label>

            <input
              type="radio"
              className="btn-check"
              name="filter"
              id="out-of-stock"
              value="out-of-stock"
              checked={filter === 'out-of-stock'}
              onChange={(e) => setFilter(e.target.value)}
            />
            <label className="btn btn-outline-danger" htmlFor="out-of-stock">
              Stok Dışı ({stocks.filter(s => s.quantity === 0).length})
            </label>
          </div>
        </div>
      )}

      {showExcelModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Excel'den Stok Yükle</h5>
                <button type="button" className="btn-close" onClick={() => setShowExcelModal(false)}></button>
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label">Depo Seç</label>
                  <select className="form-select" value={excelWarehouseId || ''} onChange={(e) => setExcelWarehouseId(e.target.value ? parseInt(e.target.value) : null)}>
                    <option value="">Depo seçiniz...</option>
                    {warehouses.map(w => (
                      <option key={w.id} value={w.id}>{w.name}</option>
                    ))}
                  </select>
                </div>
                <div className="mb-3">
                  <label className="form-label">Excel Dosyası (.xlsx)</label>
                  <input type="file" accept=".xlsx" className="form-control" onChange={(e) => setExcelFile(e.target.files && e.target.files[0] ? e.target.files[0] : null)} />
                </div>
                {excelResult && (
                  <div className={`alert alert-${(excelResult.status === 'SUCCESS' || excelResult.status === 'BAŞARILI') ? 'success' : ((excelResult.status === 'FAILED' || excelResult.status === 'BAŞARISIZ') ? 'danger' : 'warning')}`}>
                    <div className="fw-bold mb-1">{(excelResult.status === 'SUCCESS' || excelResult.status === 'BAŞARILI') ? 'Aktarım başarılı' : ((excelResult.status === 'FAILED' || excelResult.status === 'BAŞARISIZ') ? 'Aktarım başarısız' : 'Kısmen başarılı')}</div>
                    <div>Satır: {excelResult.totalRows ?? '-'}</div>
                    <div>Ürün (Yeni): {(excelResult.createdProducts ?? 0)}</div>
                    <div>Stok (Yeni/Güncellenen): {(excelResult.createdStocks ?? 0)} / {(excelResult.updatedStocks ?? 0)}</div>
                    {excelResult.errorMessage && (<div className="mt-2 text-danger small">Hata: {excelResult.errorMessage}</div>)}
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowExcelModal(false)} disabled={excelUploading}>Kapat</button>
                <button type="button" className="btn btn-primary" disabled={!excelWarehouseId || !excelFile || excelUploading} onClick={async () => {
                  try {
                    setExcelUploading(true);
                    setExcelResult(null);
                    const form = new FormData();
                    form.append('warehouseId', String(excelWarehouseId));
                    form.append('file', excelFile);
                    const res = await axios.post('/api/stock-imports/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } });
                    setExcelResult(res.data);
                    // Başarılı yükleme sonrası tüm verileri yenile
                    if (res.data && (res.data.status === 'BAŞARILI' || res.data.status === 'SUCCESS' || res.data.status === 'KISMEN' || res.data.status === 'PARTIAL')) {
                      await Promise.all([fetchAllData(), fetchStocks(0)]);
                    }
                  } catch (e) {
                    const data = e?.response?.data;
                    setExcelResult({ status: 'FAILED', errorMessage: typeof data === 'string' ? data : (data?.message || 'Yükleme hatası') });
                  } finally {
                    setExcelUploading(false);
                  }
                }}>
                  {excelUploading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                      Yükleniyor...
                    </>
                  ) : (
                    <>
                      <i className="fas fa-upload me-2"></i>
                      Yükle
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
      {!showTransferHistory && stocks.length > 0 && (
        <div className="mobile-selection-toolbar d-lg-none mb-3">
          <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
            <div className="fw-semibold">
              Seçili stok: {selectedStockCount}
            </div>
            <div className="d-flex flex-wrap gap-2">
              <button
                type="button"
                className="btn btn-sm btn-outline-primary"
                onClick={toggleSelectAllVisible}
              >
                {areAllVisibleSelected ? 'Seçimi Kaldır' : 'Tümünü Seç'}
              </button>
              {selectedStockCount > 0 && (
                <>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary"
                    onClick={clearSelectedStocks}
                  >
                    Seçimi Temizle
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-danger"
                    onClick={() => handleBatchDeleteStocks([...selectedStocks])}
                  >
                    Seçilileri Sil
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Stock Table */}
      {!showTransferHistory && (
        <div className="card">
          <div className="card-body">
            <div className="d-flex justify-content-end align-items-center mb-2 gap-2 flex-wrap">
              <div className="page-size-control d-flex align-items-center flex-wrap">
                <span className="form-label mb-0 small text-muted">Sayfa Boyutu</span>
                <select
                  className="form-select form-select-sm page-size-select"
                  value={stockPageSize}
                  onChange={handleStockPageSizeChange}
                >
                  {PAGE_SIZE_OPTIONS.map(size => (
                    <option key={`stock-page-${size}`} value={size}>{size}</option>
                  ))}
                </select>
              </div>
            </div>
            {selectedStockCount > 0 && (
              <div className="alert alert-warning d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
                <div className="fw-semibold">
                  <i className="fas fa-check-square me-2"></i>
                  {selectedStockCount} stok seçildi
                </div>
                <div className="d-flex gap-2 flex-wrap">
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary"
                    onClick={clearSelectedStocks}
                  >
                    Seçimi Temizle
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-danger"
                    onClick={() => handleBatchDeleteStocks([...selectedStocks])}
                  >
                    <i className="fas fa-trash me-1"></i>
                    Seçilileri Sil
                  </button>
                </div>
              </div>
            )}
            {/* Desktop Table View */}
            <div className="breakpoint-1155-desktop table-responsive" style={{ transition: 'opacity 0.3s ease-in-out' }}>
              <table className="table table-striped table-hover" style={{ transition: 'opacity 0.3s ease-in-out' }}>
                <thead>
                  <tr>
                    <th className="text-center" style={{ width: '40px' }}>
                      <div className="form-check mb-0">
                        <input
                          className="form-check-input"
                          type="checkbox"
                          checked={areAllVisibleSelected}
                          onChange={toggleSelectAllVisible}
                          disabled={stocks.length === 0}
                          aria-label="Tümünü seç"
                        />
                      </div>
                    </th>
                    <th>Depo</th>
                    <th>Ürün</th>
                    <th className="d-none d-md-table-cell">Stok Kodu</th>
                    <th>Miktar</th>
                    <th className="d-none d-lg-table-cell">Kullanılabilir</th>
                    <th className="d-none d-xl-table-cell">Rezerve</th>
                    <th className="d-none d-xl-table-cell">Emanet</th>
                    <th className="d-none d-md-table-cell" style={{ minWidth: '120px' }}>Müşteri</th>
                    <th className="d-none d-xl-table-cell">Min. Stok</th>
                    <th>Durum</th>
                    <th className="text-center d-none d-md-table-cell">
                      <button
                        type="button"
                        className="btn btn-link p-0 text-decoration-none"
                        onClick={() => {
                          if (stockSortBy === 'lastUpdated') {
                            setStockSortDir(stockSortDir === 'asc' ? 'desc' : 'asc');
                          } else {
                            setStockSortBy('lastUpdated');
                            setStockSortDir('desc');
                          }
                        }}
                        title={`Son Güncelleme göre sırala (${stockSortBy === 'lastUpdated' ? (stockSortDir === 'asc' ? 'Artan' : 'Azalan') : 'Kapalı'})`}
                      >
                        Son Güncelleme
                        <i
                          className={`fas ms-1 ${stockSortBy === 'lastUpdated' ? (stockSortDir === 'asc' ? 'fa-sort-up' : 'fa-sort-down') : 'fa-sort'}`}
                          aria-hidden="true"
                        ></i>
                      </button>
                    </th>
                    <th className="table-actions text-center" style={{ minWidth: '140px' }}>İşlemler</th>
                  </tr>
                </thead>
                <tbody>
                  {stocks.map((stock) => {
                    // Use product from stock directly (it comes from backend with name and sku)
                    // stock.product already contains name and sku from backend
                    const productName = stock.product?.name || (getProductById(stock.product?.id)?.name);
                    const productSku = stock.product?.sku || (getProductById(stock.product?.id)?.sku);
                    const warehouse = stock.warehouse || getWarehouseById(stock.warehouse?.id);
                    const stockStatus = getStockStatus(stock);
                    // Try to get category from product if available, otherwise use getProductById
                    const productWithCategory = getProductById(stock.product?.id);
                    const categoryPath = productWithCategory?.category ? `${productWithCategory.category.parentName ? productWithCategory.category.parentName + ' > ' : ''}${productWithCategory.category.name}` : null;
                    const isSelected = selectedStocks.includes(stock.id);

                    return (
                      <tr key={stock.id} className={isSelected ? 'table-active' : ''}>
                        <td className="text-center align-middle">
                          <div className="form-check mb-0">
                            <input
                              className="form-check-input"
                              type="checkbox"
                              checked={isSelected}
                              onChange={() => toggleStockSelection(stock.id)}
                              aria-label="Stok seç"
                            />
                          </div>
                        </td>
                        <td>{warehouse?.name || '-'}</td>
                        <td>
                          <div className="fw-semibold">{productName || '-'}</div>
                          <small className="text-muted d-md-none">{productSku || '-'}</small>
                          {stock.customerName && (
                            <small 
                              className="badge bg-info bg-opacity-10 text-info border border-info d-lg-none mt-1 d-inline-block"
                              title={stock.customerName}
                              style={{ fontSize: '0.7rem' }}
                            >
                              <i className="fas fa-user me-1"></i>
                              {stock.customerName}
                            </small>
                          )}
                          {categoryPath && (
                            <small className="text-muted d-block">
                              <i className="fas fa-tag me-1"></i>
                              {categoryPath}
                            </small>
                          )}
                          {stock.additionNote && (
                            <small
                              className="text-muted fst-italic d-block mt-1 text-truncate"
                              title={stock.additionNote}
                            >
                              <i className="fas fa-sticky-note me-1"></i>
                              {stock.additionNote}
                            </small>
                          )}
                        </td>
                        <td className="d-none d-md-table-cell">{productSku || '-'}</td>
                        <td>
                          <span className="fw-bold">{stock.quantity}</span>
                          <small className="text-muted d-lg-none d-block">
                            Kullanılabilir: <span className={stock.availableQuantity < getEffectiveMin(stock) ? 'text-danger' : 'text-success'}>
                              {stock.availableQuantity}
                            </span>
                          </small>
                        </td>
                        <td className="d-none d-lg-table-cell">
                          <span className={stock.availableQuantity < getEffectiveMin(stock) ? 'text-danger' : 'text-success'}>
                            {stock.availableQuantity}
                          </span>
                        </td>
                        <td className="d-none d-xl-table-cell">
                          <span className={stock.reservedQuantity > 0 ? 'text-warning fw-bold' : 'text-muted'}>
                            <i className="fas fa-lock me-1" style={{ fontSize: '0.75rem' }}></i>
                            {stock.reservedQuantity || 0}
                          </span>
                        </td>
                        <td className="d-none d-xl-table-cell">{stock.consignedQuantity || 0}</td>
                        <td className="d-none d-md-table-cell">
                          {stock.customerName ? (
                            <div style={{ maxWidth: '150px' }}>
                              <span 
                                className="badge bg-info bg-opacity-10 text-info border border-info mb-1 d-block" 
                                title={stock.customerName + (stock.customerPhone ? `\nTel: ${formatPhoneForDisplay(stock.customerPhone)}` : '')}
                                style={{ fontSize: '0.75rem', whiteSpace: 'normal', wordWrap: 'break-word', lineHeight: '1.3' }}
                              >
                                <i className="fas fa-user me-1"></i>
                                {stock.customerName}
                              </span>
                              {stock.customerPhone && (
                                <small className="text-muted d-block" style={{ fontSize: '0.7rem', whiteSpace: 'normal', wordWrap: 'break-word' }} title={formatPhoneForDisplay(stock.customerPhone)}>
                                  <i className="fas fa-phone me-1"></i>
                                  {formatPhoneForDisplay(stock.customerPhone)}
                                </small>
                              )}
                            </div>
                          ) : (
                            <span className="text-muted">-</span>
                          )}
                        </td>
                        <td className="d-none d-xl-table-cell">{stock.minStockLevel}</td>
                        <td>
                          <span className={`badge bg-${stockStatus.class}`}>
                            {stockStatus.label}
                          </span>
                        </td>
                        <td className="d-none d-md-table-cell">
                          <div className="text-nowrap text-center">
                            {formatDateInTurkeyTimezone(stock.lastUpdated, { year: 'numeric', month: '2-digit', day: '2-digit' })}
                          </div>
                          <small className="text-muted text-nowrap d-block text-center">
                            {formatDateInTurkeyTimezone(stock.lastUpdated, { hour: '2-digit', minute: '2-digit' })}
                          </small>
                        </td>
                        <td className="table-actions">
                          <div className="btn-group btn-group-sm" role="group" style={{ flexWrap: 'nowrap' }}>
                            {(role === 'ADMIN' || role === 'STOCK_IN') && (
                              <button
                                className="btn btn-sm btn-success"
                                onClick={() => handleQuickAdd(stock)}
                                title="Hızlı Stok Ekle"
                                style={{ padding: '0.25rem 0.375rem', fontSize: '0.75rem' }}
                              >
                                <i className="fas fa-plus"></i>
                              </button>
                            )}
                            {(role === 'ADMIN' || role === 'STOCK_OUT') && (
                              <button
                                className="btn btn-sm btn-danger"
                                onClick={() => handleQuickRemove(stock)}
                                title="Hızlı Stok Çıkar"
                                style={{ padding: '0.25rem 0.375rem', fontSize: '0.75rem' }}
                              >
                                <i className="fas fa-minus"></i>
                              </button>
                            )}
                            <button
                              className="btn btn-sm btn-outline-primary"
                              onClick={() => handleStockSettings(stock)}
                              title={role === 'ADMIN' ? 'Ayarlar (Emanet, Min Stok)' : 'Stok Detayları'}
                              style={{ padding: '0.25rem 0.375rem', fontSize: '0.75rem' }}
                            >
                              <i className="fas fa-cog"></i>
                            </button>
                            {canTransfer && (
                              <button
                                className="btn btn-sm btn-outline-success d-none d-lg-inline-flex"
                                onClick={() => handleStockTransfer(stock, role !== 'ADMIN')}
                                title="Transfer Yap"
                                style={{ padding: '0.25rem 0.375rem', fontSize: '0.75rem' }}
                              >
                                <i className="fas fa-exchange-alt"></i>
                              </button>
                            )}
                            {role === 'ADMIN' && (
                              <>
                                <button
                                  className="btn btn-sm btn-outline-danger d-none d-xl-inline-flex"
                                  onClick={() => handleDeleteStock(stock.id)}
                                  title="Stok Kaydını Sil"
                                  style={{ padding: '0.25rem 0.375rem', fontSize: '0.75rem' }}
                                >
                                  <i className="fas fa-trash"></i>
                                </button>
                                <button
                                  className="btn btn-sm btn-outline-secondary d-none d-xl-inline-flex"
                                  onClick={() => setAuditModal({ show: true, entityType: 'Stock', entityId: stock.id })}
                                  title="Hareket Geçmişi"
                                  style={{ padding: '0.25rem 0.375rem', fontSize: '0.75rem' }}
                                >
                                  <i className="fas fa-history"></i>
                                </button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Mobile Card View */}
            <div className="breakpoint-1155-mobile" style={{ transition: 'opacity 0.3s ease-in-out' }}>
              <div className="d-flex flex-column gap-3" style={{ transition: 'opacity 0.3s ease-in-out' }}>
                {stocks.map((stock) => {
                  const productName = stock.product?.name || (getProductById(stock.product?.id)?.name);
                  const productSku = stock.product?.sku || (getProductById(stock.product?.id)?.sku);
                  const warehouse = stock.warehouse || getWarehouseById(stock.warehouse?.id);
                  const stockStatus = getStockStatus(stock);
                  const productWithCategory = getProductById(stock.product?.id);
                  const categoryPath = productWithCategory?.category
                    ? `${productWithCategory.category.parentName ? productWithCategory.category.parentName + ' > ' : ''}${productWithCategory.category.name}`
                    : null;
                  const isSelected = selectedStocks.includes(stock.id);
                  const availableIsLow = stock.availableQuantity < getEffectiveMin(stock);

                  return (
                    <div
                      key={stock.id}
                      className={`stock-mobile-card card border-0 shadow-sm ${isSelected ? 'is-selected' : ''}`}
                    >
                      <div className="card-body p-3">
                        <div className="stock-mobile-card__header mb-3">
                          <div className="flex-grow-1">
                            <div className="stock-mobile-card__warehouse">{warehouse?.name || '-'}</div>
                            <div className="stock-mobile-card__title">{productName || '-'}</div>
                          </div>
                          <div className="d-flex align-items-center gap-2 flex-nowrap">
                            {isAdmin && (
                              <label className="mobile-card-checkbox-wrapper mb-0">
                                <input
                                  className="mobile-card-checkbox"
                                  type="checkbox"
                                  checked={isSelected}
                                  onChange={() => toggleStockSelection(stock.id)}
                                  aria-label="Stok seç"
                                />
                              </label>
                            )}
                            <span className={`mobile-chip badge bg-${stockStatus.class}`}>
                              {stockStatus.label}
                            </span>
                          </div>
                        </div>

                        <div className="stock-mobile-card__tags">
                          <div className="stock-mobile-card__tags-left">
                            <span className="mobile-chip">
                              <i className="fas fa-barcode me-1"></i>
                              {productSku || '-'}
                            </span>
                            {categoryPath && (
                              <span className="mobile-chip bg-info bg-opacity-10 text-info border border-info">
                                <i className="fas fa-tag me-1"></i>
                                {categoryPath.length > 24 ? `${categoryPath.substring(0, 24)}…` : categoryPath}
                              </span>
                            )}
                          </div>
                          <div className="stock-mobile-card__tags-right">
                            {stock.customerName && (
                              <>
                                <span 
                                  className="mobile-chip bg-info bg-opacity-10 text-info border border-info"
                                  title={stock.customerName}
                                  style={{ fontSize: '0.7rem' }}
                                >
                                  <i className="fas fa-user me-1"></i>
                                  {stock.customerName}
                                </span>
                                {stock.customerPhone && (
                                  <span 
                                    className="mobile-chip bg-info bg-opacity-10 text-info border border-info"
                                    title={formatPhoneForDisplay(stock.customerPhone)}
                                    style={{ fontSize: '0.7rem' }}
                                  >
                                    <i className="fas fa-phone me-1"></i>
                                    {formatPhoneForDisplay(stock.customerPhone)}
                                  </span>
                                )}
                              </>
                            )}
                            {stock.additionNote && (
                              <span className="mobile-chip mobile-chip-note" title={stock.additionNote}>
                                <i className="fas fa-sticky-note me-1"></i>
                                {stock.additionNote.length > 60 ? `${stock.additionNote.substring(0, 60)}…` : stock.additionNote}
                              </span>
                            )}
                          </div>
                        </div>

                        <div className="row g-2 mb-3 mobile-stat-grid">
                          <div className="col-6">
                            <div className="mobile-stat-tile">
                              <div className="label">
                                <i className="fas fa-cubes me-1"></i>
                                Miktar
                              </div>
                              <div className="value">{stock.quantity}</div>
                            </div>
                          </div>
                          <div className="col-6">
                            <div className={`mobile-stat-tile ${availableIsLow ? 'border-danger bg-danger bg-opacity-10' : ''}`}>
                              <div className="label">
                                <i className="fas fa-check-circle me-1"></i>
                                Kullanılabilir
                              </div>
                              <div className={`value ${availableIsLow ? 'text-danger' : 'text-success'}`}>
                                {stock.availableQuantity}
                              </div>
                            </div>
                          </div>
                          <div className="col-4">
                            <div className="mobile-stat-tile">
                              <div className="label">
                                <i className="fas fa-lock me-1"></i>
                                Rezerve
                              </div>
                              <div className={`value ${stock.reservedQuantity > 0 ? 'text-warning' : 'text-muted'}`}>
                                {stock.reservedQuantity || 0}
                              </div>
                            </div>
                          </div>
                          <div className="col-4">
                            <div className="mobile-stat-tile">
                              <div className="label">
                                <i className="fas fa-handshake me-1"></i>
                                Emanet
                              </div>
                              <div className="value text-muted">{stock.consignedQuantity || 0}</div>
                            </div>
                          </div>
                          <div className="col-4">
                            <div className="mobile-stat-tile">
                              <div className="label">
                                <i className="fas fa-exclamation-triangle me-1"></i>
                                Min. Stok
                              </div>
                              <div className="value text-muted">{stock.minStockLevel || '-'}</div>
                            </div>
                          </div>
                        </div>

                        <div className="stock-mobile-card__footer">
                          <small className="text-muted text-center">
                            <i className="fas fa-clock me-1"></i>
                            {formatDateInTurkeyTimezone(stock.lastUpdated, {
                              year: 'numeric',
                              month: '2-digit',
                              day: '2-digit',
                              hour: '2-digit',
                              minute: '2-digit'
                            })}
                          </small>
                          <div className="stock-mobile-card__actions">
                            {(role === 'ADMIN' || role === 'STOCK_IN') && (
                              <button
                                className="btn btn-success mobile-action-btn"
                                onClick={() => handleQuickAdd(stock)}
                                title="Hızlı Stok Ekle"
                              >
                                <i className="fas fa-plus"></i>
                              </button>
                            )}
                            {(role === 'ADMIN' || role === 'STOCK_OUT') && (
                              <button
                                className="btn btn-danger mobile-action-btn"
                                onClick={() => handleQuickRemove(stock)}
                                title="Hızlı Stok Çıkar"
                              >
                                <i className="fas fa-minus"></i>
                              </button>
                            )}
                            <button
                              className="btn btn-outline-primary mobile-action-btn"
                              onClick={() => handleStockSettings(stock)}
                              title={role === 'ADMIN' ? 'Ayarlar' : 'Detaylar'}
                            >
                              <i className="fas fa-cog"></i>
                            </button>
                            {canTransfer && (
                              <button
                                className="btn btn-outline-success mobile-action-btn"
                                onClick={() => handleStockTransfer(stock, role !== 'ADMIN')}
                                title="Transfer"
                              >
                                <i className="fas fa-exchange-alt"></i>
                              </button>
                            )}
                            {role === 'ADMIN' && (
                              <>
                                <button
                                  className="btn btn-outline-danger mobile-action-btn"
                                  onClick={() => handleDeleteStock(stock.id)}
                                  title="Sil"
                                >
                                  <i className="fas fa-trash"></i>
                                </button>
                                <button
                                  className="btn btn-outline-secondary mobile-action-btn"
                                  onClick={() => setAuditModal({ show: true, entityType: 'Stock', entityId: stock.id })}
                                  title="Geçmiş"
                                >
                                  <i className="fas fa-history"></i>
                                </button>
                              </>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="d-flex justify-content-between align-items-center mt-2 flex-wrap gap-2">
              <small className="text-muted">
                Gösterilen kayıt: {stocks.length}/{totalStockCount || stocks.length}
              </small>
              <PaginationControls
                page={stockPage}
                totalPages={stockTotalPages}
                onPageChange={handleStockPageChange}
              />
            </div>

            {stocks.length === 0 && (
              <div className="text-center py-4">
                <i className="fas fa-cubes fa-3x text-muted mb-3"></i>
                <h5 className="text-muted">
                  {filter === 'all'
                    ? 'Henüz stok kaydı bulunmuyor'
                    : `Bu kategoride stok kaydı bulunmuyor`
                  }
                </h5>
                <p className="text-muted">
                  {filter === 'all'
                    ? 'İlk stok kaydını oluşturmak için "Yeni Stok Kaydı" butonuna tıklayın.'
                    : 'Farklı filtre seçeneği deneyin.'
                  }
                </p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Stock Form Modal */}
      {showForm && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Yeni Stok Kaydı</h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setShowForm(false)}
                ></button>
              </div>
              <div className="modal-body">
                <StockForm
                  products={products}
                  warehouses={warehouses}
                  onSuccess={handleFormSuccess}
                  onCancel={() => setShowForm(false)}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Quick Stock Adjustment Modal */}
      {quickAdjustModal.show && quickAdjustModal.stock && (
        <QuickStockAdjustModal
          stock={quickAdjustModal.stock}
          type={quickAdjustModal.type}
          onSuccess={handleQuickAdjustSuccess}
          onClose={() => setQuickAdjustModal({ show: false, stock: null, type: null })}
        />
      )}

      {/* Stock Settings Modal */}
      {showSettingsModal && selectedStock && (
        <StockSettingsModal
          stock={selectedStock}
          onSuccess={handleSettingsSuccess}
          onClose={() => {
            setShowSettingsModal(false);
            setSelectedStock(null);
          }}
        />
      )}

      {/* Stock Transfer Modal */}
      {showTransferModal && (
        <StockTransferModal
          stock={selectedStock}
          onSuccess={handleTransferSuccess}
          onClose={() => {
            setShowTransferModal(false);
            setSelectedStock(null);
            setLockCustomerTransfer(false);
          }}
          lockToCustomerDelivery={lockCustomerTransfer}
        />
      )}

      {transferDetailModal.show && transferDetailModal.transfer && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 2000 }}>
          <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
            <div className="modal-content shadow border-0 rounded-4">
              <div className="modal-header text-white" style={{ background: 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)' }}>
                <h5 className="modal-title">
                  <i className="fas fa-eye me-2"></i>
                  Transfer {transferDetailModal.transfer.id} Detayı
                </h5>
                <button type="button" className="btn-close btn-close-white" onClick={closeTransferDetailModal}></button>
              </div>
              <div className="modal-body">
                {(() => {
                  const t = transferDetailModal.transfer;
                  const items = Array.isArray(t.items) && t.items.length ? t.items : (Array.isArray(t.transferItems) ? t.transferItems : []);
                  const routeLabel =
                    (t.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY'
                      ? `${t.sourceWarehouse?.name || '-'} → ${t.customerFullName || 'Müşteri'}`
                      : `${t.sourceWarehouse?.name || '-'} → ${t.destinationWarehouse?.name || '-'}`;
                  return (
                    <>
                      <div className="row g-3 mb-3">
                        <div className="col-md-4">
                          <div className="border rounded-3 p-3 h-100">
                            <small className="text-muted text-uppercase">Transfer Tipi</small>
                            <div className="fw-semibold">{getTransferTypeLabel(t.transferType)}</div>
                          </div>
                        </div>
                        <div className="col-md-4">
                          <div className="border rounded-3 p-3 h-100">
                            <small className="text-muted text-uppercase d-block mb-1">Durum</small>
                            {(() => {
                              const meta = getTransferStatusMeta(t.status);
                              return (
                                <span className={`badge bg-${meta.bootstrap}`}>
                                  <i className={`fas fa-${meta.icon} me-1`}></i>
                                  {meta.label}
                                </span>
                              );
                            })()}
                          </div>
                        </div>
                        <div className="col-md-4">
                          <div className="border rounded-3 p-3 h-100">
                            <small className="text-muted text-uppercase">Tarih</small>
                            <div className="fw-semibold">{formatDateInTurkeyTimezone(t.transferDate, { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</div>
                          </div>
                        </div>
                      </div>
                      <div className="row g-3 mb-4">
                        <div className="col-md-6">
                          <div className="border rounded-3 p-3 h-100">
                            <small className="text-muted text-uppercase">Rota</small>
                            <div className="fw-semibold">{routeLabel}</div>
                            {t.customerAddress && (
                              <small className="text-muted d-block mt-1">
                                <i className="fas fa-map-marker-alt me-1"></i>
                                {t.customerAddress}
                              </small>
                            )}
                          </div>
                        </div>
                        <div className="col-md-6">
                          <div className="border rounded-3 p-3 h-100">
                            <small className="text-muted text-uppercase">Sürücü / Araç</small>
                            <div className="fw-semibold">{t.driverName || '-'}</div>
                            <small className="text-muted d-block">{t.driverPhone || ''}</small>
                            {t.vehiclePlate && (
                              <span className="badge bg-secondary mt-2">
                                <i className="fas fa-car me-1"></i>
                                {t.vehiclePlate}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>
                      <h6 className="fw-bold mb-2">
                        <i className="fas fa-box me-2"></i>
                        Ürünler
                      </h6>
                      {items.length === 0 ? (
                        <p className="text-muted small mb-4">Ürün bilgisi bulunamadı.</p>
                      ) : (
                        <div className="table-responsive mb-4">
                          <table className="table table-sm align-middle">
                            <thead className="table-light">
                              <tr>
                                <th>Ürün</th>
                                <th>SKU</th>
                                <th className="text-end">Miktar</th>
                              </tr>
                            </thead>
                            <tbody>
                              {items.map((item, idx) => (
                                <tr key={`${t.id}-detail-${item.id || idx}`}>
                                  <td>{item.product?.name || '-'}</td>
                                  <td>{item.product?.sku || '-'}</td>
                                  <td className="text-end">{item.quantity}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                      {(t.notes || t.completionNote || t.cancellationReason) && (
                        <div className="row g-3">
                          {t.notes && (
                            <div className="col-md-4">
                              <div className="border rounded-3 p-3 h-100">
                                <small className="text-muted text-uppercase">Transfer Notu</small>
                                <p className="mb-0 small">{t.notes}</p>
                              </div>
                            </div>
                          )}
                          {t.completionNote && (
                            <div className="col-md-4">
                              <div className="border rounded-3 p-3 h-100">
                                <small className="text-muted text-uppercase">Tamamlama Notu</small>
                                <p className="mb-0 small">{t.completionNote}</p>
                              </div>
                            </div>
                          )}
                          {t.cancellationReason && (
                            <div className="col-md-4">
                              <div className="border rounded-3 p-3 h-100">
                                <small className="text-muted text-uppercase">İptal Nedeni</small>
                                <p className="mb-0 small text-danger">{t.cancellationReason}</p>
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </>
                  );
                })()}
              </div>
              <div className="modal-footer bg-light">
                <button type="button" className="btn btn-secondary" onClick={closeTransferDetailModal}>
                  <i className="fas fa-times me-2"></i>
                  Kapat
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Transfer History Section */}
      {showTransferHistory && (
        <div className="mt-4">
          {/* Statistics Cards */}
          <div className="row g-3 mb-4">
            <div className="col-md-3">
              <div className="card border-warning shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-clock fa-2x text-warning mb-2"></i>
                  <h3 className="mb-0">{getStatusCount('PENDING')}</h3>
                  <p className="text-muted mb-0 small">Beklemede</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-info shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-truck fa-2x text-info mb-2"></i>
                  <h3 className="mb-0">{getStatusCount('IN_TRANSIT')}</h3>
                  <p className="text-muted mb-0 small">Yolda</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-success shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-check-circle fa-2x text-success mb-2"></i>
                  <h3 className="mb-0">{getStatusCount('COMPLETED')}</h3>
                  <p className="text-muted mb-0 small">Tamamlandı</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-danger shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-times-circle fa-2x text-danger mb-2"></i>
                  <h3 className="mb-0">{getStatusCount('CANCELLED')}</h3>
                  <p className="text-muted mb-0 small">İptal Edildi</p>
                </div>
              </div>
            </div>
          </div>

          <div className="d-flex flex-wrap gap-2 mb-3">
            <span className="badge rounded-pill bg-primary bg-opacity-10 text-primary py-2 px-3">
              <i className="fas fa-warehouse me-1"></i>
              Depo Transferi: {warehouseTransferCount}
            </span>
            <span className="badge rounded-pill bg-info bg-opacity-10 text-info py-2 px-3">
              <i className="fas fa-shipping-fast me-1"></i>
              Müşteri Sevkiyatı: {customerTransferCount}
            </span>
          </div>

          {/* Filter Buttons */}
          <div className="card mb-3">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
                <div className="btn-group" role="group">
                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-all"
                    value="ALL"
                    checked={transferStatusFilter === 'ALL'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-secondary" htmlFor="status-all">
                    <i className="fas fa-list me-1"></i>
                    Tümü ({totalTransfers})
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-pending"
                    value="PENDING"
                    checked={transferStatusFilter === 'PENDING'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-warning" htmlFor="status-pending">
                    <i className="fas fa-clock me-1"></i>
                    Beklemede
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-transit"
                    value="IN_TRANSIT"
                    checked={transferStatusFilter === 'IN_TRANSIT'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-info" htmlFor="status-transit">
                    <i className="fas fa-truck me-1"></i>
                    Yolda
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-completed"
                    value="COMPLETED"
                    checked={transferStatusFilter === 'COMPLETED'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-success" htmlFor="status-completed">
                    <i className="fas fa-check-circle me-1"></i>
                    Tamamlandı
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-cancelled"
                    value="CANCELLED"
                    checked={transferStatusFilter === 'CANCELLED'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-danger" htmlFor="status-cancelled">
                    <i className="fas fa-times-circle me-1"></i>
                    İptal
                  </label>
                </div>

                <div className="text-muted small">
                  <i className="fas fa-info-circle me-1"></i>
                  Toplam {totalTransfers} transfer
                </div>
              </div>

              <div className="d-flex flex-wrap gap-2 mt-3">
                <div className="btn-group btn-group-sm" role="group" aria-label="Transfer tipi filtresi">
                  <input
                    type="radio"
                    className="btn-check"
                    name="transferType"
                    id="type-all"
                    value="ALL"
                    checked={transferTypeFilter === 'ALL'}
                    onChange={(e) => setTransferTypeFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-secondary" htmlFor="type-all">
                    <i className="fas fa-stream me-1"></i>
                    Tüm Tipler
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferType"
                    id="type-warehouse"
                    value="WAREHOUSE"
                    checked={transferTypeFilter === 'WAREHOUSE'}
                    onChange={(e) => setTransferTypeFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-primary" htmlFor="type-warehouse">
                    <i className="fas fa-warehouse me-1"></i>
                    Depo
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferType"
                    id="type-customer"
                    value="CUSTOMER_DELIVERY"
                    checked={transferTypeFilter === 'CUSTOMER_DELIVERY'}
                    onChange={(e) => setTransferTypeFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-info" htmlFor="type-customer">
                    <i className="fas fa-shipping-fast me-1"></i>
                    Müşteri
                  </label>
                </div>
              </div>

              {/* Advanced transfer filters */}
              <div className="row g-2 mt-3">
                <div className="col-md-4">
                  <div className="input-group">
                    <span className="input-group-text"><i className="fas fa-box"></i></span>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="Ürün adı ara..."
                      value={transferProductName}
                      onChange={(e) => setTransferProductName(e.target.value)}
                    />
                  </div>
                </div>
                <div className="col-md-4">
                  <div className="input-group">
                    <span className="input-group-text"><i className="fas fa-barcode"></i></span>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="Stok kodu (SKU) ara..."
                      value={transferSku}
                      onChange={(e) => setTransferSku(e.target.value)}
                    />
                  </div>
                </div>
                <div className="col-md-4">
                  <div className="input-group">
                    <span className="input-group-text"><i className="fas fa-id-card"></i></span>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="Şoför adı ara..."
                      value={transferDriver}
                      onChange={(e) => setTransferDriver(e.target.value)}
                    />
                  </div>
                </div>
              </div>
              <div className="row g-2 mt-2">
                <div className="col-md-12">
                  <div className="input-group">
                    <span className="input-group-text"><i className="fas fa-sticky-note"></i></span>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="Notlarda ara..."
                      value={transferNotes}
                      onChange={(e) => setTransferNotes(e.target.value)}
                    />
                  </div>
                </div>
              </div>
              <div className="row g-2 mt-2">
                <div className="col-md-6">
                  <SearchableSelect
                    value={transferSourceWarehouseId}
                    onChange={(id) => setTransferSourceWarehouseId(id != null ? Number(id) : null)}
                    searchEndpoint="/api/warehouses"
                    placeholder="Kaynak depo ara..."
                    allowClear={true}
                    clearText="Temizle"
                    wrapperClassName="mb-0"
                    renderOption={(w) => w.name}
                  />
                </div>
                <div className="col-md-6">
                  <SearchableSelect
                    value={transferDestinationWarehouseId}
                    onChange={(id) => setTransferDestinationWarehouseId(id != null ? Number(id) : null)}
                    searchEndpoint="/api/warehouses"
                    placeholder="Hedef depo ara..."
                    allowClear={true}
                    clearText="Temizle"
                    wrapperClassName="mb-0"
                    renderOption={(w) => w.name}
                  />
                </div>
              </div>
              <div className="col-md-12 d-flex flex-wrap gap-2 mt-1">
                {(transferProductName || transferSku || transferDriver || transferNotes || transferSourceWarehouseId || transferDestinationWarehouseId) && (
                  <button
                    className="btn btn-sm btn-outline-secondary"
                    onClick={() => {
                      setTransferProductName('');
                      setTransferSku('');
                      setTransferDriver('');
                      setTransferNotes('');
                      setTransferSourceWarehouseId(null);
                      setTransferDestinationWarehouseId(null);
                    }}
                  >
                    <i className="fas fa-times me-1"></i>
                    Transfer filtrelerini temizle
                  </button>
                )}
              </div>
            </div>
          </div>

          {transfers.length > 0 && (
            <div className="mobile-selection-toolbar d-lg-none mb-3">
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                <div className="fw-semibold">
                  Seçili transfer: {selectedTransferCount}
                </div>
                <div className="d-flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-primary"
                    onClick={toggleSelectAllVisibleTransfers}
                  >
                    {areAllVisibleTransfersSelected ? 'Seçimi Kaldır' : 'Tümünü Seç'}
                  </button>
                  {selectedTransferCount > 0 && (
                    <>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-secondary"
                        onClick={clearSelectedTransfers}
                      >
                        Seçimi Temizle
                      </button>
                      {isAdmin && (
                        <button
                          type="button"
                          className="btn btn-sm btn-danger"
                          onClick={() => handleBatchDeleteTransfers([...selectedTransfers])}
                        >
                          Seçilileri Sil
                        </button>
                      )}
                    </>
                  )}
                </div>
              </div>
            </div>
          )}

          <div className="card shadow-sm">
            <div className="card-header bg-gradient text-white" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
              <div className="d-flex justify-content-between align-items-center">
                <h5 className="mb-0">
                  <i className="fas fa-history me-2"></i>
                  Transfer Geçmişi
                </h5>
              </div>
            </div>
            {selectedTransferCount > 0 && isAdmin && (
              <div className="alert alert-warning d-flex justify-content-between align-items-center flex-wrap gap-2 m-3 mb-0">
                <div className="fw-semibold">
                  <i className="fas fa-check-square me-2"></i>
                  {selectedTransferCount} transfer seçildi
                </div>
                <div className="d-flex gap-2 flex-wrap">
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary"
                    onClick={clearSelectedTransfers}
                  >
                    Seçimi Temizle
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-danger"
                    onClick={() => handleBatchDeleteTransfers([...selectedTransfers])}
                  >
                    <i className="fas fa-trash me-1"></i>
                    Seçilileri Sil
                  </button>
                </div>
              </div>
            )}
            <div className="card-body p-0">
              <div className="px-3 pt-3 d-flex justify-content-end align-items-center mb-2 gap-2 flex-wrap">
                <div className="page-size-control d-flex align-items-center flex-wrap">
                  <span className="form-label mb-0 small text-muted">Sayfa Boyutu</span>
                  <select
                    className="form-select form-select-sm page-size-select"
                    value={transferPageSize}
                    onChange={handleTransferPageSizeChange}
                  >
                    {PAGE_SIZE_OPTIONS.map(size => (
                      <option key={`transfer-page-${size}`} value={size}>{size}</option>
                    ))}
                  </select>
                </div>
              </div>
              {transfers.length === 0 ? (
                <div className="text-center py-5">
                  <i className="fas fa-inbox fa-4x text-muted mb-3"></i>
                  <h5 className="text-muted">
                    {transferStatusFilter === 'ALL'
                      ? 'Henüz transfer kaydı bulunmuyor'
                      : `${transferStatusFilter === 'PENDING' ? 'Beklemede' : transferStatusFilter === 'IN_TRANSIT' ? 'Yolda' : transferStatusFilter === 'COMPLETED' ? 'Tamamlanmış' : 'İptal edilmiş'} transfer bulunmuyor`
                    }
                  </h5>
                  <p className="text-muted">
                    {transferStatusFilter === 'ALL' && 'İlk transferi oluşturmak için stok listesinden "Transfer Yap" butonuna tıklayın.'}
                  </p>
                </div>
              ) : (
                <>
                  {/* Desktop Table View */}
                  <div className="breakpoint-1155-desktop table-responsive" style={{ overflowX: 'auto' }}>
                    <table className="table table-hover table-sm mb-0 align-middle transfer-table-compact">
                      {/* Desktop için fixed layout - geniş ekranlarda */}
                      <colgroup className="d-none d-xl-table-column-group">
                        {isAdmin && <col style={{ width: '40px' }} />}  {/* Checkbox */}
                        <col style={{ width: '60px' }} />      {/* No */}
                        <col style={{ width: '110px' }} />     {/* Tarih */}
                        <col style={{ width: '140px' }} />     {/* Ürün */}
                        <col style={{ width: '140px' }} />     {/* Kaynak */}
                        <col style={{ width: '140px' }} />     {/* Hedef */}
                        <col style={{ width: '70px' }} />      {/* Miktar */}
                        <col style={{ width: '110px' }} />     {/* Şoför */}
                        <col style={{ width: '70px' }} />      {/* Plaka */}
                        <col style={{ width: '90px' }} />      {/* Durum */}
                        <col style={{ width: '140px' }} />     {/* İşlemler */}
                      </colgroup>
                      <thead className="table-light sticky-top" style={{ position: 'sticky', top: 0, zIndex: 10 }}>
                        <tr>
                          {isAdmin && (
                            <th className="text-center align-middle" style={{ width: '40px' }}>
                              <div className="form-check mb-0">
                                <input
                                  className="form-check-input"
                                  type="checkbox"
                                  checked={areAllVisibleTransfersSelected}
                                  onChange={toggleSelectAllVisibleTransfers}
                                  disabled={transfers.length === 0}
                                  aria-label="Tümünü seç"
                                />
                              </div>
                            </th>
                          )}
                          <th className="text-center align-middle" style={{ width: '60px' }}>
                            <i className="fas fa-hashtag d-none d-sm-inline me-1"></i>
                            <div className="small">No</div>
                          </th>
                          <th className="align-middle">
                            <i className="fas fa-calendar d-none d-sm-inline me-1"></i>
                            <div className="small">Tarih</div>
                          </th>
                          <th className="align-middle">
                            <i className="fas fa-box d-none d-sm-inline me-1"></i>
                            <div className="small">Ürün</div>
                          </th>
                          <th className="align-middle">
                            <i className="fas fa-warehouse text-danger d-none d-sm-inline me-1"></i>
                            <div className="small">Kaynak</div>
                          </th>
                          <th className="align-middle" style={{ minWidth: '150px' }}>
                            <i className="fas fa-warehouse text-success d-none d-sm-inline me-1"></i>
                            <div className="small">Hedef</div>
                          </th>
                          <th className="text-center align-middle" style={{ width: '70px' }}>
                            <i className="fas fa-boxes d-none d-sm-inline me-1"></i>
                            <div className="small">Adet</div>
                          </th>
                          {/* Şoför kolonu - sadece geniş ekranlarda göster */}
                          <th className="align-middle d-none d-xl-table-cell">
                            <i className="fas fa-user me-1"></i>
                            <div className="small">Şoför</div>
                          </th>
                          {/* Plaka kolonu - sadece geniş ekranlarda göster */}
                          <th className="text-center align-middle d-none d-xl-table-cell" style={{ width: '70px' }}>
                            <i className="fas fa-car me-1"></i>
                            <div className="small">Plaka</div>
                          </th>
                          <th className="text-center align-middle" style={{ width: '100px' }}>
                            <i className="fas fa-info-circle d-none d-sm-inline me-1"></i>
                            <div className="small">Durum</div>
                          </th>
                          <th className="text-center align-middle" style={{ width: '140px' }}>
                            <i className="fas fa-cog d-none d-sm-inline me-1"></i>
                            <div className="small">İşlemler</div>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {transfers.map((transfer) => {
                          const statusConfig = {
                            PENDING: { label: 'Beklemede', class: 'warning', icon: 'clock' },
                            IN_TRANSIT: { label: 'Yolda', class: 'info', icon: 'truck' },
                            COMPLETED: { label: 'Tamamlandı', class: 'success', icon: 'check-circle' },
                            CANCELLED: { label: 'İptal Edildi', class: 'danger', icon: 'times-circle' }
                          };
                          const status = statusConfig[transfer.status] || statusConfig.PENDING;
                          const transferItemsPreview = getTransferItemsList(transfer);
                          const totalQuantity = getTransferTotalQuantity(transfer);
                          const awaitingApproval = (transfer.approvalStatus || '').toUpperCase() === 'PENDING';
                          const approvalRejected = (transfer.approvalStatus || '').toUpperCase() === 'REJECTED';

                          const isSelected = selectedTransfers.includes(transfer.id);

                          return (
                            <tr key={transfer.id} className={isSelected ? 'table-active' : ''}>
                              {isAdmin && (
                                <td className="text-center align-middle">
                                  <div className="form-check mb-0">
                                    <input
                                      className="form-check-input"
                                      type="checkbox"
                                      checked={isSelected}
                                      onChange={() => toggleTransferSelection(transfer.id)}
                                      aria-label="Transfer seç"
                                    />
                                  </div>
                                </td>
                              )}
                              <td className="text-center align-middle">
                                <span className="badge bg-dark d-block">#{transfer.id}</span>
                                <span
                                  className={`badge d-block mt-1 ${(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY'
                                    ? 'bg-info text-dark'
                                    : 'bg-secondary'
                                    }`}
                                  title={(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'Müşteri Sevkiyatı' : 'Depo Transferi'}
                                >
                                  {(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'Müşteri' : 'Depo'}
                                </span>
                              </td>
                              <td className="align-middle">
                                <div className="small">
                                  <div className="fw-bold text-nowrap">
                                    {formatDateInTurkeyTimezone(transfer.transferDate, { year: 'numeric', month: '2-digit', day: '2-digit' })}
                                  </div>
                                  <div className="text-muted text-nowrap">
                                    {formatDateInTurkeyTimezone(transfer.transferDate, {
                                      hour: '2-digit',
                                      minute: '2-digit'
                                    })}
                                  </div>
                                </div>
                              </td>
                              <td className="align-middle">
                                <div className="text-truncate">
                                  {transferItemsPreview.length === 0 ? (
                                    <small className="text-muted">Ürün bilgisi yok</small>
                                  ) : (
                                    <>
                                      {transferItemsPreview.slice(0, 3).map((item, idx) => (
                                        <div key={`${transfer.id}-${item.product?.id || idx}`} className="d-flex justify-content-between align-items-center mb-1">
                                          <div className="me-2 overflow-hidden">
                                            <div className="fw-bold small text-truncate">{item.product?.name || '-'}</div>
                                            <small className="text-muted text-truncate">{item.product?.sku || '-'}</small>
                                          </div>
                                          <span className="badge bg-light text-dark">{item.quantity}</span>
                                        </div>
                                      ))}
                                      {transferItemsPreview.length > 3 && (
                                        <small className="text-muted">+ {transferItemsPreview.length - 3} ürün daha</small>
                                      )}
                                    </>
                                  )}
                                  {transfer.createdBy && (
                                    <small className="text-muted d-block mt-1">
                                      <i className="fas fa-user me-1"></i>
                                      {transfer.createdBy}
                                    </small>
                                  )}
                                </div>
                              </td>
                              <td className="align-middle">
                                <div className="d-flex align-items-center">
                                  <div className="bg-danger bg-opacity-10 rounded-circle p-1 me-1 me-sm-2 flex-shrink-0 d-none d-sm-flex" style={{ width: '30px', height: '30px', alignItems: 'center', justifyContent: 'center' }}>
                                    <i className="fas fa-warehouse text-danger fa-sm"></i>
                                  </div>
                                  <div className="small overflow-hidden w-100">
                                    <div className="fw-bold text-truncate" title={transfer.sourceWarehouse?.name}>
                                      <i className="fas fa-warehouse text-danger fa-xs me-1 d-inline d-sm-none"></i>
                                      {transfer.sourceWarehouse?.name}
                                    </div>
                                    <small className="text-muted d-block text-truncate" title={transfer.sourceWarehouse?.location}>
                                      {transfer.sourceWarehouse?.location}
                                    </small>
                                  </div>
                                </div>
                              </td>
                              <td className="align-middle">
                                <div className="d-flex align-items-center">
                                  <div
                                    className={`bg-${(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'info' : 'success'} bg-opacity-10 rounded-circle p-1 me-1 me-sm-2 flex-shrink-0 d-none d-sm-flex`}
                                    style={{ width: '30px', height: '30px', alignItems: 'center', justifyContent: 'center' }}
                                  >
                                    <i
                                      className={`fas ${(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'fa-user-tag text-info' : 'fa-warehouse text-success'
                                        } fa-sm`}
                                    ></i>
                                  </div>
                                  <div className="small overflow-hidden w-100">
                                    {(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? (
                                      <>
                                        <div className="fw-bold text-truncate" title={transfer.customerFullName}>
                                          <i className="fas fa-user-tag text-info fa-xs me-1 d-inline d-sm-none"></i>
                                          {transfer.customerFullName}
                                        </div>
                                        <small className="text-muted d-block text-truncate" title={transfer.customerPhone}>
                                          <i className="fas fa-phone me-1"></i>
                                          {transfer.customerPhone || '-'}
                                        </small>
                                        <small className="text-muted d-block text-truncate" title={transfer.customerAddress}>
                                          <i className="fas fa-map-marker-alt me-1"></i>
                                          {transfer.customerAddress || '-'}
                                        </small>
                                      </>
                                    ) : (
                                      <>
                                        <div className="fw-bold text-truncate" title={transfer.destinationWarehouse?.name}>
                                          <i className="fas fa-warehouse text-success fa-xs me-1 d-inline d-sm-none"></i>
                                          {transfer.destinationWarehouse?.name}
                                        </div>
                                        <small className="text-muted d-block text-truncate" title={transfer.destinationWarehouse?.location}>
                                          {transfer.destinationWarehouse?.location}
                                        </small>
                                      </>
                                    )}
                                  </div>
                                </div>
                              </td>
                              <td className="text-center align-middle">
                                <span className="badge bg-primary rounded-pill">{totalQuantity}</span>
                              </td>
                              {/* Şoför kolonu - tablet ve üstünde göster */}
                              <td className="align-middle d-none d-md-table-cell">
                                <div className="small overflow-hidden">
                                  <div className="fw-bold text-truncate" title={transfer.driverName}>{transfer.driverName}</div>
                                  <div className="text-muted text-truncate" title={transfer.driverPhone}>
                                    <i className="fas fa-phone me-1"></i>
                                    {transfer.driverPhone}
                                  </div>
                                </div>
                              </td>
                              {/* Plaka kolonu - desktop'ta göster */}
                              <td className="text-center align-middle d-none d-lg-table-cell">
                                <span className="badge bg-secondary text-truncate d-block mx-auto" style={{ maxWidth: '100%' }} title={transfer.vehiclePlate}>
                                  {transfer.vehiclePlate}
                                </span>
                              </td>
                              <td className="text-center align-middle">
                                <span className={`badge bg-${status.class} d-block py-2 mb-1`}>
                                  <i className={`fas fa-${status.icon} me-1`}></i>
                                  <span className="small">{status.label}</span>
                                </span>
                                {transfer.completedDate && (
                                  <small className="d-block text-success mt-1" title={`Tamamlanma Tarihi: ${formatDateInTurkeyTimezone(transfer.completedDate, { year: 'numeric', month: '2-digit', day: '2-digit' })}`}>
                                    <i className="fas fa-check-circle me-1"></i>
                                    <span className="d-none d-md-inline">Tamamlandı: </span>
                                    {formatDateInTurkeyTimezone(transfer.completedDate, { day: '2-digit', month: '2-digit' })}
                                  </small>
                                )}
                                {transfer.cancelledDate && (
                                  <small className="d-block text-danger mt-1" title={`İptal Tarihi: ${formatDateInTurkeyTimezone(transfer.cancelledDate, { year: 'numeric', month: '2-digit', day: '2-digit' })}`}>
                                    <i className="fas fa-times-circle me-1"></i>
                                    <span className="d-none d-md-inline">İptal: </span>
                                    {formatDateInTurkeyTimezone(transfer.cancelledDate, { day: '2-digit', month: '2-digit' })}
                                  </small>
                                )}
                                {awaitingApproval && (
                                  <small className="d-block text-warning mt-1">
                                    <i className="fas fa-hourglass-half me-1"></i>
                                    Onay Bekleniyor
                                  </small>
                                )}
                                {approvalRejected && (
                                  <small
                                    className="d-block text-danger mt-1 text-truncate"
                                    title={transfer.approvalNote || 'Onay reddedildi'}
                                  >
                                    <i className="fas fa-times-circle me-1"></i>
                                    Onay Reddedildi
                                  </small>
                                )}
                                {(transfer.approvalStatus || '').toUpperCase() === 'APPROVED' && transfer.approvalDecisionBy && (
                                  <small className="d-block text-muted mt-1">
                                    Onaylayan: {transfer.approvalDecisionBy}
                                  </small>
                                )}
                              </td>
                              <td className="text-center align-middle" style={{ padding: '6px' }}>
                                <div className="d-flex flex-column gap-1">
                                  {transfer.status === 'PENDING' && (
                                    awaitingApproval ? (
                                      <div className="d-flex flex-column gap-2">
                                        <span className="badge bg-warning text-dark">
                                          <i className="fas fa-hourglass-half me-1"></i>
                                          Onay Bekleniyor
                                        </span>
                                        {isAdmin && (
                                          <button
                                            className="btn btn-sm btn-outline-primary"
                                            onClick={() => setShowApprovalModal(true)}
                                          >
                                            <i className="fas fa-tasks me-1"></i>
                                            Onayları Aç
                                          </button>
                                        )}
                                      </div>
                                    ) : (
                                      <>
                                        <button
                                          className="btn btn-sm btn-info w-100 py-1 px-2"
                                          style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                          onClick={() => {
                                            setConfirmModal({
                                              show: true,
                                              title: 'Transferi Yola Çıkart',
                                              message: 'Transfer yola çıkartılacak ve stok rezerve edilecek. Onaylıyor musunuz?',
                                              confirmText: 'Evet, Yola Çıkar',
                                              confirmVariant: 'info',
                                              icon: 'truck',
                                              onConfirm: () => {
                                                setConfirmModal({ show: false });
                                                handleTransferStatusChange(transfer.id, 'start');
                                              }
                                            });
                                          }}
                                          title="Transfer yola çıkartılacak"
                                        >
                                          <i className="fas fa-truck me-1"></i>
                                          <span className="d-none d-sm-inline">Yola Çıkar</span>
                                          <span className="d-inline d-sm-none">Yola</span>
                                        </button>
                                        <button
                                          className="btn btn-sm btn-success w-100 py-1 px-2"
                                          style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                          onClick={() =>
                                            openCompletionFlow(
                                              transfer,
                                              'Transfer direkt tamamlanacak ve stok kaynak depodan düşülecek. Onaylıyor musunuz?'
                                            )
                                          }
                                          title="Transfer direkt tamamlanacak"
                                        >
                                          <i className="fas fa-check me-1"></i>
                                          Tamamla
                                        </button>
                                        <button
                                          className="btn btn-sm btn-danger w-100 py-1 px-2"
                                          style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                          onClick={() => {
                                            setCancellationModal({
                                              show: true,
                                              transferId: transfer.id,
                                              reason: ''
                                            });
                                          }}
                                          title="Transferi iptal et"
                                        >
                                          <i className="fas fa-ban me-1"></i>
                                          İptal
                                        </button>
                                      </>
                                    )
                                  )}
                                  {transfer.status === 'IN_TRANSIT' && (
                                    <>
                                      <button
                                        className="btn btn-sm btn-success w-100 py-1 px-2"
                                        style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                        onClick={() =>
                                          openCompletionFlow(
                                            transfer,
                                            'Transfer tamamlanacak ve stok rezervasyonu kapatılacak. Onaylıyor musunuz?'
                                          )
                                        }
                                        title="Transferi tamamla ve stok taşı"
                                      >
                                        <i className="fas fa-check-double me-1"></i>
                                        Tamamla
                                      </button>
                                      <button
                                        className="btn btn-sm btn-warning w-100 py-1 px-2"
                                        style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                        onClick={() => {
                                          setCancellationModal({
                                            show: true,
                                            transferId: transfer.id,
                                            reason: ''
                                          });
                                        }}
                                        title="Transferi iptal et ve rezervasyonu kaldır"
                                      >
                                        <i className="fas fa-ban me-1"></i>
                                        <span className="d-none d-sm-inline">İptal Et</span>
                                        <span className="d-inline d-sm-none">İptal</span>
                                      </button>
                                    </>
                                  )}
                                  {(transfer.status === 'CANCELLED' || transfer.status === 'PENDING') && (
                                    <button
                                      className="btn btn-sm btn-outline-danger w-100 py-1 px-2"
                                      style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                      onClick={() => handleDeleteTransfer(transfer.id)}
                                      title="Transfer kaydını sil"
                                    >
                                      <i className="fas fa-trash me-1"></i>
                                      Sil
                                    </button>
                                  )}
                                  {transfer.status === 'CANCELLED' && transfer.cancellationReason && (
                                    <button
                                      className="btn btn-sm btn-outline-danger w-100 py-1 px-2"
                                      style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                      onClick={() => setNotesModal({
                                        show: true,
                                        notes: transfer.cancellationReason,
                                        transferId: transfer.id,
                                        title: 'İptal Nedeni'
                                      })}
                                      title="İptal nedenini görüntüle"
                                    >
                                      <i className="fas fa-exclamation-circle me-1"></i>
                                      <span className="d-none d-sm-inline">İptal Nedeni</span>
                                      <span className="d-inline d-sm-none">Neden</span>
                                    </button>
                                  )}
                                  {transfer.notes && (
                                    <button
                                      className="btn btn-sm btn-outline-secondary w-100 py-1 px-2"
                                      style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                      onClick={() => setNotesModal({
                                        show: true,
                                        notes: transfer.notes,
                                        transferId: transfer.id,
                                        title: 'Transfer Notları'
                                      })}
                                      title="Notları görüntüle"
                                    >
                                      <i className="fas fa-sticky-note me-1"></i>
                                      Notlar
                                    </button>
                                  )}
                                  {approvalRejected && transfer.approvalNote && (
                                    <button
                                      className="btn btn-sm btn-outline-danger w-100 py-1 px-2"
                                      style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                      onClick={() => setNotesModal({
                                        show: true,
                                        notes: transfer.approvalNote,
                                        transferId: transfer.id,
                                        title: 'Onay Red Notu'
                                      })}
                                      title="Onay reddetme notunu görüntüle"
                                    >
                                      <i className="fas fa-exclamation-circle me-1"></i>
                                      Onay Notu
                                    </button>
                                  )}
                                  {transfer.completionNote && (
                                    <button
                                      className="btn btn-sm btn-outline-success w-100 py-1 px-2"
                                      style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                      onClick={() => setNotesModal({
                                        show: true,
                                        notes: transfer.completionNote,
                                        transferId: transfer.id,
                                        title: 'Tamamlama Notu'
                                      })}
                                      title="Tamamlama notunu görüntüle"
                                    >
                                      <i className="fas fa-clipboard-check me-1"></i>
                                      Tamamlama
                                    </button>
                                  )}
                                  {role === 'ADMIN' && (
                                    <button
                                      className="btn btn-sm btn-outline-secondary w-100 py-1 px-2"
                                      style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                      onClick={() => setAuditModal({ show: true, entityType: 'StockTransfer', entityId: transfer.id })}
                                      title="Hareket Geçmişi"
                                    >
                                      <i className="fas fa-history me-1"></i>
                                      Hareketler
                                    </button>
                                  )}
                                  <button
                                    className="btn btn-sm btn-outline-primary w-100 py-1 px-2"
                                    style={{ fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap' }}
                                    onClick={() => openTransferDetailModal(transfer)}
                                    title="Transfer detaylarını görüntüle"
                                  >
                                    <i className="fas fa-eye me-1"></i>
                                    Detay
                                  </button>
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>

                  {/* Mobile Card View for Transfers */}
                  <div className="breakpoint-1155-mobile">
                    <div className="d-flex flex-column gap-3 p-3">
                      {transfers.map((transfer) => {
                        const statusConfig = {
                          PENDING: { label: 'Beklemede', class: 'warning', icon: 'clock' },
                          IN_TRANSIT: { label: 'Yolda', class: 'info', icon: 'truck' },
                          COMPLETED: { label: 'Tamamlandı', class: 'success', icon: 'check-circle' },
                          CANCELLED: { label: 'İptal Edildi', class: 'danger', icon: 'times-circle' }
                        };
                        const status = statusConfig[transfer.status] || statusConfig.PENDING;
                        const transferItemsPreview = getTransferItemsList(transfer);
                        const totalQuantity = getTransferTotalQuantity(transfer);
                        const awaitingApproval = (transfer.approvalStatus || '').toUpperCase() === 'PENDING';
                        const approvalRejected = (transfer.approvalStatus || '').toUpperCase() === 'REJECTED';
                        const isSelected = selectedTransfers.includes(transfer.id);
                        const canDelete = transfer.status !== 'IN_TRANSIT' && transfer.status !== 'COMPLETED';
                        const routeLabel =
                          (transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY'
                            ? `${transfer.sourceWarehouse?.name || '-'} → ${transfer.customerFullName || 'Müşteri'}`
                            : `${transfer.sourceWarehouse?.name || '-'} → ${transfer.destinationWarehouse?.name || '-'}`;
                        const transferDateFull = formatDateInTurkeyTimezone(transfer.transferDate, {
                          year: 'numeric',
                          month: '2-digit',
                          day: '2-digit',
                          hour: '2-digit',
                          minute: '2-digit'
                        });

                        return (
                          <div
                            key={transfer.id}
                            className={`transfer-mobile-card card border-0 shadow-sm ${isSelected ? 'is-selected' : ''}`}
                          >
                            <div className="card-body p-3">
                              {(() => {
                                const typeLabel = getTransferTypeLabel(transfer.transferType);
                                const statusMeta = getTransferStatusMeta(transfer.status);
                                return (
                                  <>
                                    <div className="transfer-mobile-card__header mb-2 d-flex justify-content-between align-items-center">

                                      {/* Sol blok: Transfer + Route */}
                                      <div className="d-flex flex-column">
                                        <div className="transfer-mobile-card__title">Transfer {transfer.id}</div>
                                        <div className="text-muted small mb-2 mt-2">{routeLabel}</div>
                                      </div>

                                      {/* Sağ blok: Modern Checkbox */}
                                      {isAdmin && (
                                        <div className="custom-select-checkbox d-flex align-items-center">
                                          <input
                                            className="custom-checkbox"
                                            type="checkbox"
                                            checked={isSelected}
                                            onChange={() => toggleTransferSelection(transfer.id)}
                                            disabled={!canDelete}
                                            aria-label="Transfer seç"
                                          />
                                          <label className="small text-muted ms-2">Seç</label>
                                        </div>
                                      )}
                                    </div>

                                    {/* Tarih + Type + Status */}
                                    <div className="transfer-mobile-card__badges d-flex justify-content-between align-items-center gap-3 mb-3">
                                      <small className="text-muted flex-grow-1 d-flex align-items-center gap-2">
                                        <i className="fas fa-calendar"></i>
                                        <span className="date-text">{transferDateFull}</span>
                                      </small>

                                      <div className="d-flex flex-column align-items-end gap-2">
                                        <span className="transfer-type-pill text-end">
                                          <i className="fas fa-route me-2"></i>
                                          {getTransferTypeLabel(transfer.transferType)}
                                        </span>
                                        <span className={`mobile-chip badge bg-${getTransferStatusMeta(transfer.status).bootstrap} text-end`}>
                                          <i className={`fas fa-${getTransferStatusMeta(transfer.status).icon} me-2`}></i>
                                          {getTransferStatusMeta(transfer.status).label}
                                        </span>
                                      </div>
                                    </div>
                                  </>
                                );
                              })()}
                              {/* Products */}
                              {transferItemsPreview.length > 0 && (
                                <div className="mb-3">
                                  <div className="small fw-bold mb-2 text-uppercase text-muted">
                                    <i className="fas fa-box me-1"></i>
                                    Ürünler ({transferItemsPreview.length})
                                  </div>
                                  <div className="d-flex flex-column gap-2">
                                    {transferItemsPreview.slice(0, 2).map((item, idx) => (
                                      <div
                                        key={`${transfer.id}-${item.product?.id || idx}`}
                                        className="mobile-stat-tile d-grid align-items-center"
                                        style={{ textAlign: 'left', gridTemplateColumns: '1fr auto', columnGap: '0.75rem' }}
                                      >
                                        <div className="d-flex flex-column gap-1">
                                          <div className="fw-semibold small">{item.product?.name || '-'}</div>
                                          <small className="text-muted">{item.product?.sku || '-'}</small>
                                        </div>
                                        <span
                                          className="badge bg-primary d-inline-flex align-items-center justify-content-center"
                                          style={{ minWidth: '3rem' }}
                                        >
                                          {item.quantity}
                                        </span>
                                      </div>
                                    ))}
                                    {transferItemsPreview.length > 2 && (
                                      <small className="text-muted">
                                        + {transferItemsPreview.length - 2} ürün daha
                                      </small>
                                    )}
                                  </div>
                                </div>
                              )}

                              {/* Warehouses */}
                              <div className="row g-2 mb-3 mobile-stat-grid">
                                <div className="col-6">
                                  <div className="mobile-stat-tile" style={{ textAlign: 'left' }}>
                                    <div className="label">
                                      <i className="fas fa-warehouse me-1"></i>
                                      Kaynak
                                    </div>
                                    <div className="value" style={{ fontSize: '0.95rem' }}>{transfer.sourceWarehouse?.name || '-'}</div>
                                    <small className="text-muted d-block">{transfer.sourceWarehouse?.location || ''}</small>
                                  </div>
                                </div>
                                <div className="col-6">
                                  <div className="mobile-stat-tile" style={{ textAlign: 'left' }}>
                                    <div className="label">
                                      <i className={`fas ${(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'fa-user-tag' : 'fa-warehouse'} me-1`}></i>
                                      Hedef
                                    </div>
                                    {(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? (
                                      <>
                                        <div className="value" style={{ fontSize: '0.95rem' }}>{transfer.customerFullName || '-'}</div>
                                        <small className="text-muted d-block">{transfer.customerPhone || ''}</small>
                                      </>
                                    ) : (
                                      <>
                                        <div className="value" style={{ fontSize: '0.95rem' }}>{transfer.destinationWarehouse?.name || '-'}</div>
                                        <small className="text-muted d-block">{transfer.destinationWarehouse?.location || ''}</small>
                                      </>
                                    )}
                                  </div>
                                </div>
                              </div>

                              {(transfer.driverName || transfer.vehiclePlate) && (
                                <div className="row g-2 mb-3 mobile-stat-grid">
                                  {transfer.driverName && (
                                    <div className="col-6">
                                      <div className="mobile-stat-tile" style={{ textAlign: 'left' }}>
                                        <div className="label">
                                          <i className="fas fa-user me-1"></i>
                                          Şoför
                                        </div>
                                        <div className="value" style={{ fontSize: '0.95rem' }}>{transfer.driverName}</div>
                                      </div>
                                    </div>
                                  )}
                                  {transfer.vehiclePlate && (
                                    <div className="col-6">
                                      <div className="mobile-stat-tile" style={{ textAlign: 'left' }}>
                                        <div className="label">
                                          <i className="fas fa-car me-1"></i>
                                          Plaka
                                        </div>
                                        <div className="value" style={{ fontSize: '0.95rem' }}>{transfer.vehiclePlate}</div>
                                      </div>
                                    </div>
                                  )}
                                </div>
                              )}

                              <div className="transfer-mobile-card__summary mb-3">
                                <div className="d-flex justify-content-between">
                                  <div>
                                    <div className="small text-muted text-uppercase">Toplam Miktar</div>
                                    <div className="fw-bold">{totalQuantity}</div>
                                  </div>
                                  {transfer.createdBy && (
                                    <div className="text-end">
                                      <div className="small text-muted text-uppercase">Oluşturan</div>
                                      <div className="fw-semibold small">{transfer.createdBy}</div>
                                    </div>
                                  )}
                                </div>
                              </div>

                              <button
                                className="btn btn-outline-primary w-100 mb-2"
                                onClick={() => openTransferDetailModal(transfer)}
                              >
                                <i className="fas fa-eye me-1"></i>
                                Detayı Gör
                              </button>

                              <div className="transfer-mobile-card__primary-actions d-flex justify-content-center gap-3 flex-wrap mb-2">
                                {transfer.status === 'PENDING' && !awaitingApproval && (
                                  <>
                                    <button
                                      className="btn btn-info"
                                      onClick={() => {
                                        setConfirmModal({
                                          show: true,
                                          title: 'Transferi Yola Çıkar',
                                          message: 'Transfer yola çıkarılacak ve stok rezerve edilecek. Onaylıyor musunuz?',
                                          confirmText: 'Evet, Yola Çıkar',
                                          confirmVariant: 'info',
                                          icon: 'truck',
                                          onConfirm: () => {
                                            setConfirmModal({ show: false });
                                            handleTransferStatusChange(transfer.id, 'start');
                                          }
                                        });
                                      }}
                                    >
                                      <i className="fas fa-truck me-1"></i>
                                      Yola Çıkar
                                    </button>
                                    <button
                                      className="btn btn-success"
                                      onClick={() =>
                                        openCompletionFlow(
                                          transfer,
                                          'Transfer direkt tamamlanacak ve stok kaynak depodan düşülecek. Onaylıyor musunuz?'
                                        )
                                      }
                                    >
                                      <i className="fas fa-check me-1"></i>
                                      Tamamla
                                    </button>
                                  </>
                                )}
                                {approvalRejected && transfer.rejectionReason && (
                                  <button
                                    className="btn btn-outline-danger"
                                    onClick={() => setNotesModal({
                                      show: true,
                                      notes: transfer.rejectionReason,
                                      transferId: transfer.id,
                                      title: 'Onay Notu'
                                    })}
                                  >
                                    <i className="fas fa-exclamation-circle me-1"></i>
                                    Onay Notu
                                  </button>
                                )}
                              </div>

                              <div className="transfer-mobile-card__actions d-flex justify-content-center gap-3 flex-wrap mt-2">
                                {transfer.notes && (
                                  <button
                                    className="btn btn-outline-secondary mobile-action-btn"
                                    onClick={() => setNotesModal({
                                      show: true,
                                      notes: transfer.notes,
                                      transferId: transfer.id,
                                      title: 'Transfer Notu'
                                    })}
                                    title="Transfer notunu aç"
                                  >
                                    <i className="fas fa-sticky-note"></i>
                                  </button>
                                )}
                                {transfer.completionNote && (
                                  <button
                                    className="btn btn-outline-success mobile-action-btn"
                                    onClick={() => setNotesModal({
                                      show: true,
                                      notes: transfer.completionNote,
                                      transferId: transfer.id,
                                      title: 'Tamamlama Notu'
                                    })}
                                    title="Tamamlama notunu aç"
                                  >
                                    <i className="fas fa-clipboard-check"></i>
                                  </button>
                                )}
                                {role === 'ADMIN' && (
                                  <button
                                    className="btn btn-outline-secondary mobile-action-btn"
                                    onClick={() => setAuditModal({ show: true, entityType: 'StockTransfer', entityId: transfer.id })}
                                    title="Hareket geçmişi"
                                  >
                                    <i className="fas fa-history"></i>
                                  </button>
                                )}
                                {role === 'ADMIN' && canDelete && (
                                  <button
                                    className="btn btn-outline-danger mobile-action-btn"
                                    onClick={() => handleDeleteTransfer(transfer.id)}
                                    title="Transferi sil"
                                  >
                                    <i className="fas fa-trash"></i>
                                  </button>
                                )}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  <div className="d-flex justify-content-between align-items-center px-3 py-3 flex-wrap gap-2">
                    <small className="text-muted">
                      Gösterilen kayıt: {transfers.length}/{totalTransfers || transfers.length}
                    </small>
                    <PaginationControls
                      page={transferPage}
                      totalPages={transferTotalPages}
                      onPageChange={handleTransferPageChange}
                    />
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )
      }

      {/* Confirmation Modal */}
      <ConfirmModal
        show={confirmModal.show}
        title={confirmModal.title}
        message={confirmModal.message}
        confirmText={confirmModal.confirmText}
        cancelText="İptal"
        confirmVariant={confirmModal.confirmVariant}
        icon={confirmModal.icon}
        onConfirm={confirmModal.onConfirm}
        onCancel={() => setConfirmModal({ show: false })}
      />

      {/* Error Modal */}
      <ConfirmModal
        show={errorModal.show}
        title={errorModal.title}
        message={errorModal.message}
        confirmText="Tamam"
        cancelText={null}
        confirmVariant="danger"
        icon="exclamation-triangle"
        onConfirm={() => setErrorModal({ show: false, title: '', message: '' })}
        onCancel={() => setErrorModal({ show: false, title: '', message: '' })}
      />

      {/* Error Details Modal */}
      {errorDetailsModal.show && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header bg-danger text-white">
                <h5 className="modal-title">
                  <i className="fas fa-exclamation-triangle me-2"></i>
                  {errorDetailsModal.title}
                </h5>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setErrorDetailsModal({ show: false, title: '', errors: [] })}
                ></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-warning">
                  <i className="fas fa-info-circle me-2"></i>
                  Aşağıdaki {errorDetailsModal.errors.length} kayıt silinemedi.
                </div>
                <div className="list-group" style={{ maxHeight: '400px', overflowY: 'auto' }}>
                  {errorDetailsModal.errors.map((error, index) => (
                    <div key={index} className="list-group-item border-start border-danger border-3">
                      <div className="d-flex flex-column gap-2">
                        <div className="fw-bold text-danger">
                          <i className={`fas ${error.transferInfo ? 'fa-truck' : 'fa-box'} me-2`}></i>
                          {error.transferInfo || error.stockInfo || `#${error.transferId || error.stockId || index + 1}`}
                        </div>
                        {error.sku && (
                          <div className="text-muted">
                            <strong>Stok Kodu:</strong> <span className="badge bg-secondary">{error.sku}</span>
                          </div>
                        )}
                        <div className="text-danger mt-1">
                          <strong>Hata:</strong> {error.error}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setErrorDetailsModal({ show: false, title: '', errors: [] })}
                >
                  Kapat
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Notes Modal */}
      <NotesModal
        show={notesModal.show}
        notes={notesModal.notes}
        title={notesModal.title}
        transferId={notesModal.transferId}
        onClose={() => setNotesModal({ show: false, notes: '', transferId: null, title: '' })}
      />

      {/* Cancellation Reason Modal */}
      {
        cancellationModal.show && (
          <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
            <div className="modal-dialog modal-dialog-centered">
              <div className="modal-content">
                <div className="modal-header bg-danger text-white">
                  <h5 className="modal-title">
                    <i className="fas fa-ban me-2"></i>
                    Transferi İptal Et
                  </h5>
                  <button
                    type="button"
                    className="btn-close btn-close-white"
                    onClick={() => setCancellationModal({ show: false, transferId: null, reason: '' })}
                  ></button>
                </div>
                <div className="modal-body">
                  <div className="alert alert-warning">
                    <i className="fas fa-exclamation-triangle me-2"></i>
                    Transfer iptal edilecek. Bu işlem geri alınamaz.
                  </div>
                  <div className="mb-3">
                    <label htmlFor="cancellationReason" className="form-label">
                      <i className="fas fa-comment-alt me-1"></i>
                      İptal Nedeni <span className="text-muted">(Opsiyonel)</span>
                    </label>
                    <textarea
                      id="cancellationReason"
                      className="form-control"
                      rows="4"
                      value={cancellationModal.reason}
                      onChange={(e) => setCancellationModal({ ...cancellationModal, reason: e.target.value })}
                      placeholder="İptal nedenini buraya yazabilirsiniz..."
                      maxLength="500"
                    />
                    <small className="text-muted">
                      {cancellationModal.reason.length}/500 karakter
                    </small>
                  </div>
                </div>
                <div className="modal-footer">
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => setCancellationModal({ show: false, transferId: null, reason: '' })}
                  >
                    <i className="fas fa-times me-2"></i>
                    Vazgeç
                  </button>
                  <button
                    type="button"
                    className="btn btn-danger"
                    onClick={() => {
                      handleTransferStatusChange(
                        cancellationModal.transferId,
                        'cancel',
                        cancellationModal.reason.trim()
                          ? { cancellationReason: cancellationModal.reason.trim() }
                          : undefined
                      );
                      setCancellationModal({ show: false, transferId: null, reason: '' });
                    }}
                  >
                    <i className="fas fa-ban me-2"></i>
                    İptal Et
                  </button>
                </div>
              </div>
            </div>
          </div>
        )
      }

      {
        completionModal.show && (
          <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
            <div className="modal-dialog modal-dialog-centered">
              <div className="modal-content">
                <div className="modal-header bg-success text-white">
                  <h5 className="modal-title">
                    <i className="fas fa-clipboard-check me-2"></i>
                    Tamamlama Notu
                  </h5>
                  <button
                    type="button"
                    className="btn-close btn-close-white"
                    onClick={() => setCompletionModal({ show: false, transferId: null, note: '', message: '', transfer: null })}
                  ></button>
                </div>
                <div className="modal-body">
                  <div className="alert alert-info">
                    <i className="fas fa-info-circle me-2"></i>
                    {completionModal.message || 'Sevk tamamlanmadan önce teslimat durumunu ve varsa özel notları ekleyebilirsiniz.'}
                  </div>
                  {completionModal.transfer?.transferType === 'CUSTOMER_DELIVERY' && (
                    <div className="mb-3">
                      <div className="fw-bold">
                        <i className="fas fa-user-tag me-2 text-info"></i>
                        {completionModal.transfer.customerFullName}
                      </div>
                      <small className="text-muted d-block">
                        <i className="fas fa-phone me-1"></i>
                        {completionModal.transfer.customerPhone}
                      </small>
                      <small className="text-muted d-block">
                        <i className="fas fa-map-marker-alt me-1"></i>
                        {completionModal.transfer.customerAddress}
                      </small>
                    </div>
                  )}
                  <div className="mb-3">
                    <label htmlFor="completionNote" className="form-label">
                      <i className="fas fa-comment-alt me-1"></i>
                      Tamamlama Notu <span className="text-muted">(Opsiyonel)</span>
                    </label>
                    <textarea
                      id="completionNote"
                      className="form-control"
                      rows="4"
                      value={completionModal.note}
                      onChange={(e) => setCompletionModal({ ...completionModal, note: e.target.value })}
                      placeholder="Teslim alan kişi, adres detayları veya önemli gözlemler..."
                      maxLength="500"
                    />
                    <small className="text-muted">{completionModal.note.length}/500 karakter</small>
                  </div>
                </div>
                <div className="modal-footer">
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => setCompletionModal({ show: false, transferId: null, note: '', message: '', transfer: null })}
                  >
                    <i className="fas fa-times me-2"></i>
                    Vazgeç
                  </button>
                  <button
                    type="button"
                    className="btn btn-success"
                    onClick={() => {
                      handleTransferStatusChange(
                        completionModal.transferId,
                        'complete',
                        completionModal.note.trim()
                          ? { completionNote: completionModal.note.trim() }
                          : undefined
                      );
                      setCompletionModal({ show: false, transferId: null, note: '', message: '', transfer: null });
                    }}
                  >
                    <i className="fas fa-check me-2"></i>
                    Kaydet ve Tamamla
                  </button>
                </div>
              </div>
            </div>
          </div>
        )
      }

      {/* Audit Timeline Modal */}
      {
        auditModal.show && (
          <AuditTimelineModal
            entityType={auditModal.entityType}
            entityId={auditModal.entityId}
            onClose={() => setAuditModal({ show: false, entityType: null, entityId: null })}
          />
        )
      }

      {/* Stock Request Approval Modal */}
      {
        showApprovalModal && (
          <StockRequestApprovalModal
            onClose={() => setShowApprovalModal(false)}
            onApprove={() => {
              fetchAllData();
            }}
            initialTab={approvalModalTab}
          />
        )
      }
      {
        showMyRequestsModal && (
          <MyStockRequestsModal
            onClose={() => setShowMyRequestsModal(false)}
          />
        )
      }
    </div >
  );
};

export default Stock;
