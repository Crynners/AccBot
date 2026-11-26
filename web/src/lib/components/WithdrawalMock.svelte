<script>
  import { inview } from '$lib/inview.js';
  import { fmtNum, tpl } from '$lib/format.js';

  let { t, lang } = $props();

  const THRESHOLD = 0.01;
  const BALANCE = 0.01034;
  const btc = (v) => `${fmtNum(lang, v, v === THRESHOLD ? 2 : 5)} BTC`;

  // The static page shows the final state; once hydrated, the mock resets ("armed") and plays when scrolled into view.
  let armed = $state(false);
  let played = $state(false);
  let instant = $state(false);
  function arm(node) {
    armed = true;
    return inview(node, (reduced) => {
      instant = reduced;
      played = true;
    });
  }
</script>

<div class="wd" class:armed class:played class:instant use:arm>
  <div class="card">
    <div class="row">
      <span class="label">{tpl(t('wd_balance_tpl'), 'Coinmate')}</span>
      <span class="balance">{btc(BALANCE)}</span>
    </div>
    <div class="track"><div class="fill"></div><div class="mark"></div></div>
    <div class="row small">
      <span>{t('wd_threshold')}</span>
      <span>{btc(THRESHOLD)}</span>
    </div>
  </div>

  <div class="notif" role="img" aria-label="{t('wd_notif_title')}: {tpl(t('wd_notif_body_tpl'), btc(BALANCE), 'Coinmate')}">
    <img src="/favicon.svg" alt="" width="36" height="36" />
    <div class="notif-text">
      <div class="notif-head"><strong>AccBot</strong><span>{t('wd_now')}</span></div>
      <div class="notif-title">{t('wd_notif_title')}</div>
      <div class="notif-body">{tpl(t('wd_notif_body_tpl'), btc(BALANCE), 'Coinmate')}</div>
    </div>
  </div>
</div>

<style>
  .wd {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .card {
    background: var(--surface-alt);
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-radius: 10px;
    padding: 16px;
  }

  .row {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: 12px;
  }

  .label {
    font-size: 0.8125rem;
    color: var(--text-muted);
  }

  .balance {
    font-size: 1.25rem;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .small {
    margin-top: 8px;
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .track {
    position: relative;
    height: 8px;
    margin-top: 14px;
    border-radius: 4px;
    background: rgba(255, 255, 255, 0.08);
  }

  .fill {
    position: absolute;
    inset: 0 auto 0 0;
    width: 100%;
    border-radius: 4px;
    background: var(--accent);
    transform-origin: left;
  }

  .armed .fill {
    transform: scaleX(0.15);
  }

  .mark {
    position: absolute;
    top: -4px;
    bottom: -4px;
    right: 3%;
    width: 2px;
    border-radius: 1px;
    background: var(--text);
  }

  .armed.played .fill {
    transform: scaleX(1);
    transition: transform 1.6s cubic-bezier(0.3, 0.7, 0.2, 1);
  }

  .notif {
    display: flex;
    gap: 12px;
    align-items: flex-start;
    padding: 14px;
    border-radius: 14px;
    background: #24243c;
    border: 1px solid rgba(255, 255, 255, 0.08);
    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.35);
  }

  .armed .notif {
    opacity: 0;
    transform: translateY(14px) scale(0.98);
  }

  .armed.played .notif {
    opacity: 1;
    transform: none;
    transition: opacity 0.45s ease 1.5s, transform 0.55s cubic-bezier(0.2, 0.9, 0.3, 1.2) 1.5s;
  }

  .instant .fill,
  .instant .notif {
    transition: none;
  }

  .notif img {
    flex: 0 0 auto;
    border-radius: 9px;
    background: var(--bg);
  }

  .notif-text {
    min-width: 0;
  }

  .notif-head {
    display: flex;
    gap: 8px;
    font-size: 0.75rem;
    color: var(--text-muted);
  }

  .notif-head strong {
    color: var(--text);
    font-weight: 600;
  }

  .notif-title {
    margin-top: 2px;
    font-size: 0.875rem;
    font-weight: 600;
  }

  .notif-body {
    font-size: 0.8125rem;
    color: var(--text-muted);
    line-height: 1.45;
  }

  @media (prefers-reduced-motion: reduce) {
    .fill,
    .notif {
      transition: none !important;
    }
  }
</style>
