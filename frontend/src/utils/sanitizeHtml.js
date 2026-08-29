import DOMPurify from 'dompurify';

/**
 * Sanitises HTML before it is handed to `dangerouslySetInnerHTML`.
 *
 * React escapes everything it renders normally, so the only XSS sinks in this app are
 * the handful of places that deliberately inject stored HTML: CMS pages and the legal
 * contract modal. Those were rendering the server's string verbatim, which meant the
 * server-side sanitiser was the sole line of defence — and it was a regex denylist that
 * missed unquoted event handlers such as `<img src=x onerror=alert(1)>`.
 *
 * The backend now sanitises with jsoup on write; this is the second, independent pass
 * on read, so content written before that change (or through any path that bypasses it)
 * still cannot execute.
 *
 * The allowlist matches what the rich-text editor can produce, including allowlisted
 * `iframe` embeds for maps and video.
 */
const CONFIG = {
  ADD_TAGS: ['iframe'],
  ADD_ATTR: ['allow', 'allowfullscreen', 'frameborder', 'scrolling', 'target', 'loading'],
  // Anything not http(s)/mailto/tel — javascript:, data:, vbscript: — is dropped.
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto|tel):|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
};

// Force every link that opens a new tab to carry rel="noopener noreferrer",
// otherwise the opened page can navigate this one via window.opener.
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.getAttribute('target')) {
    node.setAttribute('rel', 'noopener noreferrer');
  }
});

export function sanitizeHtml(html) {
  if (html == null) return '';
  return DOMPurify.sanitize(String(html), CONFIG);
}

/** Convenience wrapper: `<div {...dangerousHtml(page.content)} />` */
export function dangerousHtml(html) {
  return { dangerouslySetInnerHTML: { __html: sanitizeHtml(html) } };
}

export default sanitizeHtml;
