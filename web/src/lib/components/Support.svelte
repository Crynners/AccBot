<script>
  import { BUY_ME_A_COFFEE } from '$lib/i18n.js';

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
  let copied = $state(false);
  let timer;

  async function copy() {
    try {
      await navigator.clipboard.writeText(coin.address);
      copied = true;
      clearTimeout(timer);
      timer = setTimeout(() => (copied = false), 2000);
    } catch (e) {
      /* clipboard blocked: the address stays selectable */
    }
  }

  function pick(id) {
    coinId = id;
    copied = false;
  }

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
  <div class="give">
    <!-- Buy Me a Coffee -->
    <div class="card coffee reveal">
      <div class="cup" aria-hidden="true">
        <svg viewBox="0 0 64 64">
          <path class="steam" d="M24 8c-3 4 3 6 0 10M32 6c-3 4 3 6 0 10M40 8c-3 4 3 6 0 10" />
          <path class="mug" d="M14 24h32v14a14 14 0 01-14 14h-4a14 14 0 01-14-14z" />
          <path class="handle" d="M46 28h4a6 6 0 010 12h-4" />
          <path class="saucer" d="M10 56h40" />
        </svg>
      </div>
      <h3>{t('support_coffee_title')}</h3>
      <p>{t('support_coffee_desc')}</p>
      <a class="bmc" href={BUY_ME_A_COFFEE} target="_blank" rel="noopener">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 8h12v5a5 5 0 01-5 5h-2a5 5 0 01-5-5z" /><path d="M17 9h1.5a2.5 2.5 0 010 5H17" /></svg>
        <span>{t('support_coffee_btn')}</span>
      </a>
      <span class="via">buymeacoffee.com</span>
    </div>

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
          <button type="button" class="copy" class:copied onclick={copy}>
            {#if copied}
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

  /* Coffee card: warm, a little playful */
  .coffee {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    background:
      radial-gradient(120% 80% at 100% 0%, rgba(255, 221, 0, 0.1), transparent 60%),
      var(--surface);
  }

  .cup {
    width: 64px;
    height: 64px;
    margin-bottom: 18px;
  }

  .cup svg {
    width: 100%;
    height: 100%;
    fill: none;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  .mug {
    fill: #ffdd00;
    stroke: #ffdd00;
    stroke-width: 2;
  }

  .handle,
  .saucer {
    stroke: #ffdd00;
    stroke-width: 4;
  }

  .steam {
    stroke: rgba(255, 255, 255, 0.45);
    stroke-width: 2.5;
    stroke-dasharray: 14;
    animation: steam 3s ease-in-out infinite;
  }

  @keyframes steam {
    0%, 100% { stroke-dashoffset: 0; opacity: 0.25; }
    50% { stroke-dashoffset: 14; opacity: 0.8; }
  }

  .bmc {
    margin-top: auto;
    display: inline-flex;
    align-items: center;
    gap: 10px;
    padding: 13px 22px;
    border-radius: 12px;
    background: #ffdd00;
    color: #0e0e1a;
    font-weight: 700;
    font-size: 1rem;
    transition: transform 0.15s ease, box-shadow 0.2s ease;
  }

  .coffee p {
    margin-bottom: 24px;
  }

  .bmc:hover {
    color: #0e0e1a;
    transform: translateY(-2px);
    box-shadow: 0 10px 24px rgba(255, 221, 0, 0.22);
  }

  .bmc:focus-visible,
  .coins button:focus-visible,
  .copy:focus-visible,
  .help a:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 3px;
  }

  .bmc svg {
    width: 22px;
    height: 22px;
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  .via {
    margin-top: 10px;
    font-size: 0.75rem;
    color: var(--text-muted);
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

  @media (prefers-reduced-motion: reduce) {
    .steam {
      animation: none;
    }
    .bmc {
      transition: none;
    }
  }
</style>
