import { LANGS, SITE_URL } from '$lib/i18n.js';

export const prerender = true;

export function GET() {
  const pages = ['', 'privacy/'];
  const urls = pages.flatMap((p) =>
    LANGS.map((l) => {
      const alts = LANGS.map(
        (a) => `<xhtml:link rel="alternate" hreflang="${a}" href="${SITE_URL}/${a}/${p}"/>`
      ).join('');
      return `<url><loc>${SITE_URL}/${l}/${p}</loc>${alts}</url>`;
    })
  );
  const xml =
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">' +
    urls.join('') +
    '</urlset>\n';
  return new Response(xml, { headers: { 'Content-Type': 'application/xml' } });
}
