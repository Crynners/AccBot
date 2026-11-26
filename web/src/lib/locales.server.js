import { LANGS } from './i18n.js';

const files = import.meta.glob('./locales/*.json', { eager: true, import: 'default' });

/** English is the base; every other language overlays it, so a missing key falls back to English. */
export function loadDict(lang) {
  if (!LANGS.includes(lang)) return null;
  return { ...files['./locales/en.json'], ...files[`./locales/${lang}.json`] };
}
