export const MAX_PAGE_BUTTONS = 5;

export function buildPageList(currentPage, totalPages, maxButtons = MAX_PAGE_BUTTONS) {
  if (!Number.isFinite(totalPages) || totalPages <= 0) {
    return [0];
  }

  const safeCurrent = Math.max(0, Math.min(currentPage ?? 0, totalPages - 1));
  const safeMax = Math.max(1, maxButtons);
  const maxStart = Math.max(0, totalPages - safeMax);
  const start = Math.max(0, Math.min(safeCurrent - Math.floor(safeMax / 2), maxStart));
  const end = Math.min(totalPages, start + safeMax);

  const pages = [];
  for (let i = start; i < end; i += 1) {
    pages.push(i);
  }

  if (pages.length === 0) {
    pages.push(0);
  }
  return pages;
}

