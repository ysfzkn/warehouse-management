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
      } catch (e) {
        // Keep polling on network errors
      }
    }, 3000);

    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [paymentId, onComplete]);

  return (
    <div>
      {status === 'processing' && (
        <div className="text-center mb-3">
          <div className="spinner-border spinner-border-sm text-primary me-2" role="status" />
          <span className="text-muted">Ödeme işleniyor... Lütfen sayfayı kapatmayın.</span>
        </div>
      )}
      <div ref={containerRef} id="iyzipay-checkout-form" />
    </div>
  );
}
