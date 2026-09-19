/**
 * Para birimi biçimlendirme.
 *
 * Aynı `Intl.NumberFormat('tr-TR', …)` kurgusu projede yirmiden fazla dosyada tek tek yazılmış
 * durumda; asıl sorun kopya sayısı değil, biçimlendirmeyi hiç yapmayan ekranlar. Kargo denemesi
 * fiyatları ham sayı olarak basıyordu ve ekranda "119.27250000000001" görünüyordu — sunucudan
 * gelen ondalık, JavaScript'in kayan nokta gösterimiyle olduğu gibi yazılınca böyle oluyor.
 */
const TRY_FORMAT = new Intl.NumberFormat('tr-TR', {
  style: 'currency',
  currency: 'TRY',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** 119.27250000000001 → "₺119,27". Sayıya çevrilemeyen değer için tire döner. */
export const formatTRY = (value, fallback = '—') => {
  if (value === null || value === undefined || value === '') return fallback;
  const number = Number(value);
  return Number.isFinite(number) ? TRY_FORMAT.format(number) : fallback;
};

export default formatTRY;
