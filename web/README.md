# AccBot website (accbot.eu)

SvelteKit site, prerendered to static HTML with `@sveltejs/adapter-static` and hosted on Cloudflare Pages.

## Develop

```bash
cd web
npm install
npm run dev      # http://localhost:5173
npm run build    # static site in web/build
npm run preview  # serve the build
```

## Structure

- `src/routes/[lang]/` home page per language (`/en/`, `/cs/`, ...), `src/routes/[lang]/privacy/` privacy policy.
- `src/routes/+page.svelte` is `/`: it picks the language (old `#cs` links, saved choice, browser language) and redirects.
- `src/lib/locales/<lang>.json` texts. `en.json` is the base, other languages overlay it, so a missing key falls back to English. Privacy policy texts exist only in `en` and `cs`.
- `src/lib/components/` page markup (Header, Footer, Home, Privacy).
- `src/lib/compare.js` AccBot vs Štosuj calculator (Bitstamp/Binance prices, ECB USD/CZK rates via Frankfurter, no API keys).
- `static/` CSS, images and app screenshots (`static/img/screenshots/{en,cs}/`, made from `screenshots/<lang>/phone/`).

Adding a language: add `src/lib/locales/<code>.json`, the code to `LANGS` in `src/lib/i18n.js` and an `<option>` to the language select in `Header.svelte`.

## Deploy

Cloudflare Pages project connected to this repository:

- Root directory: `web`
- Build command: `npm run build`
- Build output directory: `build`
- Production branch: `main` (other branches and pull requests get preview URLs)
- Custom domain: `accbot.eu`
