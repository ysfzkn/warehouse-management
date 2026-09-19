import { formatTRY } from '../money';

describe('formatTRY', () => {
  // Kargo denemesi fiyatları ham basıyordu ve ekranda "119.27250000000001" görünüyordu.
  it('sunucudan gelen kayan nokta artığını iki haneye indirir', () => {
    expect(formatTRY(119.27250000000001)).toBe('₺119,27');
    expect(formatTRY(102.30000000000001)).toBe('₺102,30');
  });

  it('tam sayıda bile kuruş gösterir', () => {
    expect(formatTRY(55)).toBe('₺55,00');
  });

  it('metin olarak gelen sayıyı da biçimlendirir', () => {
    expect(formatTRY('95.58333333333334')).toBe('₺95,58');
  });

  it('değer yoksa tire döner', () => {
    expect(formatTRY(null)).toBe('—');
    expect(formatTRY(undefined)).toBe('—');
    expect(formatTRY('')).toBe('—');
    expect(formatTRY('fiyat yok')).toBe('—');
  });

  it('sıfır bir değerdir, eksik değil', () => {
    expect(formatTRY(0)).toBe('₺0,00');
  });
});
