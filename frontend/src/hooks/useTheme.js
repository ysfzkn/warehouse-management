import { useCallback, useEffect, useState } from 'react';

/**
 * Tema tercihi yönetimi (storefront).
 *
 * Üç durum:
 *   - null    → "sistem" (OS tercihine uyar; data-theme set edilmez)
 *   - 'light' → açık moda kilitle
 *   - 'dark'  → koyu moda kilitle
 *
 * Tercih localStorage('theme') içinde saklanır. FOUC önleme için ilk uygulama
 * public/index.html'deki inline script'te yapılır; bu hook mount'ta idempotent
 * olarak yeniden uygular ve toggle/sistem değişikliklerini yönetir.
 */
const STORAGE_KEY = 'theme';

const readStored = () => {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === 'light' || v === 'dark' ? v : null;
  } catch {
    return null;
  }
};

const applyPref = (pref) => {
  const root = document.documentElement;
  if (pref === 'light' || pref === 'dark') {
    root.setAttribute('data-theme', pref);
  } else {
    root.removeAttribute('data-theme');
  }
};

const systemPrefersDark = () => {
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  } catch {
    return false;
  }
};

export default function useTheme() {
  const [pref, setPref] = useState(readStored); // 'light' | 'dark' | null
  const [systemDark, setSystemDark] = useState(systemPrefersDark);

  // OS tercihi değişimini izle (yalnızca "sistem" modunda etkiyi görünür kılar).
  useEffect(() => {
    let mq;
    try {
      mq = window.matchMedia('(prefers-color-scheme: dark)');
    } catch {
      return undefined;
    }
    const onChange = (e) => setSystemDark(e.matches);
    // Safari <14 addListener fallback
    if (mq.addEventListener) mq.addEventListener('change', onChange);
    else if (mq.addListener) mq.addListener(onChange);
    return () => {
      if (mq.removeEventListener) mq.removeEventListener('change', onChange);
      else if (mq.removeListener) mq.removeListener(onChange);
    };
  }, []);

  // Tercih değiştikçe DOM'a uygula (mount dahil — inline script ile idempotent).
  useEffect(() => {
    applyPref(pref);
  }, [pref]);

  const isDark = pref === 'dark' || (pref === null && systemDark);

  // Güncel efektif temaya göre karşı moda geç ve kalıcılaştır.
  const toggle = useCallback(() => {
    const effectiveDark = pref === 'dark' || (pref === null && systemPrefersDark());
    const next = effectiveDark ? 'light' : 'dark';
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {}
    setPref(next);
  }, [pref]);

  return { isDark, pref, toggle };
}
