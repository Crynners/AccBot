<script>
  import { fmtMoney, fmtNum, tpl } from '$lib/format.js';

  let { t, lang } = $props();

  // Same default tiers as the app (DcaStrategy.kt).
  const ATH = [
    { max: 20, x: 0.5 },
    { max: 40, x: 1 },
    { max: 60, x: 1.5 },
    { max: 80, x: 2 },
    { max: 100, x: 3 }
  ];
  const FG = [
    { max: 19, x: 2.5 },
    { max: 39, x: 1.5 },
    { max: 59, x: 1 },
    { max: 79, x: 0.5 },
    { max: 100, x: 0.25 }
  ];
  const BASE = 50;

  let mode = $state('ath');
  let ath = $state(52);
  let fg = $state(22);

  const tiers = $derived(mode === 'ath' ? ATH : FG);
  const value = $derived(mode === 'ath' ? ath : fg);
  const active = $derived(tiers.findIndex((tier) => value <= tier.max));
  const mult = $derived(tiers[active].x);
  const fgZone = $derived(t(`fg_${FG.findIndex((tier) => fg <= tier.max)}`));
  const pct = (v) => new Intl.NumberFormat(lang, { style: 'percent' }).format(v);
  const multText =(x) => fmtNum(lang, x, x % 1 ? (x * 100) % 10 ? 2 : 1 : 0) + '×';
</script>

<div class="sim">
  <div class="tabs" role="tablist">
    <button type="button" role="tab" aria-selected={mode === 'ath'} class:on={mode === 'ath'} onclick={() => (mode = 'ath')}>
      {t('features_strategies_ath')}
    </button>
    <button type="button" role="tab" aria-selected={mode === 'fg'} class:on={mode === 'fg'} onclick={() => (mode = 'fg')}>
      {t('features_strategies_fg')}
    </button>
  </div>

  <div class="readout">
    <div class="mult" class:up={mult > 1} class:down={mult < 1}>{multText(mult)}</div>
    <div class="buy">
      {mult === 1 ? fmtMoney(lang, BASE) : tpl(t('sim_buy_tpl'), fmtMoney(lang, BASE * mult, 'EUR', (mult * BASE) % 1 ? 2 : 0), fmtMoney(lang, BASE))}
    </div>
  </div>

  <label class="slider">
    <span class="slider-head">
      <span>{mode === 'ath' ? t('sim_distance') : t('sim_index')}</span>
      {#if mode === 'ath'}
        <strong>{pct(-ath / 100)}</strong>
      {:else}
        <strong>{fgZone} <span class="num">({fg})</span></strong>
      {/if}
    </span>
    {#if mode === 'ath'}
      <input type="range" min="0" max="95" bind:value={ath} />
    {:else}
      <input type="range" min="0" max="100" bind:value={fg} class="fg" />
    {/if}
  </label>

  <div class="bands" aria-label={t('sim_tiers')}>
    {#each tiers as tier, i (tier.max)}
      <span class="band" class:on={i === active}>{multText(tier.x)}</span>
    {/each}
  </div>
</div>

<style>
  .sim {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .tabs {
    display: flex;
    gap: 4px;
    padding: 4px;
    background: var(--bg);
    border-radius: 10px;
    align-self: flex-start;
  }

  .tabs button {
    border: 0;
    background: none;
    color: var(--text-muted);
    font: inherit;
    font-size: 0.8125rem;
    font-weight: 600;
    padding: 7px 14px;
    border-radius: 7px;
    cursor: pointer;
    transition: background 0.2s, color 0.2s;
  }

  .tabs button.on {
    background: var(--surface-alt);
    color: var(--text);
  }

  .tabs button:focus-visible,
  input:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
  }

  .readout {
    display: flex;
    align-items: baseline;
    gap: 14px;
    flex-wrap: wrap;
  }

  .mult {
    font-size: 3rem;
    font-weight: 800;
    line-height: 1;
    letter-spacing: -0.03em;
    font-variant-numeric: tabular-nums;
    color: var(--text);
    min-width: 3.2ch;
    transition: color 0.25s;
  }

  .mult.up {
    color: var(--accent);
  }

  .mult.down {
    color: var(--warning);
  }

  .buy {
    color: var(--text-muted);
    font-size: 0.9375rem;
  }

  .slider {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .slider-head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    font-size: 0.8125rem;
    color: var(--text-muted);
  }

  .slider-head strong {
    color: var(--text);
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    text-align: right;
  }

  .num {
    color: var(--text-muted);
    font-weight: 500;
  }

  input[type='range'] {
    appearance: none;
    width: 100%;
    height: 6px;
    border-radius: 3px;
    background: linear-gradient(90deg, var(--warning), var(--text-muted) 30%, var(--accent));
    cursor: pointer;
  }

  input.fg {
    background: linear-gradient(90deg, var(--accent), var(--text-muted) 55%, var(--warning));
  }

  input[type='range']::-webkit-slider-thumb {
    appearance: none;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: var(--text);
    border: 4px solid var(--surface);
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.3);
  }

  input[type='range']::-moz-range-thumb {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: var(--text);
    border: 4px solid var(--surface);
  }

  .bands {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 4px;
  }

  .band {
    text-align: center;
    font-size: 0.75rem;
    font-weight: 600;
    padding: 6px 0;
    border-radius: 6px;
    background: var(--surface-alt);
    color: var(--text-muted);
    transition: background 0.2s, color 0.2s;
  }

  .band.on {
    background: rgba(78, 204, 163, 0.16);
    color: var(--accent);
  }

  @media (prefers-reduced-motion: reduce) {
    .mult,
    .band,
    .tabs button {
      transition: none;
    }
  }
</style>
