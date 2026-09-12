import { readListPosition, saveListPosition } from '../listPosition';

beforeEach(() => {
  sessionStorage.clear();
});

describe('saveListPosition', () => {
  it('remembers how far the list was loaded and where it was scrolled', () => {
    saveListPosition('/urunler', 4, 2400);

    expect(readListPosition('/urunler', true)).toEqual({ pages: 4, scrollY: 2400 });
  });

  it('does not store a position a fresh visit already produces', () => {
    saveListPosition('/urunler', 1, 0);

    expect(sessionStorage.getItem('list-pos:/urunler')).toBeNull();
  });

  it('forgets a list the visitor scrolled back to the start of', () => {
    saveListPosition('/urunler', 5, 3000);
    saveListPosition('/urunler', 1, 0);

    expect(readListPosition('/urunler', true)).toBeNull();
  });

  it('keeps a single page once it has been scrolled into', () => {
    saveListPosition('/urunler', 1, 900);

    expect(readListPosition('/urunler', true)).toBeNull();
  });
});

describe('readListPosition', () => {
  it('restores nothing unless the visitor pressed back', () => {
    saveListPosition('/urunler', 4, 2400);

    // Arriving from a link or a fresh load should start at the top of the list.
    expect(readListPosition('/urunler', false)).toBeNull();
  });

  it('keeps lists apart by their filters', () => {
    saveListPosition('/urunler?marka=simfer', 3, 1500);

    expect(readListPosition('/urunler?marka=fakir', true)).toBeNull();
    expect(readListPosition('/urunler?marka=simfer', true).pages).toBe(3);
  });

  it('caps a very long list so the return trip stays one quick request', () => {
    saveListPosition('/urunler', 40, 90000);

    expect(readListPosition('/urunler', true).pages).toBe(10);
  });

  it('ignores a stored value that is not usable', () => {
    sessionStorage.setItem('list-pos:/urunler', 'bozuk json');
    expect(readListPosition('/urunler', true)).toBeNull();

    sessionStorage.setItem('list-pos:/urunler', JSON.stringify({ pages: 'çok', scrollY: 10 }));
    expect(readListPosition('/urunler', true)).toBeNull();
  });

  it('survives a browser that refuses storage entirely', () => {
    const original = Object.getOwnPropertyDescriptor(window, 'sessionStorage');
    Object.defineProperty(window, 'sessionStorage', {
      configurable: true,
      get() {
        throw new Error('private mode');
      },
    });

    expect(() => saveListPosition('/urunler', 3, 100)).not.toThrow();
    expect(readListPosition('/urunler', true)).toBeNull();

    Object.defineProperty(window, 'sessionStorage', original);
  });

  it('needs a key', () => {
    expect(readListPosition('', true)).toBeNull();
    expect(() => saveListPosition('', 3, 100)).not.toThrow();
  });
});
