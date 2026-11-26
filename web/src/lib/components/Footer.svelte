<script>
  let { t, lang, isHome = true } = $props();
  const home = () => (isHome ? '#' : `/${lang}/`);
  const sec = (id) => (isHome ? `#${id}` : `/${lang}/#${id}`);

</script>

<footer class="site-footer">
    <div class="container">
      <div class="footer-inner">
        <div class="footer-left">{t('footer_made')}</div>
        <div class="footer-links">
          <a href="https://github.com/crynners/AccBot/blob/main/LICENSE" target="_blank" rel="noopener">{t('footer_license')}</a>
          <a href="https://github.com/crynners/AccBot" target="_blank" rel="noopener">{t('footer_source')}</a>
          <a href={sec('support')}>{t('nav_support')}</a>
          <a href="/{lang}/privacy/">{t('footer_privacy')}</a>
          <a href="https://github.com/crynners/AccBot/issues" target="_blank" rel="noopener">{t('footer_issues')}</a>
          <a href="https://t.me/accbotsupport" target="_blank" rel="noopener">Telegram</a>
          <a href="https://www.facebook.com/profile.php?id=61580714271760" target="_blank" rel="noopener">Facebook</a>
        </div>
      </div>
    </div>
  </footer>
