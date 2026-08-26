import { toTitleCaseTr, toProductNameCase, toNoteCase } from '../name';

describe('toTitleCaseTr', () => {
  it('capitalises each word of a lower-case name', () => {
    expect(toTitleCaseTr('ayşe yılmaz')).toBe('Ayşe Yılmaz');
  });

  it('fixes an all-caps name', () => {
    expect(toTitleCaseTr('AYŞE YILMAZ')).toBe('Ayşe Yılmaz');
  });

  it('uses the Turkish capital of i and ı', () => {
    // "i" → "İ" and "ı" → "I"; the default toUpperCase gets both wrong.
    expect(toTitleCaseTr('irem ışık')).toBe('İrem Işık');
  });

  it('collapses stray whitespace', () => {
    expect(toTitleCaseTr('  ayşe    yılmaz  ')).toBe('Ayşe Yılmaz');
  });

  it('keeps hyphenated and apostrophised parts capitalised', () => {
    expect(toTitleCaseTr('mehmet-ali kaya')).toBe('Mehmet-Ali Kaya');
    expect(toTitleCaseTr("o'brien")).toBe("O'Brien");
  });

  it('leaves joining particles lower-case', () => {
    expect(toTitleCaseTr('ahmet ve mehmet')).toBe('Ahmet ve Mehmet');
  });

  it('handles three-part names', () => {
    expect(toTitleCaseTr('ali çağrı öztürk')).toBe('Ali Çağrı Öztürk');
  });

  it('returns an empty string for blank input', () => {
    expect(toTitleCaseTr('   ')).toBe('');
    expect(toTitleCaseTr('')).toBe('');
  });

  it('passes non-strings through untouched', () => {
    expect(toTitleCaseTr(null)).toBeNull();
    expect(toTitleCaseTr(undefined)).toBeUndefined();
  });
});

describe('toProductNameCase', () => {
  it('title-cases lower-case words', () => {
    expect(toProductNameCase('profilo buzdolabı')).toBe('Profilo Buzdolabı');
    expect(toProductNameCase('ankastre fırın seti')).toBe('Ankastre Fırın Seti');
  });

  it('leaves model codes and capacities exactly as typed', () => {
    // Title casing these would corrupt the code the customer searches by.
    expect(toProductNameCase('profilo BD3086W3VN buzdolabı')).toBe('Profilo BD3086W3VN Buzdolabı');
    expect(toProductNameCase('çamaşır makinesi 9KG A+++')).toBe('Çamaşır Makinesi 9KG A+++');
  });

  it('keeps brands and acronyms as typed', () => {
    expect(toProductNameCase('LG oled TV')).toBe('LG Oled TV');
    expect(toProductNameCase('usb kablo')).toBe('Usb Kablo');
  });

  it('respects deliberate mixed case', () => {
    expect(toProductNameCase('apple iPhone kılıf')).toBe('Apple iPhone Kılıf');
  });

  it('never rewrites an all-caps word, because the Turkish I is ambiguous there', () => {
    // "PROFILO" would become "Profılo" and "BUZDOLABI" needs the opposite rule — leave both.
    expect(toProductNameCase('PROFILO BUZDOLABI')).toBe('PROFILO BUZDOLABI');
  });

  it('collapses whitespace and handles blanks', () => {
    expect(toProductNameCase('  ankastre   set ')).toBe('Ankastre Set');
    expect(toProductNameCase('  ')).toBe('');
  });
});

describe('toNoteCase', () => {
  it('title-cases a note that is just a customer name', () => {
    expect(toNoteCase('ahmet yılmaz')).toBe('Ahmet Yılmaz');
    expect(toNoteCase('AYŞE YILMAZ')).toBe('Ayşe Yılmaz');
  });

  it('only capitalises the first letter of a real sentence', () => {
    // Title casing here would read "Ayşe Yılmaz'a Kalan 2 Adet Teslim Edildi".
    expect(toNoteCase("ayşe yılmaz'a kalan 2 adet teslim edildi")).toBe(
      "Ayşe yılmaz'a kalan 2 adet teslim edildi"
    );
  });

  it('uses the Turkish capital for a leading i', () => {
    expect(toNoteCase('iade alındı, depoya kondu')).toBe('İade alındı, depoya kondu');
  });

  it('handles blanks and non-strings', () => {
    expect(toNoteCase('   ')).toBe('');
    expect(toNoteCase(null)).toBeNull();
  });
});
