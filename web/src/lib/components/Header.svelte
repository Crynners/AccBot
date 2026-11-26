<script>
  let { t, lang, isHome = true } = $props();
  const home = () => (isHome ? '#' : `/${lang}/`);
  const sec = (id) => (isHome ? `#${id}` : `/${lang}/#${id}`);
  function changeLang(e) {
    const l = e.currentTarget.value;
    try { localStorage.setItem('accbot-lang', l); } catch (err) { /* storage blocked */ }
    location.href = isHome ? `/${l}/${location.hash}` : `/${l}/privacy/`;
  }
</script>

<header class="site-header" class:scrolled={!isHome}>
    <div class="header-inner">
      <a href={home()} class="logo">
        <span class="logo-text">Acc<span class="logo-btc"><span class="logo-icon">
          <svg width="48" height="48" viewBox="86 86 340 340" fill="none">
            <defs>
              <linearGradient id="hdrFade" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="white"/>
                <stop offset="28%" stop-color="white"/>
                <stop offset="40%" stop-color="black"/>
                <stop offset="60%" stop-color="black"/>
                <stop offset="72%" stop-color="white"/>
                <stop offset="100%" stop-color="white"/>
              </linearGradient>
              <mask id="hdrMask">
                <rect x="86" y="86" width="340" height="340" fill="url(#hdrFade)"/>
              </mask>
              <clipPath id="hdrClip"><circle cx="256" cy="256" r="138"/></clipPath>
            </defs>
            <g mask="url(#hdrMask)">
              <circle cx="256" cy="256" r="170" fill="none" stroke="#4ECCA3" stroke-width="8" opacity="0.3"/>
              <circle cx="256" cy="256" r="140" fill="#4ECCA3" fill-opacity="0.05" stroke="#4ECCA3" stroke-width="4"/>
              <g clip-path="url(#hdrClip)">
                <rect x="126" y="280" width="50" height="200" rx="6" fill="#4ECCA3" opacity="0.3"/>
                <rect x="196" y="230" width="50" height="250" rx="6" fill="#4ECCA3" opacity="0.5"/>
                <rect x="266" y="170" width="50" height="310" rx="6" fill="#4ECCA3" opacity="0.7"/>
                <rect x="336" y="100" width="50" height="380" rx="6" fill="#4ECCA3" opacity="0.9"/>
              </g>
              <circle cx="256" cy="256" r="85" fill="#16213E" stroke="#4ECCA3" stroke-width="4"/>
              <circle cx="256" cy="256" r="72" fill="none" stroke="#4ECCA3" stroke-width="2" stroke-dasharray="8 8" opacity="0.6"/>
            </g>
          </svg>
        </span>&#x20bf;</span>ot</span>
      </a>

      <nav>
        <ul class="nav-links">
          <li><a href={sec('features')}>{t('nav_features')}</a></li>
          <li><a href={sec('screenshots')}>{t('nav_screenshots')}</a></li>
          <li><a href={sec('exchanges')}>{t('nav_exchanges')}</a></li>
          <li><a href={sec('security')}>{t('nav_security')}</a></li>
          <li><a href={sec('compare')}>{t('nav_compare')}</a></li>
          <li><a href={sec('faq')}>{t('nav_faq')}</a></li>
          <li><a href={sec('support')}>{t('nav_support')}</a></li>
          <li><a href={sec('download')}>{t('nav_download')}</a></li>
          <li>
            <label class="lang-select-wrap">
              <span class="sr-only">{t('lang_label')}</span>
              <svg class="lang-globe" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"/></svg>
              <select id="lang-select" class="lang-select" value={lang} onchange={changeLang}>
                <option value="en">English</option>
                <option value="cs">Čeština</option>
                <option value="sk">Slovenčina</option>
                <option value="de">Deutsch</option>
                <option value="fr">Français</option>
                <option value="es">Español</option>
                <option value="it">Italiano</option>
                <option value="pt">Português</option>
                <option value="pl">Polski</option>
                <option value="uk">Українська</option>
                <option value="ru">Русский</option>
                <option value="zh">简体中文</option>
                <option value="ja">日本語</option>
              </select>
            </label>
          </li>
        </ul>
      </nav>

      <button class="burger" aria-label={t('nav_menu')}>
        <span></span>
        <span></span>
        <span></span>
      </button>
    </div>
  </header>
