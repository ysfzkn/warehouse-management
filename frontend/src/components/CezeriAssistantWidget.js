import React, { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import { useLocation } from 'react-router-dom';
import './CezeriAssistantWidget.css';

const LEFT_AVATAR = `${process.env.PUBLIC_URL}/cezeri-left.png`;
const FRONT_AVATAR = `${process.env.PUBLIC_URL}/cezeri-front.png`;
const STORAGE_PREFIX = 'cezeri_chat_v2:'; // per-user storage
const MAX_MESSAGES = 30; // short-term memory
const TTL_MS = 6 * 60 * 60 * 1000; // 6 hours
const TYPING_MIN_MS = 10;
const TYPING_MAX_MS = 24;

export default function CezeriAssistantWidget() {
  const location = useLocation();
  const hideOnLogin = (location.pathname || '').toLowerCase() === '/login';
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [loading, setLoading] = useState(false);
  const [avatarOk, setAvatarOk] = useState({ left: true, front: true });
  const [userKey, setUserKey] = useState(() => getUserKey());
  const [messages, setMessages] = useState(() => loadSession(getUserKey()));

  const bodyRef = useRef(null);

  const uiContext = useMemo(() => {
    return { route: location.pathname || '/' };
  }, [location.pathname]);

  const [authed, setAuthed] = useState(!!localStorage.getItem('auth_token'));

  useEffect(() => {
    const onAuthChanged = () => {
      setAuthed(!!localStorage.getItem('auth_token'));
      // user changed => close panel and switch session
      setOpen(false);
      const nextUserKey = getUserKey();
      setUserKey(nextUserKey);
      setMessages(loadSession(nextUserKey));
    };
    window.addEventListener('storage', onAuthChanged);
    window.addEventListener('auth-changed', onAuthChanged);
    return () => {
      window.removeEventListener('storage', onAuthChanged);
      window.removeEventListener('auth-changed', onAuthChanged);
    };
  }, []);

  useEffect(() => {
    // Avoid frequent writes while typing animation is running.
    if (messages.some((m) => m && m.isTyping)) return;
    persistSession(userKey, messages);
  }, [userKey, messages]);

  useEffect(() => {
    // If route changes while open, keep it open (user intent), but UI context will update.
    // If you want to auto-close on navigation, do it here.
  }, [location.pathname]);

  useEffect(() => {
    if (!open) return;
    // scroll to bottom on open/new messages
    const t = setTimeout(() => {
      if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
    }, 50);
    return () => clearTimeout(t);
  }, [open, messages.length]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  const send = async () => {
    const trimmed = text.trim();
    if (!trimmed || loading) return;

    if (!authed) {
      setMessages((prev) => [
        ...prev.slice(-MAX_MESSAGES),
        { role: 'user', content: trimmed },
        {
          role: 'assistant',
          content: 'Lütfen önce giriş yapın; böylece depo verilerinize güvenli şekilde erişebilirim.',
        },
      ]);
      setText('');
      return;
    }

    const nextMessages = [...messages.slice(-MAX_MESSAGES), { role: 'user', content: trimmed }];
    setMessages(nextMessages.slice(-MAX_MESSAGES));
    setText('');
    setLoading(true);

    try {
      const token = localStorage.getItem('auth_token');
      const resp = await axios.post(
        '/api/cezeri/chat',
        {
          messages: nextMessages,
          allowMutations: false,
          ui: uiContext,
        },
        {
          headers: { Authorization: `Bearer ${token}` },
        }
      );

      const assistant = resp?.data?.message || 'Yanıt alınamadı.';
      const full = String(assistant);
      const id = `a-${Date.now()}-${Math.random().toString(16).slice(2)}`;

      const reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const shouldAnimate = !reducedMotion && full.length > 0;

      if (!shouldAnimate) {
        setMessages((prev) =>
          [...prev.slice(-MAX_MESSAGES), { id, role: 'assistant', content: full, isTyping: false }].slice(-MAX_MESSAGES)
        );
      } else {
        // Insert an empty assistant bubble and animate text into it.
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
          content:
            'Şu anda sunucuya erişemedim. Backend’in çalıştığını ve Azure OpenAI env değişkenlerinin tanımlı olduğunu kontrol eder misiniz?',
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const onKeyDown = (e) => {
    // Enter to send, Shift+Enter for newline
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
              <div className="name">Cezeri</div>
              <div className="subtitle">
                {loading ? 'Düşünüyorum…' : authed ? 'Yapay Zekâ Asistanı' : 'Giriş gerekli'}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                className="btn btn-sm btn-outline-light"
                onClick={() => {
                  setMessages(defaultSessionMessages());
                }}
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
                </div>
              </div>
            ))}

            {/* Modern loading indicator while waiting for server response */}
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
              placeholder="Cezeri’ye sor…"
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

function defaultSessionMessages() {
  return [
    {
      role: 'assistant',
      content:
        'Merhaba, ben Cezeri. \n\nStok sorgulama, ürün (Stok Kodu/isim) arama, düşük stokları inceleme, müşteri/depo bazlı stok sorgulama, depo/stok hareketlerini inceleme gibi konularda size yardımcı olabilirim.\n\nNe yapmak istersiniz?',
    },
  ];
}

function getUserKey() {
  // Login ekranında set edilen değer: auth_user
  const user = (localStorage.getItem('auth_user') || '').trim();
  const role = (localStorage.getItem('auth_role') || '').trim();
  if (!user) return `${STORAGE_PREFIX}anon`;
  return `${STORAGE_PREFIX}${user.toLowerCase()}|${role || ''}`;
}

function loadSession(userKey) {
  try {
    const raw = localStorage.getItem(userKey);
    if (!raw) return defaultSessionMessages();
    const parsed = JSON.parse(raw);
    const ts = parsed?.ts;
    const msgs = parsed?.messages;
    if (!Array.isArray(msgs) || msgs.length === 0) return defaultSessionMessages();
    if (typeof ts === 'number' && Date.now() - ts > TTL_MS) return defaultSessionMessages();

    // Migration: replace legacy English greeting if present
    const migrated = msgs.map((m) => {
      if (!m || typeof m.content !== 'string') return m;
      if (m.content.includes("Hi, I'm Cezeri")) {
        return { ...m, content: defaultSessionMessages()[0].content };
      }
      // Ensure we never restore a "typing" state from storage.
      return { ...m, isTyping: false };
    });
    return migrated.slice(-MAX_MESSAGES);
  } catch (e) {
    return defaultSessionMessages();
  }
}

function persistSession(userKey, messages) {
  try {
    const safeMessages = (Array.isArray(messages) ? messages : [])
      .map((m) => ({ ...m, isTyping: false }))
      .slice(-MAX_MESSAGES);
    localStorage.setItem(
      userKey,
      JSON.stringify({
        v: 2,
        ts: Date.now(),
        messages: safeMessages,
      })
    );
  } catch (e) {
    // ignore
  }
}

function animateAssistantTyping({ id, fullText, setMessages, minMs, maxMs }) {
  let i = 0;
  const len = fullText.length;

  const nextDelay = (ch) => {
    // Small natural pauses on punctuation
    if (ch === '.' || ch === '!' || ch === '?' || ch === '\n') return Math.min(220, maxMs * 10);
    if (ch === ',' || ch === ';' || ch === ':') return Math.min(140, maxMs * 6);
    const base = minMs + Math.random() * (maxMs - minMs);
    return Math.floor(base);
  };

  const step = () => {
    i = Math.min(i + 2, len); // reveal 2 chars per tick for speed
    const slice = fullText.slice(0, i);
    setMessages((prev) =>
      prev.map((m) => {
        if (!m || m.id !== id) return m;
        const done = i >= len;
        return { ...m, content: slice, isTyping: !done };
      })
    );
    if (i < len) {
      const ch = fullText[i - 1];
      setTimeout(step, nextDelay(ch));
    }
  };

  setTimeout(step, 40);
}
/**
 * Minimal, safe rich-text renderer for **bold** emphasis.
 * - Supports **bold** segments
 * - Preserves new lines
 * - Does NOT render HTML
 */
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
  // Split by **...** markers, keeping delimiters
  const parts = line.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((p, i) => {
    const key = `l-${li}-p-${i}`;
    if (p.startsWith('**') && p.endsWith('**') && p.length >= 4) {
      return <strong key={key}>{p.slice(2, -2)}</strong>;
    }
    return <React.Fragment key={key}>{p}</React.Fragment>;
  });
}


