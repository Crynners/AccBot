// Public address of the site, used for canonical, hreflang, sitemap and share-image URLs.
// Switch to 'https://accbot.eu' once the domain is connected in Cloudflare Pages.
export const SITE_URL = 'https://accbot-4f6.pages.dev';

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

/** Lightning address for donations (user@domain). The Lightning card is hidden while this is empty. */
export const LIGHTNING_ADDRESS = 'jumpymop204@walletofsatoshi.com';
