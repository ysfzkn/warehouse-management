import React, { useState } from 'react';
import axios from 'axios';
import { useToast } from './Toast';

/**
 * Public contact form rendered on the /store/sayfa/iletisim page.
 * Submits to POST /api/store/contact-messages with a hidden honeypot field.
 */
export default function ContactForm() {
  const toast = useToast();
  const [form, setForm] = useState({
    name: '',
    email: '',
    phone: '',
    subject: '',
    message: '',
    website: '' // honeypot — must stay empty
  });
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (form.message.trim().length < 10) {
      toast.warning('Mesajınız en az 10 karakter olmalıdır.');
      return;
    }
    setLoading(true);
    try {
      await axios.post('/api/store/contact-messages', form);
      toast.success('Mesajınız alındı. En kısa sürede size dönüş yapacağız.');
      setSubmitted(true);
      setForm({ name: '', email: '', phone: '', subject: '', message: '', website: '' });
    } catch (err) {
      if (err.response?.status === 429) {
        toast.error('Çok fazla istek. Lütfen bir dakika bekleyip tekrar deneyin.');
      } else if (err.response?.status === 503) {
        toast.error('İletişim formu şu an yapılandırılmamış.');
      } else if (err.response?.status === 400) {
        toast.error('Lütfen tüm zorunlu alanları doğru doldurun.');
      } else {
        toast.error('Mesajınız gönderilemedi. Lütfen daha sonra tekrar deneyin.');
      }
    } finally {
      setLoading(false);
    }
  };

  if (submitted) {
    return (
      <div className="text-center py-4">
        <div className="mb-3" style={{ fontSize: 48 }}>✉️</div>
        <h6 className="fw-bold mb-2">Mesajınız Alındı</h6>
        <p className="text-muted mb-3">En kısa sürede size dönüş yapacağız.</p>
        <button type="button" className="btn btn-outline-primary btn-sm" onClick={() => setSubmitted(false)}>
          Yeni Mesaj Gönder
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      {/* Honeypot — hidden from humans, bots fill it */}
      <input
        type="text"
        name="website"
        value={form.website}
        onChange={handleChange}
        tabIndex={-1}
        autoComplete="off"
        style={{ position: 'absolute', left: '-9999px', width: 1, height: 1, opacity: 0 }}
        aria-hidden="true"
      />

      <div className="row g-3">
        <div className="col-md-6">
          <label htmlFor="contact-name" className="form-label small fw-semibold">Ad Soyad *</label>
          <input
            id="contact-name"
            type="text"
            name="name"
            className="form-control"
            value={form.name}
            onChange={handleChange}
            required
            maxLength={150}
          />
        </div>
        <div className="col-md-6">
          <label htmlFor="contact-email" className="form-label small fw-semibold">E-posta *</label>
          <input
            id="contact-email"
            type="email"
            name="email"
            className="form-control"
            value={form.email}
            onChange={handleChange}
            required
            maxLength={200}
          />
        </div>
        <div className="col-md-6">
          <label htmlFor="contact-phone" className="form-label small fw-semibold">Telefon</label>
          <input
            id="contact-phone"
            type="tel"
            name="phone"
            className="form-control"
            value={form.phone}
            onChange={handleChange}
            maxLength={40}
            placeholder="(İsteğe bağlı)"
          />
        </div>
        <div className="col-md-6">
          <label htmlFor="contact-subject" className="form-label small fw-semibold">Konu *</label>
          <input
            id="contact-subject"
            type="text"
            name="subject"
            className="form-control"
            value={form.subject}
            onChange={handleChange}
            required
            maxLength={200}
          />
        </div>
        <div className="col-12">
          <label htmlFor="contact-message" className="form-label small fw-semibold">Mesajınız *</label>
          <textarea
            id="contact-message"
            name="message"
            className="form-control"
            rows={5}
            value={form.message}
            onChange={handleChange}
            required
            minLength={10}
            maxLength={5000}
            placeholder="Mesajınızı buraya yazın (en az 10 karakter)…"
          />
          <div className="form-text">{form.message.length} / 5000 karakter</div>
        </div>
        <div className="col-12">
          <button type="submit" className="btn btn-primary px-4" disabled={loading}>
            {loading ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true" />
                Gönderiliyor…
              </>
            ) : (
              'Mesajı Gönder'
            )}
          </button>
        </div>
      </div>
    </form>
  );
}
