import { LANGS } from '$lib/i18n.js';

/** Sets <html lang> to the language in the URL. */
export async function handle({ event, resolve }) {
  const lang = LANGS.includes(event.params.lang) ? event.params.lang : 'en';
  return resolve(event, {
    transformPageChunk: ({ html }) => html.replace('%lang%', lang)
  });
}
