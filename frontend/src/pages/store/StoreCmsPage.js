import React, { useState, useEffect } from 'react';
import { useParams, useOutletContext } from 'react-router-dom';
import axios from 'axios';
import Breadcrumb from '../../components/store/Breadcrumb';
import ContactForm from '../../components/store/ContactForm';
import SeoHead from '../../components/store/SeoHead';
import { useSiteSettings } from '../../hooks/useSiteSettings';
import { buildArticleSchema, buildBreadcrumbSchema } from '../../utils/seo';
import { FiPhone, FiMail, FiMapPin } from 'react-icons/fi';
import { groupPhoneDirectory, telHref } from '../../utils/phones';

export default function StoreCmsPage() {
  const { slug } = useParams();
  const { siteSettings } = useOutletContext();
  const { settings } = useSiteSettings();
  const [page, setPage] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    axios
      .get(`/api/store/pages/${slug}`)
      .then((r) => setPage(r.data))
      .catch(() => setPage(null))
      .finally(() => setLoading(false));
  }, [slug]);

  if (loading)
    return (
      <div className="container my-5 text-center">
        <div className="spinner-border" />
      </div>
    );
  if (!page)
    return (
      <div className="container my-5 text-center">
        <h3 className="text-muted">Sayfa Bulunamadı</h3>
        <p className="text-muted">Aradığınız sayfa henüz oluşturulmamış olabilir.</p>
      </div>
    );

  // SEO: CMS article schema + breadcrumbs
  const articleSchema = buildArticleSchema(page, settings);
  const breadcrumbSchema = buildBreadcrumbSchema(
    [
      { name: 'Ana Sayfa', url: '/' },
      { name: page.title, url: `/sayfa/${page.slug}` },
    ],
    settings
  );

  const SeoHeader = (
    <SeoHead
      title={page.metaTitle || page.title}
      description={page.metaDescription}
      path={`/sayfa/${page.slug}`}
      type="article"
      jsonLd={[articleSchema, breadcrumbSchema]}
    />
  );

  // Special template for contact page
  if (slug === 'iletisim') {
    const mapEmbed = siteSettings.get('contact_map_embed', '');
    return (
      <div className="container my-4">
        {SeoHeader}
        <Breadcrumb items={[{ label: 'İletişim' }]} />
        <h1 className="h3 fw-bold mb-4">İletişim</h1>
        <div className="row g-4">
          <div className="col-lg-6">
            <div className="card border-0 shadow-sm h-100">
              <div className="card-body p-4">
                <h5 className="fw-bold mb-4">Bize Ulaşın</h5>
                <ul className="list-unstyled">
                  {siteSettings.get('contact_address') && (
                    <li className="d-flex align-items-start gap-3 mb-3">
                      <FiMapPin size={18} className="text-primary mt-1 flex-shrink-0" />
                      <span>{siteSettings.get('contact_address')}</span>
                    </li>
                  )}
                  {(() => {
                    const groups = groupPhoneDirectory(siteSettings.settings);
                    if (groups.length === 0) {
                      return siteSettings.get('contact_phone') ? (
                        <li className="d-flex align-items-center gap-3 mb-3">
                          <FiPhone size={18} className="text-primary flex-shrink-0" />
                          <a
                            href={telHref(siteSettings.get('contact_phone'))}
                            className="text-decoration-none"
                          >
                            {siteSettings.get('contact_phone')}
                          </a>
                        </li>
                      ) : null;
                    }
                    return groups.map((cat) => (
                      <li key={cat.key} className="d-flex align-items-start gap-3 mb-3">
                        <FiPhone size={18} className="text-primary mt-1 flex-shrink-0" />
                        <div>
                          <div className="fw-semibold">{cat.title}</div>
                          {cat.groups.map((sub) => (
                            <div key={String(sub.key)} className="mt-1">
                              {sub.rows.map((r, idx) => (
                                <div key={idx}>
                                  <a href={telHref(r.number)} className="text-decoration-none">
                                    {r.number}
                                  </a>
                                  {(r.label || sub.title) && (
                                    <span className="text-muted ms-2" style={{ fontSize: '0.85em' }}>
                                      {r.label || sub.title}
                                    </span>
                                  )}
                                </div>
                              ))}
                            </div>
                          ))}
                        </div>
                      </li>
                    ));
                  })()}
                  {siteSettings.get('contact_email') && (
                    <li className="d-flex align-items-center gap-3 mb-3">
                      <FiMail size={18} className="text-primary flex-shrink-0" />
                      <a
                        href={`mailto:${siteSettings.get('contact_email')}`}
                        className="text-decoration-none"
                      >
                        {siteSettings.get('contact_email')}
                      </a>
                    </li>
                  )}
                </ul>
                {/* CMS content */}
                <div className="mt-3" dangerouslySetInnerHTML={{ __html: page.content }} />
              </div>
            </div>
          </div>
          <div className="col-lg-6">
            {mapEmbed ? (
              <div className="ratio ratio-4x3 rounded overflow-hidden shadow-sm">
                <iframe
                  src={mapEmbed}
                  style={{ border: 0 }}
                  allowFullScreen=""
                  loading="lazy"
                  referrerPolicy="no-referrer-when-downgrade"
                  title="Konum haritası"
                />
              </div>
            ) : (
              <div className="card border-0 shadow-sm h-100">
                <div className="card-body p-4 d-flex align-items-center justify-content-center text-muted">
                  <p className="mb-0">Harita bilgisi admin panelden eklenebilir.</p>
                </div>
              </div>
            )}
          </div>
          <div className="col-12">
            <div className="card border-0 shadow-sm">
              <div className="card-body p-4">
                <h5 className="fw-bold mb-1">Mesaj Gönderin</h5>
                <p className="text-muted small mb-4">Formu doldurun, en kısa sürede size dönüş yapalım.</p>
                <ContactForm />
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Standard CMS page
  return (
    <div className="container my-4" style={{ maxWidth: 800 }}>
      {SeoHeader}
      <Breadcrumb items={[{ label: page.title }]} />
      <h1 className="h3 fw-bold mb-4">{page.title}</h1>
      <div className="cms-content" dangerouslySetInnerHTML={{ __html: page.content }} />
    </div>
  );
}
