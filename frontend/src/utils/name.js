/**
 * Turkish-aware name capitalisation.
 *
 * Names are typed in a hurry — "ayşe yılmaz", "AYŞE YILMAZ", "  ayşe   yılmaz " — and end up
 * stored that way, so the same person looks like three different people across the order list,
 * the delivery note and the invoice. Normalising on blur keeps every screen consistent without
 * fighting the caret while the user is still typing.
 *
 * The Turkish rule that catches people out: the capital of "i" is "İ" and the capital of "ı" is
 * "I". `toUpperCase()` without the tr locale turns "işbank" into "ISBANK" instead of "İşbank".
 */

/** Words that stay lower-case in the middle of a name. */
const LOWERCASE_PARTICLES = new Set(['ve', 'ile']);

const upperTr = (ch) => {
  if (ch === 'i') return 'İ';
  if (ch === 'ı') return 'I';
  return ch.toLocaleUpperCase('tr-TR');
};

const lowerTr = (str) => str.toLocaleLowerCase('tr-TR');

/**
 * Capitalises one word, keeping hyphenated and apostrophised parts intact:
 * "mehmet-ali" → "Mehmet-Ali", "o'brien" → "O'Brien".
 */
const capitalizeWord = (word) => {
  if (!word) return word;
  return word
    .split(/([-'’])/)
    .map((part) => {
      if (part.length === 0 || /[-'’]/.test(part)) return part;
      const lower = lowerTr(part);
      return upperTr(lower.charAt(0)) + lower.slice(1);
    })
    .join('');
};

/**
 * "  ayşe   YILMAZ " → "Ayşe Yılmaz". Returns the input unchanged when it is empty, so it is
 * safe to call on every blur.
 */
export function toTitleCaseTr(value) {
  if (typeof value !== 'string') return value;
  const collapsed = value.trim().replace(/\s+/g, ' ');
  if (!collapsed) return '';
  return collapsed
    .split(' ')
    .map((word, index) =>
      index > 0 && LOWERCASE_PARTICLES.has(lowerTr(word)) ? lowerTr(word) : capitalizeWord(word)
    )
    .join(' ');
}

/**
 * Product names, where blind title casing does real damage.
 *
 * Only lower-case words are touched. Anything already carrying capitals is left exactly as
 * typed, which protects three things at once:
 *
 * - model codes and capacities — "BD3086W3VN", "9KG", "A+++"
 * - brands and acronyms — "LG", "USB", "PROFILO"
 * - deliberate mixed case — "iPhone", "InstaView"
 *
 * The last one is not fussiness but a genuine dead end: in ALL-CAPS text a Turkish "I" can mean
 * either "ı" or "i", so "PROFILO" would become "Profılo" while "BUZDOLABI" needs exactly that
 * rule to stay "Buzdolabı". Guessing is worse than leaving the operator's capitals alone.
 */
export function toProductNameCase(value) {
  if (typeof value !== 'string') return value;
  const collapsed = value.trim().replace(/\s+/g, ' ');
  if (!collapsed) return '';
  return collapsed
    .split(' ')
    .map((word) => {
      if (/\d/.test(word)) return word;
      if (/[A-ZÇĞİÖŞÜ]/.test(word)) return word; // already capitalised somewhere — leave it
      return capitalizeWord(word);
    })
    .join(' ');
}

/**
 * Stock notes, which are sometimes just a customer name ("ahmet yılmaz") and sometimes a whole
 * sentence ("ayşe yılmaz'a kalan 2 adet teslim edildi").
 *
 * Title casing the sentence would produce "Kalan 2 Adet Teslim Edildi", so the shape decides:
 * a short, purely alphabetic value is treated as a name and title cased; anything longer only
 * gets its first letter capitalised, leaving the operator's wording intact.
 */
export function toNoteCase(value) {
  if (typeof value !== 'string') return value;
  const collapsed = value.trim().replace(/\s+/g, ' ');
  if (!collapsed) return '';

  const words = collapsed.split(' ');
  const looksLikeName =
    words.length <= 4 && words.every((w) => /^[A-Za-zÇĞİÖŞÜçğıöşü]+(['’-][A-Za-zÇĞİÖŞÜçğıöşü]+)*$/.test(w));
  if (looksLikeName) return toTitleCaseTr(collapsed);

  const first = collapsed.charAt(0);
  return upperTr(first) + collapsed.slice(1);
}

/**
 * Ready-made blur handler: `onBlur={handleNameBlur((v) => setName(v))}`.
 * Only calls back when the value actually changed, so it never causes a needless re-render.
 */
export function handleNameBlur(setter) {
  return (event) => {
    const next = toTitleCaseTr(event.target.value);
    if (next !== event.target.value) setter(next);
  };
}

export default toTitleCaseTr;
