/**
 * Calendar-date helpers for `<input type="date">`, which speaks yyyy-MM-dd in the user's own
 * timezone.
 *
 * The trap this exists to avoid: `new Date().toISOString()` converts to UTC first, so in +03 any
 * evening after 21:00 reports tomorrow's date. Shifting by the offset before formatting keeps the
 * value on the day the user is actually having.
 */
export const todayIsoDate = () => {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
};

/**
 * Formats a yyyy-MM-dd value for display. Parsed at midday on purpose — parsing "2026-08-27" as a
 * bare date makes it UTC midnight, which renders as the 26th anywhere west of Greenwich.
 */
export const formatIsoDateTr = (value) =>
  value ? new Date(value + 'T12:00:00').toLocaleDateString('tr-TR') : '';
