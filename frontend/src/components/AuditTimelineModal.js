import React, { useEffect, useState, useMemo } from 'react';
import axios from 'axios';

// Hook to detect mobile screen
const useIsMobile = () => {
  const [isMobile, setIsMobile] = useState(window.innerWidth <= 576);
  const [isTablet, setIsTablet] = useState(window.innerWidth <= 768);

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth <= 576);
      setIsTablet(window.innerWidth <= 768);
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  return { isMobile, isTablet };
};

// Function to get responsive styles
const getStyles = (isMobile, isTablet) => ({
  modalOverlay: {
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    backdropFilter: 'blur(4px)',
    zIndex: 1050
  },
  modalDialog: {
    maxWidth: isMobile ? 'calc(100% - 1rem)' : '700px',
    margin: isMobile ? '0.5rem' : '1rem auto'
  },
  modalContent: {
    borderRadius: isMobile ? '12px' : '16px',
    border: 'none',
    boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
    overflow: 'hidden'
  },
  modalHeader: {
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    color: 'white',
    borderBottom: 'none',
    padding: isMobile ? '1rem' : '1.25rem 1.5rem'
  },
  modalTitle: {
    fontSize: isMobile ? '1rem' : '1.1rem',
    fontWeight: '600',
    display: 'flex',
    alignItems: 'center',
    gap: isMobile ? '0.5rem' : '0.75rem'
  },
  headerIcon: {
    width: isMobile ? '36px' : '40px',
    height: isMobile ? '36px' : '40px',
    borderRadius: '12px',
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: isMobile ? '1rem' : '1.1rem'
  },
  headerBadge: {
    backgroundColor: 'rgba(255, 255, 255, 0.25)',
    padding: '0.25rem 0.75rem',
    borderRadius: '20px',
    fontSize: isMobile ? '0.7rem' : '0.75rem',
    fontWeight: '500'
  },
  closeBtn: {
    filter: 'brightness(0) invert(1)',
    opacity: 0.8
  },
  modalBody: {
    padding: '0',
    maxHeight: isMobile ? 'calc(100vh - 160px)' : 'calc(100vh - 220px)',
    overflowY: 'auto'
  },
  timelineContainer: {
    padding: isMobile ? '0.75rem' : '1rem'
  },
  timelineItem: {
    display: 'flex',
    gap: isMobile ? '0.75rem' : '1rem',
    paddingBottom: isMobile ? '0.75rem' : '1rem',
    position: 'relative'
  },
  timelineLine: {
    content: '""',
    position: 'absolute',
    left: isMobile ? '15px' : '19px',
    top: isMobile ? '36px' : '44px',
    bottom: '0',
    width: '2px',
    backgroundColor: '#e5e7eb'
  },
  timelineIcon: {
    width: isMobile ? '32px' : '40px',
    height: isMobile ? '32px' : '40px',
    borderRadius: isMobile ? '10px' : '12px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
    fontSize: isMobile ? '0.85rem' : '1rem',
    zIndex: 1
  },
  timelineCard: {
    flex: 1,
    backgroundColor: '#f8fafc',
    borderRadius: isMobile ? '10px' : '12px',
    padding: isMobile ? '0.75rem' : '1rem',
    border: '1px solid #e2e8f0',
    transition: 'all 0.2s ease'
  },
  timelineCardHover: {
    backgroundColor: '#f1f5f9',
    borderColor: '#cbd5e1'
  },
  cardHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: isMobile ? 'flex-start' : 'flex-start',
    marginBottom: isMobile ? '0.5rem' : '0.75rem',
    flexWrap: 'wrap',
    gap: isMobile ? '0.375rem' : '0.5rem',
    flexDirection: isMobile ? 'column' : 'row'
  },
  actionBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: isMobile ? '0.375rem' : '0.5rem',
    padding: isMobile ? '0.3rem 0.6rem' : '0.375rem 0.75rem',
    borderRadius: '8px',
    fontSize: isMobile ? '0.75rem' : '0.8rem',
    fontWeight: '600',
    lineHeight: 1.2
  },
  timeInfo: {
    display: 'flex',
    flexDirection: isMobile ? 'row' : 'column',
    alignItems: isMobile ? 'center' : 'flex-end',
    gap: isMobile ? '0.5rem' : '0.125rem'
  },
  dateText: {
    fontSize: isMobile ? '0.7rem' : '0.75rem',
    color: '#64748b',
    fontWeight: '500'
  },
  timeText: {
    fontSize: isMobile ? '0.65rem' : '0.7rem',
    color: '#94a3b8'
  },
  userInfo: {
    display: 'flex',
    alignItems: 'center',
    gap: isMobile ? '0.375rem' : '0.5rem',
    marginBottom: isMobile ? '0.5rem' : '0.75rem',
    padding: isMobile ? '0.375rem 0.5rem' : '0.5rem 0.75rem',
    backgroundColor: 'white',
    borderRadius: '8px',
    border: '1px solid #e2e8f0'
  },
  userAvatar: {
    width: isMobile ? '24px' : '28px',
    height: isMobile ? '24px' : '28px',
    borderRadius: '8px',
    backgroundColor: '#e0e7ff',
    color: '#4f46e5',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: isMobile ? '0.65rem' : '0.75rem',
    fontWeight: '600'
  },
  userName: {
    fontSize: isMobile ? '0.75rem' : '0.8rem',
    fontWeight: '600',
    color: '#1e293b'
  },
  detailsGrid: {
    display: 'grid',
    gridTemplateColumns: isMobile ? '1fr' : (isTablet ? 'repeat(2, 1fr)' : 'repeat(auto-fit, minmax(140px, 1fr))'),
    gap: isMobile ? '0.375rem' : '0.5rem'
  },
  detailItem: {
    display: 'flex',
    alignItems: 'center',
    gap: isMobile ? '0.375rem' : '0.5rem',
    padding: isMobile ? '0.375rem 0.5rem' : '0.5rem 0.75rem',
    backgroundColor: 'white',
    borderRadius: '8px',
    border: '1px solid #e2e8f0',
    fontSize: isMobile ? '0.75rem' : '0.8rem'
  },
  detailIcon: {
    width: isMobile ? '20px' : '24px',
    height: isMobile ? '20px' : '24px',
    borderRadius: '6px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: isMobile ? '0.6rem' : '0.7rem',
    flexShrink: 0
  },
  detailLabel: {
    color: '#64748b',
    fontSize: isMobile ? '0.65rem' : '0.7rem',
    marginBottom: '0.125rem'
  },
  detailValue: {
    fontWeight: '600',
    color: '#1e293b',
    fontSize: isMobile ? '0.75rem' : '0.8rem',
    wordBreak: 'break-word'
  },
  quantityBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.375rem',
    padding: isMobile ? '0.375rem 0.625rem' : '0.5rem 0.875rem',
    borderRadius: '10px',
    fontSize: isMobile ? '0.8rem' : '0.9rem',
    fontWeight: '700'
  },
  transferArrow: {
    display: 'flex',
    alignItems: isMobile ? 'stretch' : 'center',
    justifyContent: 'center',
    padding: isMobile ? '0.625rem' : '0.75rem',
    backgroundColor: 'white',
    borderRadius: '10px',
    border: '1px solid #e2e8f0',
    gap: isMobile ? '0.5rem' : '0.75rem',
    flexWrap: 'wrap',
    flexDirection: isMobile ? 'column' : 'row'
  },
  warehouseBox: {
    display: 'flex',
    alignItems: 'center',
    gap: isMobile ? '0.375rem' : '0.5rem',
    padding: isMobile ? '0.375rem 0.625rem' : '0.5rem 0.75rem',
    backgroundColor: '#f1f5f9',
    borderRadius: '8px',
    fontSize: isMobile ? '0.75rem' : '0.8rem',
    fontWeight: '500',
    justifyContent: isMobile ? 'center' : 'flex-start',
    flex: isMobile ? '1' : 'none'
  },
  arrowIcon: {
    color: '#667eea',
    fontSize: isMobile ? '1rem' : '1.25rem',
    transform: isMobile ? 'rotate(90deg)' : 'none'
  },
  emptyState: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: isMobile ? '2rem 1rem' : '3rem 1.5rem',
    textAlign: 'center'
  },
  emptyIcon: {
    width: isMobile ? '48px' : '64px',
    height: isMobile ? '48px' : '64px',
    borderRadius: isMobile ? '12px' : '16px',
    backgroundColor: '#f1f5f9',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: isMobile ? '1.25rem' : '1.5rem',
    color: '#94a3b8',
    marginBottom: isMobile ? '0.75rem' : '1rem'
  },
  emptyText: {
    color: '#64748b',
    fontSize: isMobile ? '0.8rem' : '0.9rem'
  },
  loadingState: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: isMobile ? '2rem 1rem' : '3rem 1.5rem'
  },
  modalFooter: {
    borderTop: '1px solid #e2e8f0',
    padding: isMobile ? '0.75rem 1rem' : '1rem 1.5rem',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#f8fafc',
    flexDirection: isMobile ? 'column' : 'row',
    gap: isMobile ? '0.75rem' : '0'
  },
  statsInfo: {
    fontSize: isMobile ? '0.75rem' : '0.8rem',
    color: '#64748b',
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem'
  },
  closeButton: {
    padding: isMobile ? '0.5rem 1.25rem' : '0.5rem 1.5rem',
    borderRadius: '10px',
    fontWeight: '600',
    fontSize: isMobile ? '0.8rem' : '0.875rem',
    width: isMobile ? '100%' : 'auto'
  }
});

// Color scheme for different action types
const actionColors = {
  STOCK_CREATE: { bg: '#dbeafe', color: '#1d4ed8', iconBg: '#3b82f6' },
  STOCK_UPDATE: { bg: '#e0f2fe', color: '#0369a1', iconBg: '#0ea5e9' },
  STOCK_ADD: { bg: '#dcfce7', color: '#15803d', iconBg: '#22c55e' },
  STOCK_REMOVE: { bg: '#fee2e2', color: '#b91c1c', iconBg: '#ef4444' },
  STOCK_DELETE: { bg: '#f3f4f6', color: '#374151', iconBg: '#6b7280' },
  STOCK_RESERVE: { bg: '#fef3c7', color: '#b45309', iconBg: '#f59e0b' },
  STOCK_RELEASE: { bg: '#e0e7ff', color: '#4338ca', iconBg: '#6366f1' },
  TRANSFER_CREATE: { bg: '#dbeafe', color: '#1d4ed8', iconBg: '#3b82f6' },
  TRANSFER_START: { bg: '#cffafe', color: '#0e7490', iconBg: '#06b6d4' },
  TRANSFER_COMPLETE: { bg: '#dcfce7', color: '#15803d', iconBg: '#22c55e' },
  TRANSFER_CANCEL: { bg: '#fee2e2', color: '#b91c1c', iconBg: '#ef4444' },
  TRANSFER_UPDATE: { bg: '#e0f2fe', color: '#0369a1', iconBg: '#0ea5e9' },
  TRANSFER_DELETE: { bg: '#f3f4f6', color: '#374151', iconBg: '#6b7280' },
  TRANSFER_APPROVE: { bg: '#d1fae5', color: '#047857', iconBg: '#10b981' }
};

const AuditTimelineModal = ({ entityType, entityId, onClose }) => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [hoveredItem, setHoveredItem] = useState(null);
  
  // Get responsive values
  const { isMobile, isTablet } = useIsMobile();
  const styles = useMemo(() => getStyles(isMobile, isTablet), [isMobile, isTablet]);

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
      return {
        date: d.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'Europe/Istanbul' }),
        time: d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Istanbul' })
      };
    } catch {
      return { date: iso, time: '' };
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
      STOCK_RELEASE: 'Rez. Çözümü',
      TRANSFER_CREATE: 'Transfer Oluşturma',
      TRANSFER_START: 'Yola Çıkarma',
      TRANSFER_COMPLETE: 'Transfer Tamamlama',
      TRANSFER_CANCEL: 'Transfer İptali',
      TRANSFER_UPDATE: 'Transfer Güncelleme',
      TRANSFER_DELETE: 'Transfer Silme',
      TRANSFER_APPROVE: 'Transfer Onayı'
    };
    return map[action] || action;
  };

  const actionIcon = (action) => {
    const map = {
      STOCK_CREATE: 'fa-plus-circle',
      STOCK_UPDATE: 'fa-edit',
      STOCK_ADD: 'fa-arrow-up',
      STOCK_REMOVE: 'fa-arrow-down',
      STOCK_DELETE: 'fa-trash-alt',
      STOCK_RESERVE: 'fa-lock',
      STOCK_RELEASE: 'fa-unlock',
      TRANSFER_CREATE: 'fa-exchange-alt',
      TRANSFER_START: 'fa-truck',
      TRANSFER_COMPLETE: 'fa-check-circle',
      TRANSFER_CANCEL: 'fa-times-circle',
      TRANSFER_UPDATE: 'fa-edit',
      TRANSFER_DELETE: 'fa-trash-alt',
      TRANSFER_APPROVE: 'fa-clipboard-check'
    };
    return map[action] || 'fa-info-circle';
  };

  // Parse product list from strings like "1 x Product A, 2 x Product B"
  const parseProductList = (productsStr) => {
    if (!productsStr) return [];
    // Split by comma, but be careful with commas inside product names
    const parts = productsStr.split(/,\s*(?=\d+\s*x\s)/);
    return parts.map(part => {
      const match = part.match(/^(\d+)\s*x\s*(.+)$/);
      if (match) {
        return { qty: parseInt(match[1], 10), name: match[2].trim() };
      }
      return { qty: 1, name: part.trim() };
    }).filter(p => p.name);
  };

  const translateDetails = (details, action) => {
    if (!details || typeof details !== 'string') return details;
    
    // Handle multi-product transfer formats first
    // Format: "Transfer tamamlandı: SRC -> DST | Ürünler=1 x Product, 2 x Product"
    let m = details.match(/^Transfer (tamamlandı|onaylandı|oluşturuldu|başlatıldı|iptal edildi):\s*(.*?)\s*->\s*(.*?)\s*\|\s*Ürünler=(.+)$/);
    if (m) {
      const statusMap = {
        'tamamlandı': 'complete_multi',
        'onaylandı': 'approve_multi',
        'oluşturuldu': 'create_multi',
        'başlatıldı': 'start_multi',
        'iptal edildi': 'cancel_multi'
      };
      return JSON.stringify({
        _type: 'transfer_multi',
        kind: statusMap[m[1]] || 'complete_multi',
        src: m[2].trim(),
        dst: m[3].trim(),
        products: parseProductList(m[4])
      });
    }

    // Handle simpler multi-product format
    // Format: "SRC -> DST | Ürünler=1 x Product, 2 x Product"
    m = details.match(/^(.*?)\s*->\s*(.*?)\s*\|\s*Ürünler=(.+)$/);
    if (m) {
      return JSON.stringify({
        _type: 'transfer_multi',
        kind: action === 'TRANSFER_APPROVE' ? 'approve_multi' : 
              action === 'TRANSFER_COMPLETE' ? 'complete_multi' :
              action === 'TRANSFER_START' ? 'start_multi' :
              action === 'TRANSFER_CREATE' ? 'create_multi' : 'complete_multi',
        src: m[1].trim(),
        dst: m[2].trim(),
        products: parseProductList(m[3])
      });
    }

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
          if (action === 'TRANSFER_CREATE') {
            return `Transfer oluşturma: ${m[1]} → ${m[2]} | Ürün=${m[3]} | Adet=${m[4]}`;
          }
          return details;
        }
      }
    ];
    for (const p of patterns) {
      const match = details.match(p.re);
      if (match) return p.fn(match);
    }
    return details;
  };

  const parseStockChange = (details) => {
    if (!details) return null;
    
    // Format: "Stok artırma: +100 adet → Yeni=200 | Depo=X, Ürün=Y"
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
    
    // Format: "Stok artırıldı: -100 adet → Yeni=100 | Depo=X, Ürün=Y"
    m = details.match(/^Stok (artırıldı|azaltıldı): ([+-]?\d+) adet → Yeni=(\d+) \| Depo=(.*), Ürün=(.*)$/);
    if (m) {
      return {
        type: m[1] === 'artırıldı' ? 'add' : 'remove',
        delta: parseInt(m[2], 10),
        newQty: parseInt(m[3], 10),
        warehouse: m[4],
        product: m[5]
      };
    }
    
    // Format with Önceki: "Stok azaltıldı: -100 adet (Önceki=200, Yeni=100) | Depo=X, Ürün=Y"
    m = details.match(/^Stok (artırıldı|azaltıldı): ([+-]?\d+) adet \(Önceki=(\d+),?\s*Yeni=(\d+)\) \| Depo=(.*), Ürün=(.*)$/);
    if (m) {
      return {
        type: m[1] === 'artırıldı' ? 'add' : 'remove',
        delta: parseInt(m[2], 10),
        prevQty: parseInt(m[3], 10),
        newQty: parseInt(m[4], 10),
        warehouse: m[5],
        product: m[6]
      };
    }
    
    // Simple format: "Stok azaltıldı: DEPO | Ürün=X | Miktar=-100 | Yeni=100"
    m = details.match(/^Stok (artırıldı|azaltıldı): (.*?) \| Ürün=(.*?) \| Miktar=([+-]?\d+) \| Yeni=(\d+)$/);
    if (m) {
      return {
        type: m[1] === 'artırıldı' ? 'add' : 'remove',
        warehouse: m[2],
        product: m[3],
        delta: parseInt(m[4], 10),
        newQty: parseInt(m[5], 10)
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
    // Parse stock delete - multiple formats
    m = details.match(/^Stok (silme|silindi): Depo=(.*), Ürün=(.*)$/);
    if (m) {
      return { type: 'delete', warehouse: m[2], product: m[3] };
    }
    
    // Parse stock update with changes - new detailed format
    // Format: "Stok güncellendi: Depo=X, Ürün=Y | Değişiklikler: Min Stok: 10 → 20, Müşteri Adı: Ali → Veli"
    m = details.match(/^Stok (güncelleme|güncellendi): Depo=(.*), Ürün=(.*) \| Değişiklikler: (.+)$/);
    if (m) {
      const changesStr = m[4];
      const changes = [];
      // Parse each change: "Field: old → new"
      const changePattern = /([^:]+):\s*([^→]+?)\s*→\s*([^,]+)/g;
      let changeMatch;
      while ((changeMatch = changePattern.exec(changesStr)) !== null) {
        changes.push({
          field: changeMatch[1].trim(),
          oldValue: changeMatch[2].trim(),
          newValue: changeMatch[3].trim()
        });
      }
      return { 
        type: 'update', 
        warehouse: m[2], 
        product: m[3],
        changes: changes.length > 0 ? changes : null
      };
    }
    
    // Parse stock update - simple format (old format without changes)
    m = details.match(/^Stok (güncelleme|güncellendi): Depo=(.*), Ürün=(.*)$/);
    if (m) {
      return { type: 'update', warehouse: m[2], product: m[3] };
    }
    
    // Parse stock create
    m = details.match(/^Stok (oluşturma|oluşturuldu): Depo=(.*), Ürün=(.*)$/);
    if (m) {
      return { type: 'create', warehouse: m[2], product: m[3] };
    }
    
    // Generic format: "Stok XXX: Depo=Y, Ürün=Z"
    m = details.match(/^Stok \w+: Depo=(.*), Ürün=(.*)$/);
    if (m) {
      return { type: 'update', warehouse: m[1], product: m[2] };
    }
    
    // Fallback: Try to parse any stock-related detail with common patterns
    // Format: "... adet ... Depo=X ... Ürün=Y"
    m = details.match(/([+-]?\d+)\s*adet.*?(?:→|->)?\s*(?:Yeni=)?(\d+)?.*?Depo=([^,|]+).*?Ürün=(.+)$/i);
    if (m) {
      const delta = parseInt(m[1], 10);
      return {
        type: delta >= 0 ? 'add' : 'remove',
        delta: delta,
        newQty: m[2] ? parseInt(m[2], 10) : null,
        warehouse: m[3].trim(),
        product: m[4].trim()
      };
    }
    
    return null;
  };

  const parseTransferChange = (details) => {
    if (!details) return null;
    
    // Check if it's a JSON multi-product transfer
    if (details.startsWith('{') && details.includes('_type')) {
      try {
        const parsed = JSON.parse(details);
        if (parsed._type === 'transfer_multi') {
          return parsed;
        }
      } catch (e) {
        // Not valid JSON, continue with regex parsing
      }
    }
    
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

  const getUserInitials = (username) => {
    if (!username) return '?';
    return username.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
  };

  const renderStockDetails = (parsed) => {
    if (parsed.type === 'add' || parsed.type === 'remove') {
      const isAdd = parsed.type === 'add';
      const absoluteDelta = Math.abs(parsed.delta);
      
      return (
        <div>
          {/* Product Info Card */}
          <div style={{ 
            backgroundColor: 'white', 
            borderRadius: '10px', 
            border: '1px solid #e2e8f0',
            overflow: 'hidden',
            marginBottom: '0.5rem'
          }}>
            {/* Product Header */}
            <div style={{
              padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
              backgroundColor: '#f8fafc',
              borderBottom: '1px solid #e2e8f0',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem'
            }}>
              <div style={{
                width: isMobile ? '28px' : '32px',
                height: isMobile ? '28px' : '32px',
                borderRadius: '8px',
                backgroundColor: '#e0f2fe',
                color: '#0369a1',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: isMobile ? '0.7rem' : '0.8rem'
              }}>
                <i className="fas fa-box"></i>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontSize: isMobile ? '0.7rem' : '0.75rem',
                  color: '#64748b',
                  marginBottom: '0.125rem'
                }}>Ürün</div>
                <div style={{
                  fontSize: isMobile ? '0.8rem' : '0.85rem',
                  fontWeight: '600',
                  color: '#1e293b',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis'
                }}>{parsed.product}</div>
              </div>
            </div>
            
            {/* Warehouse */}
            <div style={{
              padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              borderBottom: '1px solid #f1f5f9'
            }}>
              <div style={{
                width: isMobile ? '28px' : '32px',
                height: isMobile ? '28px' : '32px',
                borderRadius: '8px',
                backgroundColor: '#fef3c7',
                color: '#b45309',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: isMobile ? '0.7rem' : '0.8rem'
              }}>
                <i className="fas fa-warehouse"></i>
              </div>
              <div>
                <div style={{
                  fontSize: isMobile ? '0.7rem' : '0.75rem',
                  color: '#64748b',
                  marginBottom: '0.125rem'
                }}>Depo</div>
                <div style={{
                  fontSize: isMobile ? '0.8rem' : '0.85rem',
                  fontWeight: '600',
                  color: '#1e293b'
                }}>{parsed.warehouse}</div>
              </div>
            </div>
          </div>
          
          {/* Quantity Change Card */}
          <div style={{
            backgroundColor: isAdd ? '#f0fdf4' : '#fef2f2',
            borderRadius: '10px',
            border: `1px solid ${isAdd ? '#bbf7d0' : '#fecaca'}`,
            padding: isMobile ? '0.75rem' : '1rem'
          }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: '0.75rem'
            }}>
              {/* Change Amount */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem'
              }}>
                <div style={{
                  width: isMobile ? '36px' : '42px',
                  height: isMobile ? '36px' : '42px',
                  borderRadius: '10px',
                  backgroundColor: isAdd ? '#dcfce7' : '#fee2e2',
                  color: isAdd ? '#15803d' : '#b91c1c',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: isMobile ? '1rem' : '1.1rem'
                }}>
                  <i className={`fas ${isAdd ? 'fa-arrow-up' : 'fa-arrow-down'}`}></i>
                </div>
                <div>
                  <div style={{
                    fontSize: isMobile ? '0.7rem' : '0.75rem',
                    color: '#64748b',
                    marginBottom: '0.125rem'
                  }}>Değişim</div>
                  <div style={{
                    fontSize: isMobile ? '1.1rem' : '1.25rem',
                    fontWeight: '700',
                    color: isAdd ? '#15803d' : '#b91c1c'
                  }}>
                    {isAdd ? '+' : '-'}{absoluteDelta} adet
                  </div>
                </div>
              </div>
              
              {/* Arrow */}
              <div style={{
                color: '#94a3b8',
                fontSize: isMobile ? '1rem' : '1.25rem'
              }}>
                <i className="fas fa-long-arrow-alt-right"></i>
              </div>
              
              {/* New Quantity */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem'
              }}>
                <div style={{
                  width: isMobile ? '36px' : '42px',
                  height: isMobile ? '36px' : '42px',
                  borderRadius: '10px',
                  backgroundColor: '#e0e7ff',
                  color: '#4f46e5',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: isMobile ? '1rem' : '1.1rem'
                }}>
                  <i className="fas fa-cubes"></i>
                </div>
                <div>
                  <div style={{
                    fontSize: isMobile ? '0.7rem' : '0.75rem',
                    color: '#64748b',
                    marginBottom: '0.125rem'
                  }}>Yeni Miktar</div>
                  <div style={{
                    fontSize: isMobile ? '1.1rem' : '1.25rem',
                    fontWeight: '700',
                    color: '#4f46e5'
                  }}>
                    {parsed.newQty !== null ? `${parsed.newQty} adet` : '-'}
                  </div>
                </div>
              </div>
            </div>
            
            {/* Previous quantity if available */}
            {parsed.prevQty !== undefined && (
              <div style={{
                marginTop: '0.75rem',
                paddingTop: '0.75rem',
                borderTop: `1px solid ${isAdd ? '#bbf7d0' : '#fecaca'}`,
                fontSize: isMobile ? '0.75rem' : '0.8rem',
                color: '#64748b',
                display: 'flex',
                alignItems: 'center',
                gap: '0.375rem'
              }}>
                <i className="fas fa-history"></i>
                Önceki miktar: <strong style={{ color: '#475569' }}>{parsed.prevQty} adet</strong>
              </div>
            )}
          </div>
        </div>
      );
    }

    if (parsed.type === 'reserve' || parsed.type === 'release') {
      const isReserve = parsed.type === 'reserve';
      
      return (
        <div>
          {/* Product Info Card */}
          <div style={{ 
            backgroundColor: 'white', 
            borderRadius: '10px', 
            border: '1px solid #e2e8f0',
            overflow: 'hidden',
            marginBottom: '0.5rem'
          }}>
            {/* Product Header */}
            <div style={{
              padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
              backgroundColor: '#f8fafc',
              borderBottom: '1px solid #e2e8f0',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem'
            }}>
              <div style={{
                width: isMobile ? '28px' : '32px',
                height: isMobile ? '28px' : '32px',
                borderRadius: '8px',
                backgroundColor: '#e0f2fe',
                color: '#0369a1',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: isMobile ? '0.7rem' : '0.8rem'
              }}>
                <i className="fas fa-box"></i>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontSize: isMobile ? '0.7rem' : '0.75rem',
                  color: '#64748b',
                  marginBottom: '0.125rem'
                }}>Ürün</div>
                <div style={{
                  fontSize: isMobile ? '0.8rem' : '0.85rem',
                  fontWeight: '600',
                  color: '#1e293b',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis'
                }}>{parsed.product}</div>
              </div>
            </div>
            
            {/* Warehouse */}
            <div style={{
              padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem'
            }}>
              <div style={{
                width: isMobile ? '28px' : '32px',
                height: isMobile ? '28px' : '32px',
                borderRadius: '8px',
                backgroundColor: '#fef3c7',
                color: '#b45309',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: isMobile ? '0.7rem' : '0.8rem'
              }}>
                <i className="fas fa-warehouse"></i>
              </div>
              <div>
                <div style={{
                  fontSize: isMobile ? '0.7rem' : '0.75rem',
                  color: '#64748b',
                  marginBottom: '0.125rem'
                }}>Depo</div>
                <div style={{
                  fontSize: isMobile ? '0.8rem' : '0.85rem',
                  fontWeight: '600',
                  color: '#1e293b'
                }}>{parsed.warehouse}</div>
              </div>
            </div>
          </div>
          
          {/* Reservation Info Card */}
          <div style={{
            backgroundColor: isReserve ? '#fffbeb' : '#f0f9ff',
            borderRadius: '10px',
            border: `1px solid ${isReserve ? '#fde68a' : '#bae6fd'}`,
            padding: isMobile ? '0.75rem' : '1rem'
          }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: '0.75rem'
            }}>
              {/* Reserved/Released Amount */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem'
              }}>
                <div style={{
                  width: isMobile ? '36px' : '42px',
                  height: isMobile ? '36px' : '42px',
                  borderRadius: '10px',
                  backgroundColor: isReserve ? '#fef3c7' : '#e0e7ff',
                  color: isReserve ? '#b45309' : '#4f46e5',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: isMobile ? '1rem' : '1.1rem'
                }}>
                  <i className={`fas ${isReserve ? 'fa-lock' : 'fa-unlock'}`}></i>
                </div>
                <div>
                  <div style={{
                    fontSize: isMobile ? '0.7rem' : '0.75rem',
                    color: '#64748b',
                    marginBottom: '0.125rem'
                  }}>{isReserve ? 'Ayrılan Miktar' : 'Serbest Bırakılan'}</div>
                  <div style={{
                    fontSize: isMobile ? '1.1rem' : '1.25rem',
                    fontWeight: '700',
                    color: isReserve ? '#b45309' : '#4f46e5'
                  }}>
                    {parsed.amount} adet
                  </div>
                </div>
              </div>
              
              {/* Total Reserved */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem'
              }}>
                <div style={{
                  width: isMobile ? '36px' : '42px',
                  height: isMobile ? '36px' : '42px',
                  borderRadius: '10px',
                  backgroundColor: '#f3f4f6',
                  color: '#6b7280',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: isMobile ? '1rem' : '1.1rem'
                }}>
                  <i className="fas fa-layer-group"></i>
                </div>
                <div>
                  <div style={{
                    fontSize: isMobile ? '0.7rem' : '0.75rem',
                    color: '#64748b',
                    marginBottom: '0.125rem'
                  }}>Toplam Ayrılan</div>
                  <div style={{
                    fontSize: isMobile ? '1.1rem' : '1.25rem',
                    fontWeight: '700',
                    color: '#374151'
                  }}>
                    {parsed.totalReserved} adet
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      );
    }

    if (parsed.type === 'update' && parsed.changes && parsed.changes.length > 0) {
      // Detailed update with changes
      return (
        <div>
          {/* Product and Warehouse Info */}
          <div style={{ 
            backgroundColor: 'white', 
            borderRadius: '10px', 
            border: '1px solid #e2e8f0',
            overflow: 'hidden',
            marginBottom: '0.5rem'
          }}>
            {/* Product */}
            <div style={{
              padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
              backgroundColor: '#f8fafc',
              borderBottom: '1px solid #e2e8f0',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem'
            }}>
              <div style={{
                width: isMobile ? '28px' : '32px',
                height: isMobile ? '28px' : '32px',
                borderRadius: '8px',
                backgroundColor: '#e0f2fe',
                color: '#0369a1',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: isMobile ? '0.7rem' : '0.8rem'
              }}>
                <i className="fas fa-box"></i>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  fontSize: isMobile ? '0.7rem' : '0.75rem',
                  color: '#64748b',
                  marginBottom: '0.125rem'
                }}>Ürün</div>
                <div style={{
                  fontSize: isMobile ? '0.8rem' : '0.85rem',
                  fontWeight: '600',
                  color: '#1e293b',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis'
                }}>{parsed.product}</div>
              </div>
            </div>
            
            {/* Warehouse */}
            <div style={{
              padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem'
            }}>
              <div style={{
                width: isMobile ? '28px' : '32px',
                height: isMobile ? '28px' : '32px',
                borderRadius: '8px',
                backgroundColor: '#fef3c7',
                color: '#b45309',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: isMobile ? '0.7rem' : '0.8rem'
              }}>
                <i className="fas fa-warehouse"></i>
              </div>
              <div>
                <div style={{
                  fontSize: isMobile ? '0.7rem' : '0.75rem',
                  color: '#64748b',
                  marginBottom: '0.125rem'
                }}>Depo</div>
                <div style={{
                  fontSize: isMobile ? '0.8rem' : '0.85rem',
                  fontWeight: '600',
                  color: '#1e293b'
                }}>{parsed.warehouse}</div>
              </div>
            </div>
          </div>
          
          {/* Changes List */}
          <div style={{
            backgroundColor: '#f0f9ff',
            borderRadius: '10px',
            border: '1px solid #bae6fd',
            overflow: 'hidden'
          }}>
            <div style={{
              padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
              backgroundColor: '#e0f2fe',
              borderBottom: '1px solid #bae6fd',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              fontSize: isMobile ? '0.7rem' : '0.75rem',
              fontWeight: '600',
              color: '#0369a1'
            }}>
              <i className="fas fa-edit"></i>
              Değişiklikler ({parsed.changes.length})
            </div>
            <div>
              {parsed.changes.map((change, idx) => {
                const isNumeric = /^\d+$/.test(change.oldValue) && /^\d+$/.test(change.newValue);
                const isEmpty = (val) => val === '(boş)' || val === '';
                return (
                  <div 
                    key={idx}
                    style={{
                      padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
                      borderBottom: idx < parsed.changes.length - 1 ? '1px solid #e0f2fe' : 'none',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.75rem'
                    }}
                  >
                    <div style={{
                      width: isMobile ? '28px' : '32px',
                      height: isMobile ? '28px' : '32px',
                      borderRadius: '8px',
                      backgroundColor: '#dbeafe',
                      color: '#1d4ed8',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: isMobile ? '0.7rem' : '0.8rem',
                      flexShrink: 0
                    }}>
                      <i className="fas fa-arrow-right"></i>
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{
                        fontSize: isMobile ? '0.7rem' : '0.75rem',
                        color: '#64748b',
                        marginBottom: '0.25rem',
                        fontWeight: '500'
                      }}>{change.field}</div>
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.5rem',
                        flexWrap: 'wrap'
                      }}>
                        <span style={{
                          fontSize: isMobile ? '0.75rem' : '0.8rem',
                          color: isEmpty(change.oldValue) ? '#94a3b8' : '#475569',
                          textDecoration: isEmpty(change.oldValue) ? 'line-through' : 'none'
                        }}>
                          {change.oldValue}
                        </span>
                        <i className="fas fa-long-arrow-alt-right" style={{ 
                          color: '#94a3b8',
                          fontSize: isMobile ? '0.7rem' : '0.75rem'
                        }}></i>
                        <span style={{
                          fontSize: isMobile ? '0.75rem' : '0.8rem',
                          fontWeight: '600',
                          color: isEmpty(change.newValue) ? '#94a3b8' : (isNumeric ? '#059669' : '#1e293b')
                        }}>
                          {change.newValue}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      );
    }

    if (parsed.type === 'delete' || parsed.type === 'update' || parsed.type === 'create') {
      return (
        <div style={{ 
          backgroundColor: 'white', 
          borderRadius: '10px', 
          border: '1px solid #e2e8f0',
          overflow: 'hidden'
        }}>
          {/* Product */}
          <div style={{
            padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
            backgroundColor: '#f8fafc',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem'
          }}>
            <div style={{
              width: isMobile ? '28px' : '32px',
              height: isMobile ? '28px' : '32px',
              borderRadius: '8px',
              backgroundColor: '#e0f2fe',
              color: '#0369a1',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: isMobile ? '0.7rem' : '0.8rem'
            }}>
              <i className="fas fa-box"></i>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{
                fontSize: isMobile ? '0.7rem' : '0.75rem',
                color: '#64748b',
                marginBottom: '0.125rem'
              }}>Ürün</div>
              <div style={{
                fontSize: isMobile ? '0.8rem' : '0.85rem',
                fontWeight: '600',
                color: '#1e293b',
                overflow: 'hidden',
                textOverflow: 'ellipsis'
              }}>{parsed.product}</div>
            </div>
          </div>
          
          {/* Warehouse */}
          <div style={{
            padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem'
          }}>
            <div style={{
              width: isMobile ? '28px' : '32px',
              height: isMobile ? '28px' : '32px',
              borderRadius: '8px',
              backgroundColor: '#fef3c7',
              color: '#b45309',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: isMobile ? '0.7rem' : '0.8rem'
            }}>
              <i className="fas fa-warehouse"></i>
            </div>
            <div>
              <div style={{
                fontSize: isMobile ? '0.7rem' : '0.75rem',
                color: '#64748b',
                marginBottom: '0.125rem'
              }}>Depo</div>
              <div style={{
                fontSize: isMobile ? '0.8rem' : '0.85rem',
                fontWeight: '600',
                color: '#1e293b'
              }}>{parsed.warehouse}</div>
            </div>
          </div>
        </div>
      );
    }

    return null;
  };

  const renderTransferDetails = (parsed) => {
    if (parsed.kind === 'cancel' || parsed.kind === 'cancel_multi') {
      return (
        <div style={styles.detailItem}>
          <div style={{ ...styles.detailIcon, backgroundColor: '#fee2e2', color: '#b91c1c' }}>
            <i className="fas fa-ban"></i>
          </div>
          <div>
            <div style={styles.detailLabel}>İptal Sebebi</div>
            <div style={styles.detailValue}>{parsed.reason}</div>
          </div>
        </div>
      );
    }

    // Multi-product transfer
    if (parsed._type === 'transfer_multi' && parsed.products) {
      return (
        <div>
          {/* Source -> Destination */}
          <div style={styles.transferArrow}>
            <div style={styles.warehouseBox}>
              <i className="fas fa-warehouse" style={{ color: '#ef4444' }}></i>
              <span>{parsed.src}</span>
            </div>
            <i className="fas fa-long-arrow-alt-right" style={styles.arrowIcon}></i>
            <div style={styles.warehouseBox}>
              <i className="fas fa-warehouse" style={{ color: '#22c55e' }}></i>
              <span>{parsed.dst}</span>
            </div>
          </div>
          
          {/* Products List */}
          <div style={{ 
            marginTop: '0.75rem',
            backgroundColor: 'white',
            borderRadius: '10px',
            border: '1px solid #e2e8f0',
            overflow: 'hidden'
          }}>
            <div style={{
              padding: '0.5rem 0.75rem',
              backgroundColor: '#f8fafc',
              borderBottom: '1px solid #e2e8f0',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              fontSize: isMobile ? '0.7rem' : '0.75rem',
              fontWeight: '600',
              color: '#475569'
            }}>
              <i className="fas fa-boxes" style={{ color: '#6366f1' }}></i>
              Ürünler ({parsed.products.length})
            </div>
            <div style={{ maxHeight: '200px', overflowY: 'auto' }}>
              {parsed.products.map((product, idx) => (
                <div 
                  key={idx}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.75rem',
                    padding: isMobile ? '0.5rem 0.75rem' : '0.625rem 0.75rem',
                    borderBottom: idx < parsed.products.length - 1 ? '1px solid #f1f5f9' : 'none',
                    transition: 'background-color 0.15s ease'
                  }}
                >
                  <div style={{
                    width: isMobile ? '28px' : '32px',
                    height: isMobile ? '28px' : '32px',
                    borderRadius: '8px',
                    backgroundColor: '#e0f2fe',
                    color: '#0369a1',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: isMobile ? '0.7rem' : '0.8rem',
                    flexShrink: 0
                  }}>
                    <i className="fas fa-box"></i>
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      fontSize: isMobile ? '0.75rem' : '0.8rem',
                      fontWeight: '500',
                      color: '#1e293b',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis'
                    }}>
                      {product.name}
                    </div>
                  </div>
                  <div style={{
                    padding: '0.25rem 0.5rem',
                    backgroundColor: '#dbeafe',
                    color: '#1d4ed8',
                    borderRadius: '6px',
                    fontSize: isMobile ? '0.7rem' : '0.75rem',
                    fontWeight: '600',
                    whiteSpace: 'nowrap'
                  }}>
                    {product.qty} adet
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      );
    }

    // Single product transfer (legacy format)
    return (
      <div>
        <div style={styles.transferArrow}>
          <div style={styles.warehouseBox}>
            <i className="fas fa-warehouse" style={{ color: '#ef4444' }}></i>
            <span>{parsed.src}</span>
          </div>
          <i className="fas fa-long-arrow-alt-right" style={styles.arrowIcon}></i>
          <div style={styles.warehouseBox}>
            <i className="fas fa-warehouse" style={{ color: '#22c55e' }}></i>
            <span>{parsed.dst}</span>
          </div>
        </div>
        <div style={{ ...styles.detailsGrid, marginTop: '0.75rem' }}>
          <div style={styles.detailItem}>
            <div style={{ ...styles.detailIcon, backgroundColor: '#e0f2fe', color: '#0369a1' }}>
              <i className="fas fa-box"></i>
            </div>
            <div>
              <div style={styles.detailLabel}>Ürün</div>
              <div style={styles.detailValue}>{parsed.product}</div>
            </div>
          </div>
          <div style={styles.detailItem}>
            <div style={{ ...styles.detailIcon, backgroundColor: '#dbeafe', color: '#1d4ed8' }}>
              <i className="fas fa-cubes"></i>
            </div>
            <div>
              <div style={styles.detailLabel}>Miktar</div>
              <div style={styles.detailValue}>{parsed.qty} adet</div>
            </div>
          </div>
        </div>
      </div>
    );
  };

  // Try to parse any remaining raw transfer details
  const parseRawTransferDetails = (details) => {
    if (!details || typeof details !== 'string') return null;
    
    // Try to match raw format like "DEPO1 -> DEPO2 | Ürünler=1 x Ürün A, 2 x Ürün B"
    let m = details.match(/^(.*?)\s*->\s*(.*?)\s*\|\s*Ürünler=(.+)$/);
    if (m) {
      const products = [];
      const productsStr = m[3];
      const parts = productsStr.split(/,\s*(?=\d+\s*x\s)/);
      parts.forEach(part => {
        const match = part.match(/^(\d+)\s*x\s*(.+)$/);
        if (match) {
          products.push({ qty: parseInt(match[1], 10), name: match[2].trim() });
        } else {
          products.push({ qty: 1, name: part.trim() });
        }
      });
      return {
        _type: 'transfer_multi',
        kind: 'complete_multi',
        src: m[1].trim(),
        dst: m[2].trim(),
        products: products.filter(p => p.name)
      };
    }
    
    // Try to match format like "Transfer tamamlandı: DEPO1 -> DEPO2 | Ürünler=..."
    m = details.match(/^Transfer\s+\w+:\s*(.*?)\s*->\s*(.*?)\s*\|\s*Ürünler=(.+)$/);
    if (m) {
      const products = [];
      const productsStr = m[3];
      const parts = productsStr.split(/,\s*(?=\d+\s*x\s)/);
      parts.forEach(part => {
        const match = part.match(/^(\d+)\s*x\s*(.+)$/);
        if (match) {
          products.push({ qty: parseInt(match[1], 10), name: match[2].trim() });
        } else {
          products.push({ qty: 1, name: part.trim() });
        }
      });
      return {
        _type: 'transfer_multi',
        kind: 'complete_multi',
        src: m[1].trim(),
        dst: m[2].trim(),
        products: products.filter(p => p.name)
      };
    }
    
    return null;
  };

  const renderDetails = (details) => {
    const stockParsed = parseStockChange(details);
    if (stockParsed) return renderStockDetails(stockParsed);
    
    const transferParsed = parseTransferChange(details);
    if (transferParsed) return renderTransferDetails(transferParsed);
    
    // Try raw transfer parsing as fallback
    const rawTransferParsed = parseRawTransferDetails(details);
    if (rawTransferParsed) return renderTransferDetails(rawTransferParsed);
    
    if (details) {
      // Check if it looks like a transfer detail and format it better
      if (details.includes('->') && details.includes('|')) {
        const parts = details.split('|').map(p => p.trim());
        return (
          <div style={{ 
            backgroundColor: 'white', 
            borderRadius: '8px', 
            border: '1px solid #e2e8f0',
            overflow: 'hidden'
          }}>
            {parts.map((part, idx) => (
              <div 
                key={idx}
                style={{
                  padding: '0.5rem 0.75rem',
                  fontSize: isMobile ? '0.75rem' : '0.8rem',
                  color: '#475569',
                  borderBottom: idx < parts.length - 1 ? '1px solid #f1f5f9' : 'none',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem'
                }}
              >
                {part.includes('->') ? (
                  <>
                    <i className="fas fa-route" style={{ color: '#6366f1' }}></i>
                    <span>{part}</span>
                  </>
                ) : part.toLowerCase().includes('ürün') ? (
                  <>
                    <i className="fas fa-box" style={{ color: '#0ea5e9' }}></i>
                    <span>{part}</span>
                  </>
                ) : (
                  <span>{part}</span>
                )}
              </div>
            ))}
          </div>
        );
      }
      
      return (
        <div style={{ 
          padding: '0.75rem', 
          backgroundColor: 'white', 
          borderRadius: '8px', 
          border: '1px solid #e2e8f0',
          fontSize: isMobile ? '0.75rem' : '0.85rem',
          color: '#475569'
        }}>
          {details}
        </div>
      );
    }
    
    return null;
  };

  const label = entityType === 'Stock' ? 'Stok' : (entityType === 'StockTransfer' ? 'Transfer' : entityType);
  const headerIconClass = entityType === 'Stock' ? 'fa-boxes' : 'fa-exchange-alt';

  return (
    <div className="modal show d-block" tabIndex="-1" style={styles.modalOverlay}>
      <div className="modal-dialog modal-dialog-scrollable" style={styles.modalDialog}>
        <div className="modal-content" style={styles.modalContent}>
          {/* Header */}
          <div className="modal-header" style={styles.modalHeader}>
            <div style={styles.modalTitle}>
              <div style={styles.headerIcon}>
                <i className={`fas ${headerIconClass}`}></i>
              </div>
              <div>
                <div>Hareket Geçmişi</div>
                <div style={styles.headerBadge}>{label} #{entityId}</div>
              </div>
            </div>
            <button 
              type="button" 
              className="btn-close" 
              onClick={onClose}
              style={styles.closeBtn}
            ></button>
          </div>

          {/* Body */}
          <div className="modal-body" style={styles.modalBody}>
            {loading && (
              <div style={styles.loadingState}>
                <div className="spinner-border text-primary mb-3" role="status">
                  <span className="visually-hidden">Yükleniyor...</span>
                </div>
                <div style={{ color: '#64748b', fontSize: '0.9rem' }}>Hareket geçmişi yükleniyor...</div>
              </div>
            )}

            {error && (
              <div className="alert alert-danger m-3" role="alert">
                <i className="fas fa-exclamation-circle me-2"></i>
                {error}
              </div>
            )}

            {!loading && !error && logs.length === 0 && (
              <div style={styles.emptyState}>
                <div style={styles.emptyIcon}>
                  <i className="fas fa-inbox"></i>
                </div>
                <div style={styles.emptyText}>Henüz kayıtlı hareket bulunmuyor.</div>
              </div>
            )}

            {!loading && !error && logs.length > 0 && (
              <div style={styles.timelineContainer}>
                {logs.map((log, index) => {
                  const colors = actionColors[log.action] || { bg: '#f3f4f6', color: '#374151', iconBg: '#6b7280' };
                  const dateInfo = formatDate(log.createdAt);
                  const isLastItem = index === logs.length - 1;
                  const translatedDetails = translateDetails(log.details, log.action);

                  return (
                    <div 
                      key={log.id} 
                      style={styles.timelineItem}
                      onMouseEnter={() => setHoveredItem(log.id)}
                      onMouseLeave={() => setHoveredItem(null)}
                    >
                      {/* Timeline line */}
                      {!isLastItem && <div style={styles.timelineLine}></div>}
                      
                      {/* Timeline icon */}
                      <div 
                        style={{
                          ...styles.timelineIcon,
                          backgroundColor: colors.iconBg,
                          color: 'white'
                        }}
                      >
                        <i className={`fas ${actionIcon(log.action)}`}></i>
                      </div>

                      {/* Timeline card */}
                      <div 
                        style={{
                          ...styles.timelineCard,
                          ...(hoveredItem === log.id ? styles.timelineCardHover : {})
                        }}
                      >
                        {/* Card header */}
                        <div style={styles.cardHeader}>
                          <span style={{
                            ...styles.actionBadge,
                            backgroundColor: colors.bg,
                            color: colors.color
                          }}>
                            <i className={`fas ${actionIcon(log.action)}`}></i>
                            {actionLabel(log.action)}
                          </span>
                          <div style={styles.timeInfo}>
                            <span style={styles.dateText}>{dateInfo.date}</span>
                            <span style={styles.timeText}>{dateInfo.time}</span>
                          </div>
                        </div>

                        {/* User info */}
                        <div style={styles.userInfo}>
                          <div style={styles.userAvatar}>
                            {getUserInitials(log.username)}
                          </div>
                          <span style={styles.userName}>{log.username}</span>
                        </div>

                        {/* Details */}
                        {translatedDetails && renderDetails(translatedDetails)}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="modal-footer" style={styles.modalFooter}>
            <div style={styles.statsInfo}>
              <i className="fas fa-list-ul"></i>
              <span>Toplam {logs.length} hareket</span>
            </div>
            <button 
              type="button" 
              className="btn btn-primary" 
              onClick={onClose}
              style={styles.closeButton}
            >
              <i className="fas fa-times me-2"></i>
              Kapat
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AuditTimelineModal;


