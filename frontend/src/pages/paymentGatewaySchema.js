/**
 * Ödeme Gateway Form Şeması
 *
 * Her protokol için hangi alanların gerekli/opsiyonel/n/a olduğunu tanımlar.
 * AdminPaymentGateways formu bu şemaya bakarak dinamik render eder; kullanıcı
 * sadece protokolüne ait alanları görür, gereksiz alanlar gizlenir.
 *
 * Yapı:
 *   PROTOCOLS[code] = {
 *     label, icon, description, helpUrl,    // visual + help
 *     bankSelect: boolean,                  // banka seçimi gösterilsin mi
 *     banks: [...],                         // bankSelect=true ise opsiyonlar
 *     fields: { fieldKey: { required, label, type, placeholder, help, ... } }
 *     extraFields: { ... }                  // extraConfig altında saklanan
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
 * Ortak field tanımları (her protokolde yer alan).
 * Bu objelerin spread'i ile her protokol kendi farklılaştırmasını yapar.
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
        help: 'Ödeme başarılı olunca müşterinin yönlendirileceği frontend URL.',
      },
      merchant_fail_url: {
        label: 'Başarısız Ödeme URL',
        type: 'url',
        required: true,
        placeholder: 'https://siteniz.com/odeme/sonuc?success=false',
        help: 'Ödeme başarısız olunca müşterinin yönlendirileceği frontend URL.',
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
 * Frontend'den backend'e gönderilecek payload'u oluştur.
 * Form state'inde sadece görünen alanları topla; gizli alanlar undefined.
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
  // Standart field'lar
  Object.keys(protocol.fields).forEach(key => {
    if (formState[key] !== undefined) payload[key] = formState[key];
  });
  // extraConfig field'ları
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
 * Form state'i mevcut gateway entity'sinden (edit modu) hydrate eder.
 * Secret'lar mask'li gelir, bu yüzden onları boş bırakırız (kullanıcı değiştirmek isterse yazsın).
 */
export function hydrateFormFromGateway(gateway) {
  const f = {
    code: gateway.code || '',
    displayName: gateway.displayName || '',
    gatewayProtocol: gateway.gatewayProtocol || 'IYZICO',
    bankCode: gateway.bankCode || '',
    merchantId: gateway.merchantId || '',
    terminalId: gateway.terminalId || '',
    storeKey: '', // Secret'lar boş — değiştirmek için yeni değer girilir
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
  // extraConfig alanları
  if (gateway.extraConfig && typeof gateway.extraConfig === 'object') {
    Object.entries(gateway.extraConfig).forEach(([k, v]) => {
      f['extra_' + k] = v != null ? String(v) : '';
    });
  }
  // Edit modunda secret'ların DB'de set olup olmadığını bilmek için *Set flag'leri
  f._storeKeySet = !!gateway.storeKeySet;
  f._provisionPasswordSet = !!gateway.provisionPasswordSet;
  f._apiKeySet = !!gateway.apiKeySet;
  f._secretKeySet = !!gateway.secretKeySet;
  return f;
}

/** Boş form (yeni gateway oluştururken). */
export function emptyFormForProtocol(protocolCode = 'IYZICO') {
  return {
    code: '', displayName: '', gatewayProtocol: protocolCode, bankCode: '',
    merchantId: '', terminalId: '', storeKey: '', provisionPassword: '',
    apiKey: '', secretKey: '', baseUrl: '', threeDUrl: '', callbackUrl: '',
    sandbox: true, priority: 100, supportedCards: 'VISA,MASTERCARD,TROY', maxInstallments: 12,
  };
}

/**
 * Frontend tarafı validation (UX) — backend de aynı kontrolü yapar (defense-in-depth).
 * @returns string error message or null if valid
 */
export function validateForm(formState, protocolCode, isEdit) {
  if (!formState.code || !formState.code.trim()) return 'Gateway kodu zorunludur.';
  if (!/^[A-Z0-9_]{3,50}$/.test(formState.code)) return 'Gateway kodu 3-50 karakter, sadece A-Z, 0-9, _ içerebilir.';
  if (!formState.displayName || !formState.displayName.trim()) return 'Görünen ad zorunludur.';

  const protocol = PROTOCOLS[protocolCode];
  if (!protocol) return 'Geçersiz protokol.';

  if (protocol.bankSelect && !formState.bankCode) return 'Banka seçimi zorunludur.';

  // Her required field için boş kontrolü (edit modunda secret'lar opsiyonel — mevcut korunur)
  for (const [key, def] of Object.entries(protocol.fields)) {
    if (!def.required) continue;
    const isSecret = def.type === 'password';
    if (isEdit && isSecret) continue; // edit'te secret boş kalabilir
    const v = formState[key];
    if (!v || !String(v).trim()) return `${def.label} zorunludur.`;
    // URL ek validation
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
  // extraConfig field'ları
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

/** Code → callback URL önerisi. */
export function suggestCallbackUrl(code, protocolCode, host) {
  const base = host || window.location.origin;
  if (protocolCode === 'PAYTR') {
    return `${base}/api/store/payment/callback/paytr/${code || 'CODE'}`;
  }
  return `${base}/api/store/payment/callback/pos/${code || 'CODE'}`;
}
