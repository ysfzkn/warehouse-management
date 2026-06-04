/**
 * Payment Gateway Form Schema
 *
 * Defines which fields are required/optional/n-a for each protocol.
 * The AdminPaymentGateways form renders dynamically based on this schema; the user
 * sees only the fields relevant to their protocol, and unnecessary fields are hidden.
 *
 * Structure:
 *   PROTOCOLS[code] = {
 *     label, icon, description, helpUrl,    // visual + help
 *     bankSelect: boolean,                  // whether to show bank selection
 *     banks: [...],                         // options when bankSelect=true
 *     fields: { fieldKey: { required, label, type, placeholder, help, ... } }
 *     extraFields: { ... }                  // stored under extraConfig
 *   }
 *
 * field type: 'text' | 'password' | 'url' | 'select' | 'number' | 'cards-multi'
 */

export const BANK_LABELS = {
  ISBANK: 'İş Bankası', AKBANK: 'Akbank', HALKBANK: 'Halkbank', TEB: 'TEB',
  DENIZBANK: 'DenizBank', ING: 'ING Bank', ZIRAAT: 'Ziraat Bankası', KUVEYT: 'Kuveyt Türk',
  QNB: 'QNB Finansbank', ANADOLU: 'Anadolubank', GARANTI: 'Garanti BBVA', YAPI_KREDI: 'Yapı Kredi',
};

/**
 * Common field definitions (present in every protocol).
 * Each protocol applies its own customization by spreading these objects.
 */
const COMMON_FIELDS = {
  callbackUrl: {
    label: 'Callback URL',
    type: 'url',
    required: true,
    placeholder: 'https://api.siteniz.com/api/store/payment/callback/...',
    help: 'Ödeme sonucu bu URL\'e POST gelir. Production\'da HTTPS zorunlu.',
  },
};

export const PROTOCOLS = {
  IYZICO: {
    label: 'iyzico',
    icon: 'fa-shield-alt',
    color: 'primary',
    description: 'Türkiye\'nin en yaygın ödeme servisi. Tüm Türk bankalarını destekler, tek entegrasyon.',
    helpUrl: 'https://merchant.iyzipay.com',
    helpText: 'Anahtarlarınızı iyzico Merchant Panel → Ayarlar → API Anahtarları sayfasından alabilirsiniz.',
    bankSelect: false,
    fields: {
      apiKey: {
        label: 'API Key',
        type: 'password',
        required: true,
        placeholder: 'sandbox-... veya live-...',
        help: 'iyzico panel → Ayarlar → API Anahtarları → API Key',
      },
      secretKey: {
        label: 'Secret Key',
        type: 'password',
        required: true,
        placeholder: 'iyzico secret key',
        help: 'iyzico panel → Ayarlar → API Anahtarları → Secret Key. Sunucu tarafında saklanır, asla frontend\'e açılmaz.',
      },
      baseUrl: {
        label: 'API Base URL',
        type: 'url',
        required: true,
        placeholder: 'https://sandbox-api.iyzipay.com (test) — https://api.iyzipay.com (canlı)',
        help: 'Sandbox: https://sandbox-api.iyzipay.com — Production: https://api.iyzipay.com',
      },
      callbackUrl: COMMON_FIELDS.callbackUrl,
    },
  },

  PAYTR: {
    label: 'PayTR',
    icon: 'fa-credit-card',
    color: 'success',
    description: 'iFrame tabanlı ödeme. Kart bilgileri PayTR\'ın güvenli sayfasında girilir (PCI SAQ-A).',
    helpUrl: 'https://www.paytr.com',
    helpText: 'Mağaza Paneli → Bilgi → API Bilgileri sayfasından Mağaza No, Parolası ve Gizli Anahtar değerlerini alın.',
    bankSelect: false,
    fields: {
      merchantId: {
        label: 'Mağaza No',
        type: 'text',
        required: true,
        placeholder: '123456',
        help: 'PayTR panel → Bilgi → API Bilgileri → Mağaza No (merchant_id)',
        monospace: true,
      },
      apiKey: {
        label: 'Mağaza Parolası',
        type: 'password',
        required: true,
        placeholder: 'merchant_key değeri',
        help: 'PayTR panel → API Bilgileri → Mağaza Parolası (merchant_key). Hash imzalama için kullanılır.',
      },
      secretKey: {
        label: 'Gizli Anahtar',
        type: 'password',
        required: true,
        placeholder: 'merchant_salt değeri',
        help: 'PayTR panel → API Bilgileri → Gizli Anahtar (merchant_salt). HMAC tuzu, asla paylaşılmamalı.',
      },
      callbackUrl: {
        ...COMMON_FIELDS.callbackUrl,
        label: 'Bildirim URL (notify_url)',
        placeholder: 'https://api.siteniz.com/api/store/payment/callback/paytr/{code}',
        help: 'PayTR ödeme sonucunu server-to-server bu URL\'e POST eder. Cevap olarak "OK" beklenir.',
      },
    },
    extraFields: {
      merchant_ok_url: {
        label: 'Başarılı Ödeme URL',
        type: 'url',
        required: true,
        placeholder: 'https://siteniz.com/odeme/sonuc?success=true',
        help: 'Ödeme başarılı olunca müşterinin yönlendirileceği store frontend URL\'i. ' +
              '"Otomatik öner" ile mevcut ortama göre doldurabilirsiniz.',
      },
      merchant_fail_url: {
        label: 'Başarısız Ödeme URL',
        type: 'url',
        required: true,
        placeholder: 'https://siteniz.com/odeme/sonuc?success=false',
        help: 'Ödeme başarısız olunca müşterinin yönlendirileceği store frontend URL\'i. ' +
              '"Otomatik öner" ile mevcut ortama göre doldurabilirsiniz.',
      },
      timeout_limit: {
        label: 'Zaman Aşımı (dk)',
        type: 'number',
        required: false,
        placeholder: '30',
        help: 'Müşterinin ödeme sayfasında geçirebileceği maksimum süre (default: 30).',
        min: 5, max: 60,
      },
    },
  },

  NESTPAY: {
    label: 'NestPay (Asseco)',
    icon: 'fa-university',
    color: 'info',
    description: 'Doğrudan banka POS terminali entegrasyonu. İş Bankası, Akbank, Halkbank, TEB, DenizBank vb. NestPay altyapısını kullanır.',
    helpUrl: '',
    helpText: 'POS terminal bilgileri (Merchant ID, Terminal ID, Store Key) bankanızdan başvuru sonrası verilir. Genellikle 1-3 iş günü.',
    bankSelect: true,
    banks: ['ISBANK', 'AKBANK', 'HALKBANK', 'TEB', 'DENIZBANK', 'ING', 'ZIRAAT', 'KUVEYT', 'QNB', 'ANADOLU'],
    fields: {
      merchantId: {
        label: 'Merchant ID',
        type: 'text',
        required: true,
        placeholder: '8 haneli (örn. 12345678)',
        help: 'Bankanızdan verilen üye işyeri numarası.',
        monospace: true,
      },
      terminalId: {
        label: 'Terminal ID',
        type: 'text',
        required: true,
        placeholder: '8 haneli (örn. 87654321)',
        help: 'Bankanızdan verilen POS terminal numarası.',
        monospace: true,
      },
      storeKey: {
        label: 'Store Key',
        type: 'password',
        required: true,
        placeholder: 'Banka tarafından verilen anahtar',
        help: 'Hash imzalama için kullanılır. Bankanın gönderdiği zarflı belgede bulunur.',
      },
      threeDUrl: {
        label: '3D Secure URL',
        type: 'url',
        required: true,
        placeholder: 'https://entegrasyon.asseco-see.com.tr/fim/est3Dgate',
        help: 'Test: https://entegrasyon.asseco-see.com.tr/fim/est3Dgate — Prod URL bankaya göre değişir.',
      },
      callbackUrl: COMMON_FIELDS.callbackUrl,
    },
  },

  GVP: {
    label: 'GVP (Garanti)',
    icon: 'fa-building-columns',
    color: 'danger',
    description: 'Garanti BBVA Sanal POS (GVP). Sadece Garanti POS kullanıcıları içindir.',
    helpUrl: '',
    helpText: 'Bilgilerinizi Garanti BBVA Sanal POS başvurusu sonrası "Sanal POS Yönetim Paneli"nden alabilirsiniz.',
    bankSelect: true,
    banks: ['GARANTI'],
    fields: {
      merchantId: {
        label: 'Merchant ID',
        type: 'text',
        required: true,
        placeholder: '7000xxxx',
        help: 'Garanti üye işyeri numarası.',
        monospace: true,
      },
      terminalId: {
        label: 'Terminal ID',
        type: 'text',
        required: true,
        placeholder: '30xxxxxx',
        help: 'Garanti POS terminal numarası.',
        monospace: true,
      },
      storeKey: {
        label: 'Store Key',
        type: 'password',
        required: true,
        placeholder: 'GVP store key',
        help: 'Hash imzalama anahtarı.',
      },
      provisionPassword: {
        label: 'Provision Password',
        type: 'password',
        required: true,
        placeholder: 'GVP provision şifresi',
        help: 'Garanti GVP\'ye özel — direkt çekim işlemleri için ayrı şifre.',
      },
      threeDUrl: {
        label: '3D Secure URL',
        type: 'url',
        required: true,
        placeholder: 'https://sanalposprov.garanti.com.tr/servlet/gt3dengine',
        help: 'Garanti 3D Secure endpoint URL\'i.',
      },
      callbackUrl: COMMON_FIELDS.callbackUrl,
    },
  },
};

/**
 * Build the payload to send from the frontend to the backend.
 * Collect only the visible fields from the form state; hidden fields are undefined.
 */
export function buildPayloadForProtocol(formState, protocolCode) {
  const protocol = PROTOCOLS[protocolCode];
  if (!protocol) return formState;
  const payload = {
    code: formState.code,
    displayName: formState.displayName,
    gatewayProtocol: protocolCode,
    bankCode: protocol.bankSelect ? formState.bankCode : null,
    sandbox: !!formState.sandbox,
    priority: formState.priority || 100,
    supportedCards: formState.supportedCards || 'VISA,MASTERCARD,TROY',
    maxInstallments: formState.maxInstallments || 12,
  };
  // Standard fields
  Object.keys(protocol.fields).forEach(key => {
    if (formState[key] !== undefined) payload[key] = formState[key];
  });
  // extraConfig fields
  if (protocol.extraFields) {
    payload.extraConfig = {};
    Object.keys(protocol.extraFields).forEach(key => {
      if (formState['extra_' + key] !== undefined && formState['extra_' + key] !== '') {
        payload.extraConfig[key] = formState['extra_' + key];
      }
    });
  }
  return payload;
}

/**
 * Hydrate the form state from an existing gateway entity (edit mode).
 * Secrets come masked, so we leave them empty (the user types a new value if they want to change them).
 */
export function hydrateFormFromGateway(gateway) {
  const f = {
    code: gateway.code || '',
    displayName: gateway.displayName || '',
    gatewayProtocol: gateway.gatewayProtocol || 'IYZICO',
    bankCode: gateway.bankCode || '',
    merchantId: gateway.merchantId || '',
    terminalId: gateway.terminalId || '',
    storeKey: '', // Secrets are empty — enter a new value to change them
    provisionPassword: '',
    apiKey: '',
    secretKey: '',
    baseUrl: gateway.baseUrl || '',
    threeDUrl: gateway.threeDUrl || '',
    callbackUrl: gateway.callbackUrl || '',
    sandbox: gateway.sandbox !== false,
    priority: gateway.priority || 100,
    supportedCards: gateway.supportedCards || 'VISA,MASTERCARD,TROY',
    maxInstallments: gateway.maxInstallments || 12,
  };
  // extraConfig fields
  if (gateway.extraConfig && typeof gateway.extraConfig === 'object') {
    Object.entries(gateway.extraConfig).forEach(([k, v]) => {
      f['extra_' + k] = v != null ? String(v) : '';
    });
  }
  // *Set flags to know whether secrets are set in the DB while in edit mode
  f._storeKeySet = !!gateway.storeKeySet;
  f._provisionPasswordSet = !!gateway.provisionPasswordSet;
  f._apiKeySet = !!gateway.apiKeySet;
  f._secretKeySet = !!gateway.secretKeySet;
  return f;
}

/** Empty form (when creating a new gateway). */
export function emptyFormForProtocol(protocolCode = 'IYZICO') {
  return {
    code: '', displayName: '', gatewayProtocol: protocolCode, bankCode: '',
    merchantId: '', terminalId: '', storeKey: '', provisionPassword: '',
    apiKey: '', secretKey: '', baseUrl: '', threeDUrl: '', callbackUrl: '',
    sandbox: true, priority: 100, supportedCards: 'VISA,MASTERCARD,TROY', maxInstallments: 12,
  };
}

/**
 * Frontend-side validation (UX) — the backend performs the same check (defense-in-depth).
 * @returns string error message or null if valid
 */
export function validateForm(formState, protocolCode, isEdit) {
  if (!formState.code || !formState.code.trim()) return 'Gateway kodu zorunludur.';
  if (!/^[A-Z0-9_]{3,50}$/.test(formState.code)) return 'Gateway kodu 3-50 karakter, sadece A-Z, 0-9, _ içerebilir.';
  if (!formState.displayName || !formState.displayName.trim()) return 'Görünen ad zorunludur.';

  const protocol = PROTOCOLS[protocolCode];
  if (!protocol) return 'Geçersiz protokol.';

  if (protocol.bankSelect && !formState.bankCode) return 'Banka seçimi zorunludur.';

  // Empty check for each required field (in edit mode secrets are optional — the existing value is kept)
  for (const [key, def] of Object.entries(protocol.fields)) {
    if (!def.required) continue;
    const isSecret = def.type === 'password';
    if (isEdit && isSecret) continue; // secrets may stay empty on edit
    const v = formState[key];
    if (!v || !String(v).trim()) return `${def.label} zorunludur.`;
    // Additional URL validation
    if (def.type === 'url') {
      const lower = String(v).trim().toLowerCase();
      if (!lower.startsWith('http://') && !lower.startsWith('https://')) {
        return `${def.label} http:// veya https:// ile başlamalıdır.`;
      }
      if (!formState.sandbox && !lower.startsWith('https://')) {
        return `${def.label}: Production modunda HTTPS zorunludur.`;
      }
      // In sandbox, http://localhost*, 127.x, 10.x, 192.168.x, *.local are allowed
      if (formState.sandbox && lower.startsWith('http://')) {
        const isLocal =
          /^http:\/\/([a-z0-9-]+\.)*localhost(:\d+)?(\/|$|\?)/.test(lower) ||
          /^http:\/\/127\.\d+\.\d+\.\d+(:\d+)?(\/|$|\?)/.test(lower) ||
          /^http:\/\/0\.0\.0\.0/.test(lower) ||
          /^http:\/\/[a-z0-9.-]+\.local(:\d+)?(\/|$|\?)/.test(lower) ||
          /^http:\/\/192\.168\.\d+\.\d+(:\d+)?(\/|$|\?)/.test(lower) ||
          /^http:\/\/10\.\d+\.\d+\.\d+(:\d+)?(\/|$|\?)/.test(lower) ||
          /^http:\/\/172\.(1[6-9]|2[0-9]|3[01])\.\d+\.\d+(:\d+)?(\/|$|\?)/.test(lower);
        if (!isLocal) {
          return `${def.label}: Sandbox'ta bile HTTPS önerilir. Lokal test için http://localhost veya *.local izinli.`;
        }
      }
    }
  }
  // extraConfig fields
  if (protocol.extraFields) {
    for (const [key, def] of Object.entries(protocol.extraFields)) {
      if (!def.required) continue;
      const v = formState['extra_' + key];
      if (!v || !String(v).trim()) return `${def.label} zorunludur.`;
      if (def.type === 'url') {
        const lower = String(v).trim().toLowerCase();
        if (!lower.startsWith('http://') && !lower.startsWith('https://')) {
          return `${def.label} http:// veya https:// ile başlamalıdır.`;
        }
        if (!formState.sandbox && !lower.startsWith('https://')) {
          return `${def.label}: Production modunda HTTPS zorunludur.`;
        }
      }
    }
  }
  return null;
}

/**
 * Detect the backend base URL. Even if the admin panel runs at
 * `admin.localhost:3000`, the callback must reach the backend (port 8080) in dev.
 * In prod, the backend is at `api.<domain>` or directly under the domain.
 */
function detectBackendBase() {
  const { protocol, hostname, port } = window.location;
  // Local dev: frontend 3000, backend 8080
  if (hostname === 'localhost' || hostname.endsWith('.localhost') || hostname === '127.0.0.1') {
    return `${protocol}//localhost:8080`;
  }
  // Prod: admin.yourdomain.com → api.yourdomain.com
  // or yourdomain.com → api.yourdomain.com
  const hostParts = hostname.split('.');
  if (hostParts.length >= 2) {
    // Strip the subdomain (admin., wms., www.) and prepend api.
    const rootDomain = hostParts.length > 2 ? hostParts.slice(-2).join('.') : hostname;
    return `${protocol}//api.${rootDomain}`;
  }
  return `${protocol}//${hostname}${port ? ':' + port : ''}`;
}

/**
 * Detect the store frontend base URL. Customers should be redirected
 * to the main store domain, not the admin panel.
 */
function detectStoreBase() {
  const { protocol, hostname, port } = window.location;
  // Local dev
  if (hostname === 'localhost' || hostname.endsWith('.localhost') || hostname === '127.0.0.1') {
    return `${protocol}//localhost:3000`;
  }
  // Prod: admin.yourdomain.com → yourdomain.com
  const hostParts = hostname.split('.');
  if (hostParts.length > 2 && ['admin', 'wms', 'panel'].includes(hostParts[0])) {
    return `${protocol}//${hostParts.slice(1).join('.')}`;
  }
  return `${protocol}//${hostname}${port ? ':' + port : ''}`;
}

/** Code + protocol → callback URL suggestion (backend endpoint). */
export function suggestCallbackUrl(code, protocolCode, host) {
  const base = host || detectBackendBase();
  if (protocolCode === 'PAYTR') {
    return `${base}/api/store/payment/callback/paytr/${code || 'CODE'}`;
  }
  return `${base}/api/store/payment/callback/pos/${code || 'CODE'}`;
}

/** Success/failure redirect URL suggestion (store frontend). */
export function suggestRedirectUrl(success) {
  return `${detectStoreBase()}/odeme/sonuc?success=${success ? 'true' : 'false'}`;
}
