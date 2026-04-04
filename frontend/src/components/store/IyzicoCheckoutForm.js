import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';

export default function IyzicoCheckoutForm({ htmlContent, paymentId, onComplete }) {
  const containerRef = useRef(null);
  const [status, setStatus] = useState('processing');
  const pollRef = useRef(null);

  useEffect(() => {
    if (htmlContent && containerRef.current) {
      containerRef.current.innerHTML = htmlContent;
      // iyzico JS auto-initializes from the injected HTML
      const scripts = containerRef.current.querySelectorAll('script');
      scripts.forEach(oldScript => {
        const newScript = document.createElement('script');
        if (oldScript.src) {
          newScript.src = oldScript.src;
        } else {
          newScript.textContent = oldScript.textContent;
        }
        oldScript.parentNode.replaceChild(newScript, oldScript);
      });
    }
  }, [htmlContent]);

  // Aggressively override iyzico's overlay: watch DOM and force inline
  useEffect(() => {
    const fix = () => {
      // e1knarz31 = overlay (black backdrop) — hide it
      document.querySelectorAll('[class*="e1knarz31"]').forEach(el => {
        el.style.setProperty('display', 'none', 'important');
      });
      // e1knarz30 = wrapper — make relative
      document.querySelectorAll('[class*="e1knarz30"]').forEach(el => {
        el.style.setProperty('position', 'relative', 'important');
        el.style.setProperty('z-index', '1', 'important');
      });
      // e1knarz32 = holder — make relative
      document.querySelectorAll('[class*="e1knarz32"]').forEach(el => {
        el.style.setProperty('position', 'relative', 'important');
        el.style.setProperty('height', 'auto', 'important');
      });
      // e1u40tqm1 = modal view — make relative
      document.querySelectorAll('[class*="e1u40tqm1"]').forEach(el => {
        el.style.setProperty('position', 'relative', 'important');
        el.style.setProperty('transform', 'none', 'important');
        el.style.setProperty('width', '100%', 'important');
        el.style.setProperty('max-width', '100%', 'important');
      });
    };

    // Run immediately and on a short interval (iyzico renders async)
    fix();
    const interval = setInterval(fix, 200);
    const timeout = setTimeout(() => clearInterval(interval), 10000); // stop after 10s

    return () => { clearInterval(interval); clearTimeout(timeout); };
  }, []);

  // Poll payment status
  useEffect(() => {
    if (!paymentId) return;
    pollRef.current = setInterval(async () => {
      try {
        const token = localStorage.getItem('customer_token');
        const res = await axios.get(`/api/store/payment/${paymentId}/status`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {}
        });
        if (res.data.status === 'SUCCESS') {
          clearInterval(pollRef.current);
          setStatus('success');
          if (onComplete) onComplete(true, res.data);
        } else if (res.data.status === 'FAILED' || res.data.status === 'TIMEOUT') {
          clearInterval(pollRef.current);
          setStatus('failed');
          if (onComplete) onComplete(false, res.data);
        }
      } catch (e) {}
    }, 3000);
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [paymentId, onComplete]);

  return (
    <div style={{ background: '#fff', borderRadius: 16, overflow: 'hidden' }}>
      {status === 'processing' && (
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          padding: '10px 16px', background: 'linear-gradient(90deg, #eff6ff, #f0fdf4)',
          borderBottom: '1px solid #e2e8f0', fontSize: '0.82rem', color: '#475569',
        }}>
          <div className="spinner-border spinner-border-sm text-primary" style={{ width: 14, height: 14 }} />
          <span>Ödeme formu yükleniyor...</span>
        </div>
      )}
      <div
        ref={containerRef}
        id="iyzipay-checkout-form"
        style={{ background: '#fff', minHeight: 200 }}
      />
    </div>
  );
}
