<script>
  import { LANGS, SITE_URL } from '$lib/i18n.js';

  // Picks the visitor's language (old #cs links, saved choice, browser) and redirects to /<lang>/.
  const detect = `(function () {
    var L = ${JSON.stringify(LANGS)};
    var h = location.hash.slice(1);
    var s = null;
    try { s = localStorage.getItem('accbot-lang'); } catch (e) {}
    var c = [h, s].concat(navigator.languages || [navigator.language]);
    var l = 'en';
    for (var i = 0; i < c.length; i++) {
      var x = (c[i] || '').toLowerCase().split('-')[0];
      if (L.indexOf(x) >= 0) { l = x; break; }
    }
    var keep = h && L.indexOf(h) < 0 ? '#' + h : '';
    location.replace('/' + l + '/' + keep);
  })();`;
  const redirect = '<scr' + 'ipt>' + detect + '</scr' + 'ipt>';
</script>

<svelte:head>
  <title>AccBot</title>
  <meta name="robots" content="noindex" />
  <link rel="alternate" hreflang="x-default" href="{SITE_URL}/en/" />
  <!-- Link previews (Facebook, Telegram) read these without running the redirect script. -->
  <meta property="og:type" content="website" />
  <meta property="og:url" content="{SITE_URL}/" />
  <meta property="og:site_name" content="AccBot" />
  <meta property="og:title" content="AccBot | Automatic Bitcoin DCA on your phone" />
  <meta property="og:description" content="Free, open-source Android app that buys Bitcoin and other crypto on a schedule, straight from your own exchange account." />
  <meta property="og:image" content="{SITE_URL}/img/share-en.jpg" />
  <meta property="og:image:type" content="image/jpeg" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:image" content="{SITE_URL}/img/share-en.jpg" />
  <!-- eslint-disable-next-line svelte/no-at-html-tags -->
  {@html redirect}
  <noscript><meta http-equiv="refresh" content="0; url=/en/" /></noscript>
</svelte:head>

<main class="container" style="padding: 120px 20px; text-align: center">
  <p>
    {#each LANGS as l (l)}
      <a href="/{l}/" style="margin: 0 8px">{l.toUpperCase()}</a>
    {/each}
  </p>
</main>
