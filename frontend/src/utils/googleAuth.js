/**
 * Google OAuth 2.0 sign-in kick-off, shared by the login and register pages.
 *
 * The `state` parameter is the login-CSRF defence. Without it an attacker can send a
 * victim a pre-baked callback URL carrying the attacker's own authorization code; the
 * victim's browser posts it, and they end up silently signed into the attacker's
 * account — every order and address they then enter lands in the attacker's hands.
 * We mint a random value per attempt, keep it in sessionStorage (per-tab, dropped when
 * the tab closes) and the callback refuses any response whose state does not match.
 */

const CLIENT_ID = process.env.REACT_APP_GOOGLE_CLIENT_ID || '';
const STATE_KEY = 'google_oauth_state';
const AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';

/** False when the build carries no client id — the button then stays inert. */
export const isGoogleAuthEnabled = () => Boolean(CLIENT_ID);

/**
 * Must match a "Authorized redirect URI" entry on the Google OAuth client exactly,
 * scheme and host included. Derived from the live origin so the same build works on
 * every domain it is served from.
 */
export const googleRedirectUri = () => `${window.location.origin}/auth/google/callback`;

function mintState() {
  const bytes = new Uint8Array(16);
  window.crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Sends the browser to Google's consent screen.
 * Returns false when sign-in is unavailable, so the caller can surface a message.
 */
export function startGoogleAuth() {
  if (!CLIENT_ID) return false;

  const state = mintState();
  try {
    sessionStorage.setItem(STATE_KEY, state);
  } catch {
    // No sessionStorage means no way to verify the response — better to refuse than
    // to complete a sign-in we cannot attribute to this tab.
    return false;
  }

  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    redirect_uri: googleRedirectUri(),
    response_type: 'code',
    scope: 'openid email profile',
    // The profile is read once, at sign-in, and Google is never called on the user's
    // behalf afterwards — so no `access_type=offline` refresh token to leave lying
    // around, and no consent screen re-prompt on every single login.
    prompt: 'select_account',
    state,
  });

  window.location.href = `${AUTH_ENDPOINT}?${params.toString()}`;
  return true;
}

/**
 * Reads and clears the pending state. Single use: a callback URL replayed a second
 * time finds nothing stored and is rejected.
 */
export function consumeGoogleState() {
  try {
    const state = sessionStorage.getItem(STATE_KEY);
    sessionStorage.removeItem(STATE_KEY);
    return state;
  } catch {
    return null;
  }
}
