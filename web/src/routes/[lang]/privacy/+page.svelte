<script>
  import Privacy from '$lib/components/Privacy.svelte';
  import { translator } from '$lib/i18n.js';

  let { data } = $props();
  const t = $derived(translator(data.dict));
</script>

<svelte:head>
  <title>{t('privacy_page_title')}</title>
  <meta name="description" content={t('meta_description')} />
  <meta property="og:title" content={t('privacy_page_title')} />
</svelte:head>

<Privacy {t} lang={data.lang} isHome={false} />
