<script>
  import { onMount } from 'svelte';
  import Home from '$lib/components/Home.svelte';
  import { translator } from '$lib/i18n.js';
  import { initCompare } from '$lib/compare.js';

  let { data } = $props();
  const t = $derived(translator(data.dict));

  onMount(() => initCompare());
</script>

<svelte:head>
  <title>{t('meta_title')}</title>
  <meta name="description" content={t('meta_description')} />
  <meta property="og:title" content={t('meta_title')} />
  <meta property="og:description" content={t('meta_description')} />
</svelte:head>

<Home {t} lang={data.lang} />
