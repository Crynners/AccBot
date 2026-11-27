<script>
  import { onMount } from 'svelte';
  import { page } from '$app/state';
  import Header from '$lib/components/Header.svelte';
  import Footer from '$lib/components/Footer.svelte';
  import { LANGS, SITE_URL, exposeTranslator, translator } from '$lib/i18n.js';
  import { initSite } from '$lib/site.js';

  let { data, children } = $props();
  const t = $derived(translator(data.dict));
  const isHome = $derived(page.route.id === '/[lang]');
  const subpath = $derived(isHome ? '' : 'privacy/');
  const shotLang = $derived(['cs', 'sk'].includes(data.lang) ? 'cs' : 'en');

  onMount(() => {
    exposeTranslator(data.lang, data.dict);
    try {
      localStorage.setItem('accbot-lang', data.lang);
    } catch (e) {
      /* storage blocked */
    }
    initSite();
  });
</script>

<svelte:head>
  <link rel="canonical" href="{SITE_URL}/{data.lang}/{subpath}" />
  {#each LANGS as l (l)}
    <link rel="alternate" hreflang={l} href="{SITE_URL}/{l}/{subpath}" />
  {/each}
  <link rel="alternate" hreflang="x-default" href="{SITE_URL}/en/{subpath}" />
  <meta property="og:type" content="website" />
  <meta property="og:url" content="{SITE_URL}/{data.lang}/{subpath}" />
  <meta property="og:site_name" content="AccBot" />
  <meta property="og:image" content="{SITE_URL}/img/share-{shotLang}.jpg" />
  <meta property="og:image:type" content="image/jpeg" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta property="og:image:alt" content={t('meta_title')} />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:image" content="{SITE_URL}/img/share-{shotLang}.jpg" />
</svelte:head>

<Header {t} lang={data.lang} {isHome} />
{@render children()}
<Footer {t} lang={data.lang} {isHome} />
