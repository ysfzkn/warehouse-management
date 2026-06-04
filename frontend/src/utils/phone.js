const PHONE_PREFIX = '+90';

const normalizeDigits = (value = '') => {
  if (!value) return '';
  const digitsOnly = value.replace(/\D/g, '');

  if (digitsOnly.length >= 12 && digitsOnly.startsWith('90')) {
    return digitsOnly.slice(2, 12);
  }
  if (digitsOnly.length === 11 && digitsOnly.startsWith('0')) {
    return digitsOnly.slice(1, 11);
  }
  if (digitsOnly.length > 10) {
    return digitsOnly.slice(-10);
  }
  return digitsOnly.slice(0, 10);
};

const formatGroups = (digits = '') => {
  const clean = normalizeDigits(digits);
  const groups = [];
  const sizes = [3, 3, 2, 2];
  let index = 0;
  sizes.forEach(size => {
    if (clean.length > index) {
      groups.push(clean.slice(index, Math.min(clean.length, index + size)));
      index += size;
    }
  });
  return groups.join(' ');
};

export const extractPhoneDigits = normalizeDigits;

export const isPhoneComplete = (digits) => normalizeDigits(digits).length === 10;

export const formatPhoneForSubmit = (digits) => {
  const clean = normalizeDigits(digits);
  return clean.length === 10 ? `${PHONE_PREFIX}${clean}` : '';
};

export const formatPhoneForDisplay = (digits) => {
  const clean = normalizeDigits(digits);
  if (!clean) return '';
  return `${PHONE_PREFIX} ${formatGroups(clean)}`.trim();
};

export const formatPhoneInputValue = (digits) => formatGroups(digits);

export const PHONE_PLACEHOLDER = '555 123 45 67';

const phoneUtils = {
  PHONE_PREFIX,
  extractPhoneDigits,
  isPhoneComplete,
  formatPhoneForSubmit,
  formatPhoneForDisplay,
  formatPhoneInputValue,
  PHONE_PLACEHOLDER
};
export default phoneUtils;

