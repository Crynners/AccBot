<script>
  let { t, lang, isHome = true } = $props();
  const home = () => (isHome ? '#' : `/${lang}/`);
  const sec = (id) => (isHome ? `#${id}` : `/${lang}/#${id}`);

</script>

<main>
    <section class="privacy-page">
      <div class="container">
        <div class="privacy-content">
          <h1>{t('privacy_title')}</h1>
          <p class="updated">{t('privacy_effective_date')}</p>
          <p class="updated">{t('privacy_last_updated')}</p>

          <p>{t('privacy_intro')}</p>

          <h2>{t('privacy_collection_title')}</h2>
          <p>{t('privacy_collection_text')}</p>

          <h2>{t('privacy_stored_title')}</h2>
          <p>{t('privacy_stored_text')}</p>
          <ul>
            <li>{t('privacy_stored_item1')}</li>
            <li>{t('privacy_stored_item2')}</li>
            <li>{t('privacy_stored_item3')}</li>
            <li>{t('privacy_stored_item4')}</li>
            <li>{t('privacy_stored_item5')}</li>
            <li>{t('privacy_stored_item6')}</li>
            <li>{t('privacy_stored_item7')}</li>
            <li>{t('privacy_stored_item8')}</li>
          </ul>

          <h2>{t('privacy_network_title')}</h2>
          <p>{t('privacy_network_intro')}</p>
          <p><strong>{t('privacy_network_exchanges')}</strong> <span>{t('privacy_network_exchanges_text')}</span></p>
          <p><strong>{t('privacy_network_market')}</strong> <span>{t('privacy_network_market_text')}</span></p>
          <p><strong>{t('privacy_network_mlkit')}</strong> <span>{@html t('privacy_network_mlkit_text')}</span></p>

          <h2>{t('privacy_security_title')}</h2>
          <p>{t('privacy_security_text')}</p>
          <ul>
            <li>{t('privacy_security_item1')}</li>
            <li>{t('privacy_security_item2')}</li>
            <li>{t('privacy_security_item3')}</li>
            <li>{t('privacy_security_item4')}</li>
            <li>{t('privacy_security_item5')}</li>
            <li>{t('privacy_security_item6')}</li>
          </ul>

          <h2>{t('privacy_permissions_title')}</h2>
          <ul>
            <li>{t('privacy_perm_internet')}</li>
            <li>{t('privacy_perm_camera')}</li>
            <li>{t('privacy_perm_battery')}</li>
            <li>{t('privacy_perm_alarm')}</li>
            <li>{t('privacy_perm_foreground')}</li>
            <li>{t('privacy_perm_notifications')}</li>
            <li>{t('privacy_perm_network')}</li>
            <li>{t('privacy_perm_boot')}</li>
            <li>{t('privacy_perm_wakelock')}</li>
          </ul>

          <h2>{t('privacy_thirdparty_title')}</h2>
          <p>{t('privacy_thirdparty_text')}</p>
          <ul>
            <li>{t('privacy_thirdparty_exchanges')}</li>
            <li>{t('privacy_thirdparty_coingecko')}</li>
            <li>{t('privacy_thirdparty_cryptocompare')}</li>
            <li>{t('privacy_thirdparty_alternative')}</li>
            <li>{@html t('privacy_thirdparty_mlkit')}</li>
          </ul>

          <h2>{t('privacy_deletion_title')}</h2>
          <p>{t('privacy_deletion_text')}</p>

          <h2>{t('privacy_children_title')}</h2>
          <p>{t('privacy_children_text')}</p>

          <h2>{t('privacy_biometric_title')}</h2>
          <p>{t('privacy_biometric_text')}</p>

          <h2>{t('privacy_backup_title')}</h2>
          <p>{t('privacy_backup_text')}</p>

          <h2>{t('privacy_retention_title')}</h2>
          <p>{t('privacy_retention_text')}</p>

          <h2>{t('privacy_rights_title')}</h2>
          <p>{t('privacy_rights_text')}</p>

          <h2>{t('privacy_international_title')}</h2>
          <p>{t('privacy_international_text')}</p>

          <h2>{t('privacy_changes_title')}</h2>
          <p>{t('privacy_changes_text')}</p>

          <h2>{t('privacy_contact_title')}</h2>
          <p>{t('privacy_contact_text')}</p>

          <a href={home()} class="privacy-back-link">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
            <span>{t('privacy_back')}</span>
          </a>
        </div>
      </div>
    </section>
  </main>
