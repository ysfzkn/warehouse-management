/**
 * Client-side image format normalization for uploads.
 *
 * The backend (and the OpenAI Images API) only accept JPEG, PNG and WebP. Modern
 * cameras and crawled stores often produce AVIF / HEIC, which Java's ImageIO can't
 * decode — uploading or reusing those triggers an "unsupported format" rejection.
 *
 * Browsers, however, already ship AVIF (and on some platforms HEIC) decoders. So
 * we decode with the browser, repaint onto a canvas and re-encode as PNG. This is
 * free, instant and keeps the workflow smooth. If the browser can't decode the
 * format either, we fall back gracefully.
 */

const SAFE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

/** Longest edge of the re-encoded image; keeps converted PNGs from ballooning. */
const MAX_DIMENSION = 2400;

const isSafeType = (file) => {
  const type = (file.type || '').toLowerCase();
  if (SAFE_TYPES.has(type)) return true;
  // Some browsers report an empty type for known-good extensions — trust those.
  if (!type) {
    return /\.(jpe?g|png|webp)$/i.test(file.name || '');
  }
  return false;
};

/** Repaints an already-decoded bitmap onto a canvas and encodes it as a PNG File. */
const bitmapToPngFile = async (bitmap, fileName) => {
  const scale = Math.min(1, MAX_DIMENSION / Math.max(bitmap.width, bitmap.height));
  const width = Math.max(1, Math.round(bitmap.width * scale));
  const height = Math.max(1, Math.round(bitmap.height * scale));

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;
  ctx.drawImage(bitmap, 0, 0, width, height);

  const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'));
  if (!blob) return null;
  const baseName = (fileName || 'image').replace(/\.[^.]+$/, '') || 'image';
  return new File([blob], `${baseName}.png`, { type: 'image/png' });
};

/**
 * Returns a File guaranteed to be JPEG/PNG/WebP when possible. Safe formats pass
 * through untouched; AVIF/HEIC/other decodable formats are converted to PNG.
 *
 * @param {File} file the user-selected file
 * @returns {Promise<File>} the original or a converted PNG File
 */
export async function toUploadableImage(file) {
  if (!file || isSafeType(file)) return file;
  if (typeof createImageBitmap !== 'function') return file;

  let bitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    return file; // browser can't decode this format — let the backend report it
  }
  try {
    const converted = await bitmapToPngFile(bitmap, file.name);
    return converted || file;
  } catch {
    return file;
  } finally {
    bitmap.close?.();
  }
}

/**
 * Force-decodes raw image bytes (a Blob, e.g. fetched from the server) and
 * re-encodes them to a PNG File, regardless of the declared MIME type. Used to
 * rescue stored photos whose bytes are a format the backend can't read (such as
 * AVIF saved under a `.jpg` name) but the browser renders fine.
 *
 * @returns {Promise<File|null>} a PNG File, or null if the browser can't decode it
 */
export async function reencodeImageToPng(blob, fileName = 'image.png') {
  if (!blob || typeof createImageBitmap !== 'function') return null;

  let bitmap;
  try {
    bitmap = await createImageBitmap(blob);
  } catch {
    return null; // truly undecodable (e.g. HEIC on a browser without a codec)
  }
  try {
    return await bitmapToPngFile(bitmap, fileName);
  } catch {
    return null;
  } finally {
    bitmap.close?.();
  }
}
