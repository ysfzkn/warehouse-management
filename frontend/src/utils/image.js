// Simple client-side image compression & resize helper
// Falls back to original file if the browser does not support required APIs

export async function compressImage(file, options = {}) {
  const {
    maxWidth = 1920,
    maxHeight = 1920,
    quality = 0.75,
    mimeType = 'image/jpeg'
  } = options;

  if (typeof window === 'undefined' || !window.FileReader || !window.Image || !document.createElement) {
    return file;
  }

  if (!file || !file.type.startsWith('image/')) {
    return file;
  }

  try {
    const image = await loadImage(file);
    const { width, height } = image;

    const scale = Math.min(maxWidth / width, maxHeight / height, 1);
    if (scale >= 1) {
      // Already within bounds; no need to resize
      return file;
    }

    const targetWidth = Math.round(width * scale);
    const targetHeight = Math.round(height * scale);

    const canvas = document.createElement('canvas');
    canvas.width = targetWidth;
    canvas.height = targetHeight;

    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return file;
    }

    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(image, 0, 0, targetWidth, targetHeight);

    const outputType = mimeType || file.type || 'image/jpeg';

    const blob = await new Promise((resolve, reject) => {
      canvas.toBlob(
        (result) => {
          if (result) resolve(result);
          else reject(new Error('Canvas toBlob failed'));
        },
        outputType,
        quality
      );
    });

    // Preserve original file name if possible
    const compressedFile = new File([blob], file.name || 'photo.jpg', {
      type: blob.type,
      lastModified: Date.now()
    });

    return compressedFile;
  } catch (e) {
    console.warn('compressImage failed, using original file', e);
    return file;
  }
}

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = reader.result;
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}


