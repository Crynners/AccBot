export const SITE_URL = 'https://accbot.eu';

export const LANGS = ['en', 'cs', 'sk', 'de', 'fr', 'es', 'it', 'pt', 'pl', 'uk', 'ru', 'zh', 'ja'];

export function translator(dict) {
  return (key) => dict[key] ?? key;
}

/** Exposes the current dictionary to the vanilla scripts (copy button, comparison chart). */
export function exposeTranslator(lang, dict) {
  window.AccBotI18n = {
    current: () => lang,
    t: translator(dict)
  };
}
