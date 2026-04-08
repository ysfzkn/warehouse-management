import React, { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import { useLocation } from 'react-router-dom';
import './CezeriAssistantWidget.css';

/**
 * Generic Cezeri assistant widget shared by the WMS (admin) and Store
 * (storefront) profiles. Behaviour is driven by the `config` prop so we can
 * keep one component with two flavours — logo, rendering, session handling
 * and typing animation are identical; only the backend endpoint, prompt
 * flow and auth model differ.
 *
 * Config shape:
 * {
 *   profile:          'wms' | 'store',
 *   endpoint:         '/api/cezeri/chat' | '/api/store/assistant/chat',
 *   storagePrefix:    string,                    // localStorage key prefix
 *   authTokenKey:     'auth_token' | null,       // localStorage key for Bearer; null = cookie-based
 *   requiresAuth:     boolean,                    // wms=true, store=false (guest allowed)
 *   welcomeMessage:   string,
 *   title:            string,
 *   subtitle:         string,
 *   placeholder:      string,
 *   unauthHint:       string,                     // shown when requiresAuth && no token
 *   sendAllowMutations: boolean,                  // wms only
 *   withCredentials:  boolean,                    // store=true so guest cookie rides along
 *   getUserKey:       () => string,               // key for per-user localStorage bucket
 *   hideOnPaths:      string[],                   // array of route paths to hide on
 * }
 */

const LEFT_AVATAR = `${process.env.PUBLIC_URL}/cezeri-left.png`;
const FRONT_AVATAR = `${process.env.PUBLIC_URL}/cezeri-front.png`;
const MAX_MESSAGES = 30;
const TTL_MS = 6 * 60 * 60 * 1000;
const TYPING_MIN_MS = 10;
const TYPING_MAX_MS = 24;

export default function AssistantWidget({ config }) {
  const location = useLocation();
  const hideOnLogin = (config.hideOnPaths || []).includes((location.pathname || '').toLowerCase());
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [loading, setLoading] = useState(false);
  const [avatarOk, setAvatarOk] = useState({ left: true, front: true });
  const [userKey, setUserKey] = useState(() => config.getUserKey(config.storagePrefix));
  const [messages, setMessages] = useState(() => loadSession(config.getUserKey(config.storagePrefix), config.welcomeMessage));
  const [rateLimited, setRateLimited] = useState(null); // { friendlyMessage, reachedGuestLimit, retryAfterSeconds }

  const bodyRef = useRef(null);

  const uiContext = useMemo(() => ({ route: location.pathname || '/' }), [location.pathname]);

  const [authed, setAuthed] = useState(
    config.requiresAuth ? !!localStorage.getItem(config.authTokenKey) : true
  );

  useEffect(() => {
    const onAuthChanged = () => {
      if (config.requiresAuth) {
        setAuthed(!!localStorage.getItem(config.authTokenKey));
      }
      setOpen(false);
      const nextKey = config.getUserKey(config.storagePrefix);
      setUserKey(nextKey);
      setMessages(loadSession(nextKey, config.welcomeMessage));
    };
    window.addEventListener('storage', onAuthChanged);
    window.addEventListener('auth-changed', onAuthChanged);
    return () => {
      window.removeEventListener('storage', onAuthChanged);
      window.removeEventListener('auth-changed', onAuthChanged);
    };
  }, [config]);

  useEffect(() => {
    if (messages.some((m) => m && m.isTyping)) return;
    persistSession(userKey, messages);
  }, [userKey, messages]);

  useEffect(() => {
    if (!open) return;
    const t = setTimeout(() => {
      if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
    }, 50);
    return () => clearTimeout(t);
  }, [open, messages.length]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  const send = async () => {
    const trimmed = text.trim();
    if (!trimmed || loading) return;

    // WMS requires login; store does not.
    if (config.requiresAuth && !authed) {
      setMessages((prev) => [
        ...prev.slice(-MAX_MESSAGES),
        { role: 'user', content: trimmed },
        { role: 'assistant', content: config.unauthHint },
      ]);
      setText('');
      return;
    }

    const nextMessages = [...messages.slice(-MAX_MESSAGES), { role: 'user', content: trimmed }];
    setMessages(nextMessages.slice(-MAX_MESSAGES));
    setText('');
    setLoading(true);
    setRateLimited(null);

    try {
      // Build request body per profile
      const body = config.profile === 'wms'
        ? {
            messages: nextMessages,
            allowMutations: !!config.sendAllowMutations,
            ui: uiContext,
          }
        : {
            messages: nextMessages,
            ui: uiContext,
          };

      const headers = {};
      const token = config.authTokenKey ? localStorage.getItem(config.authTokenKey) : null;
      if (token) headers.Authorization = `Bearer ${token}`;

      const resp = await axios.post(config.endpoint, body, {
        headers,
        withCredentials: !!config.withCredentials,
      });

      const data = resp?.data || {};
      const assistant = data.message || 'Yanıt alınamadı.';

      // Rate limit (store only). Render the friendly message and stop.
      if (data.rateLimit) {
        setRateLimited(data.rateLimit);
        setMessages((prev) =>
          [...prev.slice(-MAX_MESSAGES), { role: 'assistant', content: assistant, isRateLimit: true, rateLimit: data.rateLimit }].slice(-MAX_MESSAGES)
        );
        return;
      }

      const full = String(assistant);
      const id = `a-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const shouldAnimate = !reducedMotion && full.length > 0;

      if (!shouldAnimate) {
        setMessages((prev) =>
          [...prev.slice(-MAX_MESSAGES), { id, role: 'assistant', content: full, isTyping: false }].slice(-MAX_MESSAGES)
        );
      } else {
        setMessages((prev) =>
          [...prev.slice(-MAX_MESSAGES), { id, role: 'assistant', content: '', isTyping: true }].slice(-MAX_MESSAGES)
        );
        animateAssistantTyping({
          id,
          fullText: full,
          setMessages,
          minMs: TYPING_MIN_MS,
          maxMs: TYPING_MAX_MS,
        });
      }
    } catch (e) {
      setMessages((prev) => [
        ...prev.slice(-MAX_MESSAGES),
        {
          role: 'assistant',
          content: 'Şu an yapay zekâ servisine ulaşamadım. Lütfen birkaç saniye sonra tekrar deneyin.',
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  if (hideOnLogin) return null;

  return (
    <>
      {open && (
        <div className="cezeri-panel" role="dialog" aria-label="Cezeri AI Assistant">
          <div className="cezeri-panel-header">
            <div className="cezeri-panel-title">
              <div className="name">{config.title || 'Cezeri'}</div>
              <div className="subtitle">
                {loading ? 'Düşünüyorum…'
                  : (config.requiresAuth && !authed) ? (config.unauthSubtitle || 'Giriş gerekli')
                  : (config.subtitle || 'Yapay Zekâ Asistanı')}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                className="btn btn-sm btn-outline-light"
                onClick={() => setMessages(defaultSessionMessages(config.welcomeMessage))}
                title="Sohbeti temizle"
              >
                Temizle
              </button>
              <button className="btn btn-sm btn-outline-light" onClick={() => setOpen(false)}>
                Kapat
              </button>
            </div>
          </div>

          <div className="cezeri-panel-body" ref={bodyRef}>
            {messages.map((m, idx) => (
              <div key={idx} className={`cezeri-msg ${m.role === 'user' ? 'user' : 'assistant'}`}>
                <div className={`cezeri-bubble ${m.isTyping ? 'cezeri-typing' : ''}`}>
                  {renderRichText(m.content)}
                  {m.isTyping ? <span className="cezeri-caret" aria-hidden="true" /> : null}
                  {m.isRateLimit && m.rateLimit && m.rateLimit.reachedGuestLimit ? (
                    <div style={{ marginTop: 10 }}>
                      <a
                        href="/giris"
                        className="btn btn-sm btn-light"
                        style={{ fontWeight: 600 }}
                      >
                        Üye Girişi
                      </a>
                      <a
                        href="/kayit"
                        className="btn btn-sm btn-outline-light"
                        style={{ marginLeft: 8 }}
                      >
                        Ücretsiz Üye Ol
                      </a>
                    </div>
                  ) : null}
                </div>
              </div>
            ))}

            {loading && !messages.some((m) => m && m.isTyping) ? (
              <div className="cezeri-msg assistant">
                <div className="cezeri-bubble cezeri-loading">
                  <span className="cezeri-loading-text">Cezeri yazıyor</span>
                  <span className="cezeri-loading-dots" aria-hidden="true">
                    <span className="dot d1" />
                    <span className="dot d2" />
                    <span className="dot d3" />
                  </span>
                </div>
              </div>
            ) : null}
          </div>

          <div className="cezeri-panel-footer">
            <textarea
              className="cezeri-input"
              placeholder={config.placeholder || 'Cezeri’ye sor…'}
              value={text}
              onChange={(e) => setText(e.target.value)}
              onKeyDown={onKeyDown}
              disabled={loading}
            />
            <button className="cezeri-btn" onClick={send} disabled={loading || !text.trim()}>
              Gönder
            </button>
          </div>
        </div>
      )}

      <button
        className={`cezeri-fab ${open ? 'cezeri-open' : ''}`}
        aria-label="Open Cezeri AI assistant"
        onClick={() => setOpen((v) => !v)}
        title="Cezeri"
      >
        <div className="cezeri-avatars">
          {avatarOk.left ? (
            <img
              className="cezeri-avatar left"
              src={LEFT_AVATAR}
              alt=""
              onError={() => setAvatarOk((p) => ({ ...p, left: false }))}
            />
          ) : (
            <div className="cezeri-fallback">C</div>
          )}
          {avatarOk.front ? (
            <img
              className="cezeri-avatar front"
              src={FRONT_AVATAR}
              alt=""
              onError={() => setAvatarOk((p) => ({ ...p, front: false }))}
            />
          ) : null}
        </div>
      </button>
    </>
  );
}

// ---------- Session helpers ----------

function defaultSessionMessages(welcomeMessage) {
  return [{ role: 'assistant', content: welcomeMessage }];
}

function loadSession(userKey, welcomeMessage) {
  try {
    const raw = localStorage.getItem(userKey);
    if (!raw) return defaultSessionMessages(welcomeMessage);
    const parsed = JSON.parse(raw);
    const ts = parsed?.ts;
    const msgs = parsed?.messages;
    if (!Array.isArray(msgs) || msgs.length === 0) return defaultSessionMessages(welcomeMessage);
    if (typeof ts === 'number' && Date.now() - ts > TTL_MS) return defaultSessionMessages(welcomeMessage);
    return msgs.map((m) => ({ ...m, isTyping: false })).slice(-MAX_MESSAGES);
  } catch (e) {
    return defaultSessionMessages(welcomeMessage);
  }
}

function persistSession(userKey, messages) {
  try {
    const safeMessages = (Array.isArray(messages) ? messages : [])
      .map((m) => ({ ...m, isTyping: false }))
      .slice(-MAX_MESSAGES);
    localStorage.setItem(userKey, JSON.stringify({ v: 2, ts: Date.now(), messages: safeMessages }));
  } catch (e) { /* ignore */ }
}

function animateAssistantTyping({ id, fullText, setMessages, minMs, maxMs }) {
  let i = 0;
  const len = fullText.length;
  const nextDelay = (ch) => {
    if (ch === '.' || ch === '!' || ch === '?' || ch === '\n') return Math.min(220, maxMs * 10);
    if (ch === ',' || ch === ';' || ch === ':') return Math.min(140, maxMs * 6);
    const base = minMs + Math.random() * (maxMs - minMs);
    return Math.floor(base);
  };
  const step = () => {
    i = Math.min(i + 2, len);
    const slice = fullText.slice(0, i);
    setMessages((prev) =>
      prev.map((m) => (m && m.id === id ? { ...m, content: slice, isTyping: i < len } : m))
    );
    if (i < len) setTimeout(step, nextDelay(fullText[i - 1]));
  };
  setTimeout(step, 40);
}

// ---------- Rich text rendering ----------

function renderRichText(text) {
  const s = typeof text === 'string' ? text : '';
  const lines = s.split('\n');
  return lines.map((line, li) => (
    <React.Fragment key={`l-${li}`}>
      {renderBoldSegments(line, li)}
      {li < lines.length - 1 ? <br /> : null}
    </React.Fragment>
  ));
}

function renderBoldSegments(line, li) {
  const parts = line.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((p, i) => {
    const key = `l-${li}-p-${i}`;
    if (p.startsWith('**') && p.endsWith('**') && p.length >= 4) {
      return <strong key={key}>{p.slice(2, -2)}</strong>;
    }
    return <React.Fragment key={key}>{p}</React.Fragment>;
  });
}
