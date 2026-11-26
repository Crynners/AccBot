<script>
  import { tpl } from '$lib/format.js';

  /** @type {{ t: (k: string) => string, shots: { src: string, caption: string }[] }} */
  let { t, shots } = $props();

  let index = $state(0);
  const n = $derived(shots.length);

  // Signed distance of slide i from the active one, wrapping around (…, -2, -1, 0, 1, 2, …).
  const offset = (i) => {
    let d = (i - index) % n;
    if (d > n / 2) d -= n;
    if (d < -n / 2) d += n;
    return d;
  };

  const go = (i) => (index = (i + n) % n);
  const prev = () => go(index - 1);
  const next = () => go(index + 1);

  function onKey(e) {
    if (e.key === 'ArrowLeft') { e.preventDefault(); prev(); }
    if (e.key === 'ArrowRight') { e.preventDefault(); next(); }
  }

  // Swipe support: a horizontal drag of more than 40 px changes the slide.
  let startX = null;
  const down = (e) => (startX = e.clientX);
  function up(e) {
    if (startX === null) return;
    const dx = e.clientX - startX;
    startX = null;
    if (Math.abs(dx) > 40) (dx < 0 ? next : prev)();
  }
</script>

<div
  class="carousel"
  role="region"
  aria-roledescription="carousel"
  aria-label={t('screenshots_title')}
  tabindex="0"
  onkeydown={onKey}
  onpointerdown={down}
  onpointerup={up}
  onpointercancel={() => (startX = null)}
>
  <div class="stage">
    {#each shots as s, i (s.src)}
      {@const d = offset(i)}
      <button
        type="button"
        class="slide"
        class:active={d === 0}
        class:far={Math.abs(d) > 2}
        style="--d: {d}; --ad: {Math.abs(d)}"
        aria-hidden={d !== 0}
        tabindex="-1"
        aria-label={d === 0 ? s.caption : tpl(t('carousel_goto_tpl'), i + 1)}
        onclick={() => go(i)}
      >
        <span class="frame">
          <img src={s.src} alt="" width="540" height="1200" loading={Math.abs(d) <= 1 ? 'eager' : 'lazy'} draggable="false" />
        </span>
      </button>
    {/each}
  </div>

  <div class="controls">
    <button type="button" class="arrow" onclick={prev} aria-label={t('carousel_prev')}>
      <svg viewBox="0 0 24 24" aria-hidden="true"><polyline points="15 18 9 12 15 6" /></svg>
    </button>
    <p class="caption" aria-live="polite">{shots[index].caption}</p>
    <button type="button" class="arrow" onclick={next} aria-label={t('carousel_next')}>
      <svg viewBox="0 0 24 24" aria-hidden="true"><polyline points="9 18 15 12 9 6" /></svg>
    </button>
  </div>

  <div class="dots">
    {#each shots as s, i (s.src)}
      <button
        type="button"
        class="dot"
        class:on={i === index}
        aria-label={tpl(t('carousel_goto_tpl'), i + 1)}
        aria-current={i === index}
        onclick={() => go(i)}
      ></button>
    {/each}
  </div>
</div>

<style>
  .carousel {
    --w: 270px;
    position: relative;
    max-width: 1000px;
    margin: 0 auto;
    padding: 8px 0 0;
    outline: none;
    touch-action: pan-y;
    user-select: none;
  }

  .carousel:focus-visible .stage {
    outline: 2px solid var(--accent);
    outline-offset: 6px;
    border-radius: 12px;
  }

  .stage {
    position: relative;
    height: calc(var(--w) * 2.22 + 24px);
    perspective: 1400px;
    overflow: hidden;
  }

  .slide {
    position: absolute;
    top: 0;
    left: 50%;
    width: var(--w);
    margin-left: calc(var(--w) / -2);
    padding: 0;
    border: 0;
    background: none;
    cursor: pointer;
    transform: translateX(calc(var(--d) * var(--w) * 0.95)) translateZ(calc(var(--ad) * -140px))
      rotateY(calc(var(--d) * -20deg));
    opacity: calc(1 - var(--ad) * 0.32);
    z-index: calc(10 - var(--ad));
    transition: transform 0.55s cubic-bezier(0.22, 0.8, 0.24, 1), opacity 0.4s ease, filter 0.4s ease;
    filter: brightness(calc(1 - var(--ad) * 0.25));
  }

  .slide.active {
    cursor: default;
  }

  /* Slides three or more steps away sit hidden behind the others. */
  .slide.far {
    opacity: 0;
    pointer-events: none;
  }

  .frame {
    display: block;
    border: 2px solid rgba(255, 255, 255, 0.1);
    border-radius: 30px;
    background: var(--surface);
    padding: 7px;
    box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
  }

  .active .frame {
    border-color: rgba(78, 204, 163, 0.35);
    box-shadow: 0 30px 80px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(78, 204, 163, 0.08), 0 0 60px rgba(78, 204, 163, 0.12);
  }

  .frame img {
    width: 100%;
    height: auto;
    border-radius: 23px;
    pointer-events: none;
  }

  .controls {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    margin-top: 20px;
  }

  .caption {
    min-width: 0;
    width: 320px;
    text-align: center;
    color: var(--text);
    font-weight: 500;
    font-size: 0.9375rem;
    line-height: 1.4;
  }

  .arrow {
    flex: 0 0 auto;
    width: 44px;
    height: 44px;
    border-radius: 50%;
    border: 1px solid rgba(255, 255, 255, 0.14);
    background: var(--surface);
    color: var(--text);
    display: grid;
    place-items: center;
    cursor: pointer;
    transition: border-color 0.2s, color 0.2s;
  }

  .arrow:hover {
    border-color: var(--accent);
    color: var(--accent);
  }

  .arrow:focus-visible,
  .dot:focus-visible,
  .slide:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 3px;
  }

  .arrow svg {
    width: 20px;
    height: 20px;
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  .dots {
    display: flex;
    justify-content: center;
    gap: 8px;
    margin-top: 14px;
  }

  .dot {
    width: 8px;
    height: 8px;
    padding: 0;
    border-radius: 4px;
    border: 0;
    background: rgba(255, 255, 255, 0.2);
    cursor: pointer;
    transition: width 0.3s, background 0.3s;
  }

  .dot.on {
    width: 24px;
    background: var(--accent);
  }

  @media (max-width: 560px) {
    .carousel {
      --w: 210px;
    }
    .caption {
      width: auto;
      flex: 1;
      font-size: 0.875rem;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .slide,
    .dot {
      transition: none;
    }
  }
</style>
