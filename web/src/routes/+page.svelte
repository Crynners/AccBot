<script>
  import { LANGS } from '$lib/i18n.js';

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
  <link rel="alternate" hreflang="x-default" href="https://accbot.eu/en/" />
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
