import { buildPageList } from '../pagination';

describe('buildPageList', () => {
  it('returns a sane default when total pages missing', () => {
    expect(buildPageList(0, 0)).toEqual([0]);
  });

  it('centers around the current page when possible', () => {
    expect(buildPageList(5, 10)).toEqual([3, 4, 5, 6, 7]);
  });

  it('caps to available pages near the end', () => {
    expect(buildPageList(9, 10)).toEqual([5, 6, 7, 8, 9]);
  });
});

