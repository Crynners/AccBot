/** Locale-aware number, money and date formatting for the mock UI. */
export const fmtNum = (lang, n, digits = 0) =>
  new Intl.NumberFormat(lang, { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(n);

export const fmtMoney = (lang, n, currency = 'EUR', digits = 0) =>
  new Intl.NumberFormat(lang, {
    style: 'currency',
    currency,
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  }).format(n);

export const fmtDate = (lang, iso) =>
  new Intl.DateTimeFormat(lang, { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(iso));

export const tpl = (str, ...args) => args.reduce((s, a, i) => s.replace(`{${i}}`, a), str);
