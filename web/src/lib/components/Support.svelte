<script>
  import QRCode from 'qrcode';
  import { LIGHTNING_ADDRESS } from '$lib/i18n.js';

  let { t } = $props();

  const COINS = [
    { id: 'btc', name: 'Bitcoin', ticker: 'BTC', address: 'bc1q2hz79m4csklecqgusu9e2yjnrr6e9ca6nhu0at' },
    { id: 'eth', name: 'Ethereum', ticker: 'ETH', address: '0x8A944bcb5919dF04C5207939749C40A61f88188C' },
    { id: 'ltc', name: 'Litecoin', ticker: 'LTC', address: 'LTXdCFBYHgVLa8cBNBqwEvaQLi8tENY5R3' },
    { id: 'xmr', name: 'Monero', ticker: 'XMR', address: '49QBko3UdegAkx6g8foqjs9efQD6rrhsPEoTqP9HmA2LCUZsJ8xBD2JZSMEdzhA5NJ9SrVhzu2uJXRUvL2kAiV45LyDBCUt' },
    { id: 'doge', name: 'Dogecoin', ticker: 'DOGE', address: 'DR9mEaVLmx3gxqiqffwYQcLsT1upRL3xe9' },
    { id: 'dot', name: 'Polkadot', ticker: 'DOT', address: '15sBCVyWu5Gy9VnzQpid4ggC1MmguBBd1xotUVbsbbRWddun' },
    { id: 'ada', name: 'Cardano', ticker: 'ADA', address: 'addr1qxgfp7xf8rpg7laque78queavpfdztajgl3hr8kuanuqgdysjruvjwxz3al6penuwpen6czj6yhmy3lrwx0dem8cqs6qr8y8fj' }
  ];

  let coinId = $state('btc');
  const coin = $derived(COINS.find((c) => c.id === coinId));
  // Which address was just copied ('ln' or a coin id), for the "Copied" state.
  let copied = $state(null);
  let timer;

  async function copy(text, key) {
    try {
      await navigator.clipboard.writeText(text);
      copied = key;
      clearTimeout(timer);
      timer = setTimeout(() => (copied = null), 2000);
    } catch (e) {
      /* clipboard blocked: the address stays selectable */
    }
  }

  function pick(id) {
    coinId = id;
    copied = null;
  }

  // QR code for the Lightning address, rendered as an SVG path (wallets read "lightning:" + address).
  function qrPath(text) {
    const { size, data } = QRCode.create(text, { errorCorrectionLevel: 'M' }).modules;
    let d = '';
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) if (data[y * size + x]) d += `M${x + 2} ${y + 2}h1v1h-1z`;
    return { size: size + 4, d };
  }
  const lnQr = LIGHTNING_ADDRESS ? qrPath(`lightning:${LIGHTNING_ADDRESS}`) : null;

  const HELP = [
    {
      href: 'https://github.com/crynners/AccBot',
      title: 'support_star_title',
      desc: 'support_star_desc',
      icon: '<path d="M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1z"/>'
    },
    {
      href: 'https://play.google.com/store/apps/details?id=com.accbot.dca',
      title: 'support_review_title',
      desc: 'support_review_desc',
      icon: '<path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>'
    },
    {
      href: 'https://github.com/crynners/AccBot/issues',
      title: 'support_contribute_title',
      desc: 'support_contribute_desc',
      icon: '<circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>'
    }
  ];
</script>

<div class="support">
  <div class="give" class:solo={!LIGHTNING_ADDRESS}>
    {#if LIGHTNING_ADDRESS}
      <!-- Lightning -->
      <div class="card lightning reveal">
        <div class="bolt" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M13 2L4 14h7l-1 8 9-12h-7z" /></svg>
        </div>
        <h3>{t('support_ln_title')}</h3>
        <p>{t('support_ln_desc')}</p>
        <div class="wallet ln-box">
          <svg class="ln-qr" viewBox="0 0 {lnQr.size} {lnQr.size}" role="img" aria-label="{t('support_qr_alt')} (Lightning)" shape-rendering="crispEdges">
            <rect width={lnQr.size} height={lnQr.size} fill="#fff" /><path d={lnQr.d} fill="#0E0E1A" />
          </svg>
          <div class="addr">
            <span class="coin-name">Lightning</span>
            <code>{LIGHTNING_ADDRESS}</code>
          <button type="button" class="copy" class:copied={copied === 'ln'} onclick={() => copy(LIGHTNING_ADDRESS, 'ln')}>
            {#if copied === 'ln'}
              <svg viewBox="0 0 24 24" aria-hidden="true"><polyline points="20 6 9 17 4 12" /></svg>
              {t('support_copied')}
            {:else}
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="9" width="13" height="13" rx="2" /><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" /></svg>
              {t('support_copy')}
            {/if}
          </button>
          </div>
        </div>
      </div>
    {/if}

    <!-- Crypto -->
    <div class="card crypto reveal">
      <h3>{t('support_crypto_title')}</h3>
      <p>{t('support_crypto_desc')}</p>
      <div class="coins" role="tablist" aria-label={t('support_crypto_title')}>
        {#each COINS as c (c.id)}
          <button type="button" role="tab" aria-selected={c.id === coinId} class:on={c.id === coinId} title={c.name} onclick={() => pick(c.id)}>
            {c.ticker}
          </button>
        {/each}
      </div>
      <div class="wallet" role="tabpanel">
        <img src="/img/donate-{coin.id}.svg" width="132" height="132" alt="{t('support_qr_alt')} ({coin.name})" />
        <div class="addr">
          <span class="coin-name">{coin.name}</span>
          <code>{coin.address}</code>
          <button type="button" class="copy" class:copied={copied === coin.id} onclick={() => copy(coin.address, coin.id)}>
            {#if copied === coin.id}
              <svg viewBox="0 0 24 24" aria-hidden="true"><polyline points="20 6 9 17 4 12" /></svg>
              {t('support_copied')}
            {:else}
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="9" width="13" height="13" rx="2" /><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" /></svg>
              {t('support_copy')}
            {/if}
          </button>
        </div>
      </div>
    </div>
  </div>

  <h3 class="help-title reveal">{t('support_help_title')}</h3>
  <ul class="help reveal">
    {#each HELP as h (h.href)}
      <li>
        <a href={h.href} target="_blank" rel="noopener">
          <span class="ico" aria-hidden="true"><svg viewBox="0 0 24 24">{@html h.icon}</svg></span>
          <span class="txt"><strong>{t(h.title)}</strong><span>{t(h.desc)}</span></span>
        </a>
      </li>
    {/each}
    <li class="follow">
      <span class="ico" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 01-3.46 0" /></svg></span>
      <span class="txt">
        <strong>{t('support_follow_title')}</strong>
        <span>{t('support_follow_desc')}</span>
        <span class="social">
          <a href="https://t.me/accbotsupport" target="_blank" rel="noopener">Telegram</a>
          <a href="https://www.facebook.com/profile.php?id=61580714271760" target="_blank" rel="noopener">Facebook</a>
        </span>
      </span>
    </li>
  </ul>
</div>

<style>
  .give {
    display: grid;
    grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
    gap: 24px;
  }

  .card {
    background: var(--surface);
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-radius: 16px;
    padding: 32px;
  }

  .card h3 {
    font-size: 1.375rem;
    margin-bottom: 8px;
  }

  .card p {
    color: var(--text-muted);
    font-size: 0.9375rem;
    line-height: 1.6;
  }

  .give.solo {
    grid-template-columns: 1fr;
  }

  /* Lightning card */
  .lightning {
    display: flex;
    flex-direction: column;
    background:
      radial-gradient(120% 80% at 100% 0%, rgba(247, 147, 26, 0.12), transparent 60%),
      var(--surface);
  }

  .bolt {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    background: rgba(247, 147, 26, 0.14);
    display: grid;
    place-items: center;
    margin-bottom: 18px;
  }

  .bolt svg {
    width: 26px;
    height: 26px;
    fill: #f7931a;
  }

  .ln-box {
    margin-top: auto;
  }

  .lightning p {
    margin-bottom: 20px;
  }

  .ln-qr {
    flex: 0 0 auto;
    width: 132px;
    height: 132px;
    border-radius: 8px;
  }


  /* Crypto card */
  .coins {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin: 20px 0 18px;
  }

  .coins button {
    border: 1px solid rgba(255, 255, 255, 0.1);
    background: transparent;
    color: var(--text-muted);
    font: inherit;
    font-size: 0.8125rem;
    font-weight: 600;
    padding: 6px 12px;
    border-radius: 999px;
    cursor: pointer;
    transition: background 0.2s, color 0.2s, border-color 0.2s;
  }

  .coins button:hover {
    color: var(--text);
    border-color: rgba(255, 255, 255, 0.25);
  }

  .coins button.on {
    background: var(--accent);
    border-color: var(--accent);
    color: var(--bg);
  }

  .wallet {
    display: flex;
    gap: 22px;
    align-items: center;
    padding: 18px;
    background: var(--bg);
    border-radius: 12px;
  }

  .wallet img {
    flex: 0 0 auto;
    border-radius: 8px;
  }

  .addr {
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .coin-name {
    font-size: 0.8125rem;
    color: var(--text-muted);
  }

  .addr code {
    font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
    font-size: 0.8125rem;
    line-height: 1.5;
    word-break: break-all;
    user-select: all;
  }

  .copy {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border: 1px solid rgba(78, 204, 163, 0.4);
    background: transparent;
    color: var(--accent);
    font: inherit;
    font-size: 0.8125rem;
    font-weight: 600;
    padding: 7px 14px;
    border-radius: 8px;
    cursor: pointer;
  }

  .copy:hover,
  .copy.copied {
    background: rgba(78, 204, 163, 0.12);
  }

  .copy svg {
    width: 15px;
    height: 15px;
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  /* Other ways to help */
  .help-title {
    margin: 48px 0 16px;
    font-size: 1.125rem;
  }

  .help {
    list-style: none;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
  }

  .help li {
    border-radius: 12px;
    border: 1px solid rgba(255, 255, 255, 0.06);
    background: rgba(255, 255, 255, 0.02);
  }

  .help li > a,
  .follow {
    display: flex;
    gap: 14px;
    padding: 18px;
    color: inherit;
    height: 100%;
    border-radius: 12px;
    transition: background 0.2s;
  }

  .help li > a:hover {
    background: rgba(255, 255, 255, 0.04);
    color: inherit;
  }

  .ico {
    flex: 0 0 auto;
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: rgba(78, 204, 163, 0.12);
    color: var(--accent);
    display: grid;
    place-items: center;
  }

  .ico svg {
    width: 18px;
    height: 18px;
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  .txt {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }

  .txt strong {
    font-size: 0.9375rem;
    font-weight: 600;
  }

  .txt > span {
    font-size: 0.8125rem;
    color: var(--text-muted);
    line-height: 1.5;
  }

  .social {
    display: flex;
    gap: 14px;
    margin-top: 4px;
  }

  .social a {
    font-weight: 600;
    font-size: 0.8125rem;
  }

  @media (max-width: 960px) {
    .give {
      grid-template-columns: 1fr;
    }
    .help {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 560px) {
    .card {
      padding: 24px 20px;
    }
    .wallet {
      flex-direction: column;
      align-items: flex-start;
    }
    .help {
      grid-template-columns: 1fr;
    }
  }

</style>
