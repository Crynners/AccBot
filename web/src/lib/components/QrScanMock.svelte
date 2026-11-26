<script>
  import { inview } from '$lib/inview.js';

  let { t } = $props();

  // Demo QR code with a made-up key; generated once with the qrcode library.
  const QR = 'M0 0h1v1h-1zM1 0h1v1h-1zM2 0h1v1h-1zM3 0h1v1h-1zM4 0h1v1h-1zM5 0h1v1h-1zM6 0h1v1h-1zM11 0h1v1h-1zM12 0h1v1h-1zM13 0h1v1h-1zM16 0h1v1h-1zM19 0h1v1h-1zM20 0h1v1h-1zM22 0h1v1h-1zM23 0h1v1h-1zM24 0h1v1h-1zM25 0h1v1h-1zM26 0h1v1h-1zM27 0h1v1h-1zM28 0h1v1h-1zM0 1h1v1h-1zM6 1h1v1h-1zM12 1h1v1h-1zM13 1h1v1h-1zM14 1h1v1h-1zM15 1h1v1h-1zM17 1h1v1h-1zM20 1h1v1h-1zM22 1h1v1h-1zM28 1h1v1h-1zM0 2h1v1h-1zM2 2h1v1h-1zM3 2h1v1h-1zM4 2h1v1h-1zM6 2h1v1h-1zM10 2h1v1h-1zM12 2h1v1h-1zM13 2h1v1h-1zM16 2h1v1h-1zM17 2h1v1h-1zM20 2h1v1h-1zM22 2h1v1h-1zM24 2h1v1h-1zM25 2h1v1h-1zM26 2h1v1h-1zM28 2h1v1h-1zM0 3h1v1h-1zM2 3h1v1h-1zM3 3h1v1h-1zM4 3h1v1h-1zM6 3h1v1h-1zM10 3h1v1h-1zM11 3h1v1h-1zM13 3h1v1h-1zM14 3h1v1h-1zM15 3h1v1h-1zM18 3h1v1h-1zM19 3h1v1h-1zM22 3h1v1h-1zM24 3h1v1h-1zM25 3h1v1h-1zM26 3h1v1h-1zM28 3h1v1h-1zM0 4h1v1h-1zM2 4h1v1h-1zM3 4h1v1h-1zM4 4h1v1h-1zM6 4h1v1h-1zM9 4h1v1h-1zM11 4h1v1h-1zM13 4h1v1h-1zM14 4h1v1h-1zM16 4h1v1h-1zM18 4h1v1h-1zM22 4h1v1h-1zM24 4h1v1h-1zM25 4h1v1h-1zM26 4h1v1h-1zM28 4h1v1h-1zM0 5h1v1h-1zM6 5h1v1h-1zM8 5h1v1h-1zM9 5h1v1h-1zM10 5h1v1h-1zM11 5h1v1h-1zM13 5h1v1h-1zM14 5h1v1h-1zM17 5h1v1h-1zM19 5h1v1h-1zM20 5h1v1h-1zM22 5h1v1h-1zM28 5h1v1h-1zM0 6h1v1h-1zM1 6h1v1h-1zM2 6h1v1h-1zM3 6h1v1h-1zM4 6h1v1h-1zM5 6h1v1h-1zM6 6h1v1h-1zM8 6h1v1h-1zM10 6h1v1h-1zM12 6h1v1h-1zM14 6h1v1h-1zM16 6h1v1h-1zM18 6h1v1h-1zM20 6h1v1h-1zM22 6h1v1h-1zM23 6h1v1h-1zM24 6h1v1h-1zM25 6h1v1h-1zM26 6h1v1h-1zM27 6h1v1h-1zM28 6h1v1h-1zM10 7h1v1h-1zM12 7h1v1h-1zM14 7h1v1h-1zM17 7h1v1h-1zM19 7h1v1h-1zM0 8h1v1h-1zM3 8h1v1h-1zM5 8h1v1h-1zM6 8h1v1h-1zM8 8h1v1h-1zM11 8h1v1h-1zM15 8h1v1h-1zM18 8h1v1h-1zM20 8h1v1h-1zM21 8h1v1h-1zM23 8h1v1h-1zM0 9h1v1h-1zM1 9h1v1h-1zM3 9h1v1h-1zM5 9h1v1h-1zM8 9h1v1h-1zM10 9h1v1h-1zM11 9h1v1h-1zM12 9h1v1h-1zM14 9h1v1h-1zM15 9h1v1h-1zM18 9h1v1h-1zM22 9h1v1h-1zM25 9h1v1h-1zM27 9h1v1h-1zM28 9h1v1h-1zM3 10h1v1h-1zM4 10h1v1h-1zM5 10h1v1h-1zM6 10h1v1h-1zM8 10h1v1h-1zM9 10h1v1h-1zM16 10h1v1h-1zM18 10h1v1h-1zM19 10h1v1h-1zM22 10h1v1h-1zM23 10h1v1h-1zM24 10h1v1h-1zM25 10h1v1h-1zM27 10h1v1h-1zM0 11h1v1h-1zM1 11h1v1h-1zM2 11h1v1h-1zM5 11h1v1h-1zM8 11h1v1h-1zM9 11h1v1h-1zM10 11h1v1h-1zM12 11h1v1h-1zM14 11h1v1h-1zM15 11h1v1h-1zM20 11h1v1h-1zM23 11h1v1h-1zM26 11h1v1h-1zM28 11h1v1h-1zM0 12h1v1h-1zM1 12h1v1h-1zM2 12h1v1h-1zM3 12h1v1h-1zM4 12h1v1h-1zM6 12h1v1h-1zM7 12h1v1h-1zM8 12h1v1h-1zM9 12h1v1h-1zM12 12h1v1h-1zM16 12h1v1h-1zM17 12h1v1h-1zM20 12h1v1h-1zM22 12h1v1h-1zM28 12h1v1h-1zM1 13h1v1h-1zM3 13h1v1h-1zM7 13h1v1h-1zM15 13h1v1h-1zM17 13h1v1h-1zM19 13h1v1h-1zM20 13h1v1h-1zM25 13h1v1h-1zM26 13h1v1h-1zM27 13h1v1h-1zM2 14h1v1h-1zM3 14h1v1h-1zM4 14h1v1h-1zM6 14h1v1h-1zM7 14h1v1h-1zM8 14h1v1h-1zM10 14h1v1h-1zM15 14h1v1h-1zM16 14h1v1h-1zM17 14h1v1h-1zM20 14h1v1h-1zM25 14h1v1h-1zM26 14h1v1h-1zM27 14h1v1h-1zM28 14h1v1h-1zM1 15h1v1h-1zM2 15h1v1h-1zM3 15h1v1h-1zM5 15h1v1h-1zM8 15h1v1h-1zM10 15h1v1h-1zM12 15h1v1h-1zM13 15h1v1h-1zM14 15h1v1h-1zM16 15h1v1h-1zM17 15h1v1h-1zM19 15h1v1h-1zM21 15h1v1h-1zM22 15h1v1h-1zM23 15h1v1h-1zM24 15h1v1h-1zM28 15h1v1h-1zM0 16h1v1h-1zM1 16h1v1h-1zM2 16h1v1h-1zM4 16h1v1h-1zM6 16h1v1h-1zM13 16h1v1h-1zM14 16h1v1h-1zM15 16h1v1h-1zM16 16h1v1h-1zM17 16h1v1h-1zM18 16h1v1h-1zM3 17h1v1h-1zM4 17h1v1h-1zM8 17h1v1h-1zM11 17h1v1h-1zM12 17h1v1h-1zM15 17h1v1h-1zM18 17h1v1h-1zM20 17h1v1h-1zM22 17h1v1h-1zM23 17h1v1h-1zM26 17h1v1h-1zM27 17h1v1h-1zM28 17h1v1h-1zM0 18h1v1h-1zM3 18h1v1h-1zM4 18h1v1h-1zM5 18h1v1h-1zM6 18h1v1h-1zM7 18h1v1h-1zM9 18h1v1h-1zM11 18h1v1h-1zM13 18h1v1h-1zM21 18h1v1h-1zM22 18h1v1h-1zM23 18h1v1h-1zM24 18h1v1h-1zM26 18h1v1h-1zM27 18h1v1h-1zM28 18h1v1h-1zM2 19h1v1h-1zM4 19h1v1h-1zM5 19h1v1h-1zM7 19h1v1h-1zM9 19h1v1h-1zM10 19h1v1h-1zM12 19h1v1h-1zM13 19h1v1h-1zM15 19h1v1h-1zM16 19h1v1h-1zM17 19h1v1h-1zM23 19h1v1h-1zM24 19h1v1h-1zM28 19h1v1h-1zM0 20h1v1h-1zM3 20h1v1h-1zM4 20h1v1h-1zM5 20h1v1h-1zM6 20h1v1h-1zM7 20h1v1h-1zM8 20h1v1h-1zM10 20h1v1h-1zM11 20h1v1h-1zM14 20h1v1h-1zM16 20h1v1h-1zM20 20h1v1h-1zM21 20h1v1h-1zM22 20h1v1h-1zM23 20h1v1h-1zM24 20h1v1h-1zM25 20h1v1h-1zM26 20h1v1h-1zM28 20h1v1h-1zM8 21h1v1h-1zM9 21h1v1h-1zM10 21h1v1h-1zM11 21h1v1h-1zM12 21h1v1h-1zM13 21h1v1h-1zM15 21h1v1h-1zM16 21h1v1h-1zM17 21h1v1h-1zM19 21h1v1h-1zM20 21h1v1h-1zM24 21h1v1h-1zM25 21h1v1h-1zM26 21h1v1h-1zM27 21h1v1h-1zM28 21h1v1h-1zM0 22h1v1h-1zM1 22h1v1h-1zM2 22h1v1h-1zM3 22h1v1h-1zM4 22h1v1h-1zM5 22h1v1h-1zM6 22h1v1h-1zM9 22h1v1h-1zM12 22h1v1h-1zM13 22h1v1h-1zM14 22h1v1h-1zM15 22h1v1h-1zM17 22h1v1h-1zM20 22h1v1h-1zM22 22h1v1h-1zM24 22h1v1h-1zM25 22h1v1h-1zM27 22h1v1h-1zM0 23h1v1h-1zM6 23h1v1h-1zM8 23h1v1h-1zM10 23h1v1h-1zM11 23h1v1h-1zM13 23h1v1h-1zM16 23h1v1h-1zM20 23h1v1h-1zM24 23h1v1h-1zM26 23h1v1h-1zM27 23h1v1h-1zM28 23h1v1h-1zM0 24h1v1h-1zM2 24h1v1h-1zM3 24h1v1h-1zM4 24h1v1h-1zM6 24h1v1h-1zM9 24h1v1h-1zM11 24h1v1h-1zM12 24h1v1h-1zM14 24h1v1h-1zM15 24h1v1h-1zM16 24h1v1h-1zM17 24h1v1h-1zM18 24h1v1h-1zM20 24h1v1h-1zM21 24h1v1h-1zM22 24h1v1h-1zM23 24h1v1h-1zM24 24h1v1h-1zM27 24h1v1h-1zM0 25h1v1h-1zM2 25h1v1h-1zM3 25h1v1h-1zM4 25h1v1h-1zM6 25h1v1h-1zM8 25h1v1h-1zM9 25h1v1h-1zM10 25h1v1h-1zM11 25h1v1h-1zM12 25h1v1h-1zM14 25h1v1h-1zM15 25h1v1h-1zM16 25h1v1h-1zM17 25h1v1h-1zM18 25h1v1h-1zM19 25h1v1h-1zM20 25h1v1h-1zM22 25h1v1h-1zM23 25h1v1h-1zM24 25h1v1h-1zM28 25h1v1h-1zM0 26h1v1h-1zM2 26h1v1h-1zM3 26h1v1h-1zM4 26h1v1h-1zM6 26h1v1h-1zM12 26h1v1h-1zM13 26h1v1h-1zM16 26h1v1h-1zM24 26h1v1h-1zM25 26h1v1h-1zM28 26h1v1h-1zM0 27h1v1h-1zM6 27h1v1h-1zM11 27h1v1h-1zM12 27h1v1h-1zM13 27h1v1h-1zM14 27h1v1h-1zM15 27h1v1h-1zM17 27h1v1h-1zM20 27h1v1h-1zM23 27h1v1h-1zM27 27h1v1h-1zM0 28h1v1h-1zM1 28h1v1h-1zM2 28h1v1h-1zM3 28h1v1h-1zM4 28h1v1h-1zM5 28h1v1h-1zM6 28h1v1h-1zM8 28h1v1h-1zM13 28h1v1h-1zM14 28h1v1h-1zM16 28h1v1h-1zM17 28h1v1h-1zM18 28h1v1h-1zM19 28h1v1h-1zM20 28h1v1h-1zM22 28h1v1h-1zM24 28h1v1h-1zM27 28h1v1h-1z';

  let armed = $state(false);
  let done = $state(false);
  function arm(node) {
    armed = true;
    return inview(node, (reduced) => {
      if (reduced) done = true;
      else setTimeout(() => (done = true), 2600);
    });
  }
</script>

<div class="qr" class:armed class:done use:arm>
  <div class="viewfinder">
    <span class="corner tl"></span><span class="corner tr"></span><span class="corner bl"></span><span class="corner br"></span>
    <div class="code">
      <svg viewBox="-2 -2 33 33" aria-hidden="true"><rect x="-2" y="-2" width="33" height="33" fill="#fff" /><path d={QR} fill="#0E0E1A" shape-rendering="crispEdges" /></svg>
    </div>
    <span class="beam"></span>
  </div>
  <p class="hint">{t('qr_hint')}</p>
  <div class="result" aria-live="polite">
    <svg viewBox="0 0 24 24" aria-hidden="true"><polyline points="20 6 9 17 4 12" /></svg>
    <span>{t('qr_done')}</span>
    <code>7f3a…4f60</code>
  </div>
</div>

<style>
  .qr {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 14px;
  }

  .viewfinder {
    position: relative;
    width: 200px;
    height: 200px;
    border-radius: 18px;
    background: radial-gradient(circle at 50% 40%, #23233a, #121222);
    display: grid;
    place-items: center;
    overflow: hidden;
  }

  .code {
    width: 128px;
    height: 128px;
    border-radius: 6px;
    overflow: hidden;
    transition: transform 0.5s ease, filter 0.5s ease;
  }

  .code svg {
    display: block;
    width: 100%;
    height: 100%;
  }

  .corner {
    position: absolute;
    width: 28px;
    height: 28px;
    border: 3px solid var(--text);
    transition: border-color 0.3s, inset 0.4s ease;
  }

  .tl { top: 16px; left: 16px; border-right: 0; border-bottom: 0; border-top-left-radius: 10px; }
  .tr { top: 16px; right: 16px; border-left: 0; border-bottom: 0; border-top-right-radius: 10px; }
  .bl { bottom: 16px; left: 16px; border-right: 0; border-top: 0; border-bottom-left-radius: 10px; }
  .br { bottom: 16px; right: 16px; border-left: 0; border-top: 0; border-bottom-right-radius: 10px; }

  .beam {
    position: absolute;
    left: 22px;
    right: 22px;
    top: 30px;
    height: 2px;
    border-radius: 1px;
    background: var(--accent);
    box-shadow: 0 0 14px 3px rgba(78, 204, 163, 0.55);
    opacity: 0;
  }

  .armed:not(.done) .beam {
    opacity: 1;
    animation: scan 1.3s ease-in-out 2 alternate;
  }

  @keyframes scan {
    from { top: 30px; }
    to { top: 168px; }
  }

  .done .corner {
    border-color: var(--accent);
  }

  .done .tl { top: 30px; left: 30px; }
  .done .tr { top: 30px; right: 30px; }
  .done .bl { bottom: 30px; left: 30px; }
  .done .br { bottom: 30px; right: 30px; }

  .hint {
    font-size: 0.8125rem;
    color: var(--text-muted);
    text-align: center;
  }

  .result {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    border-radius: 999px;
    background: rgba(78, 204, 163, 0.12);
    color: var(--accent);
    font-size: 0.8125rem;
    font-weight: 600;
  }

  .armed:not(.done) .result {
    visibility: hidden;
  }

  .done .result {
    animation: pop 0.35s cubic-bezier(0.2, 0.9, 0.3, 1.3);
  }

  @keyframes pop {
    from { transform: scale(0.85); opacity: 0; }
  }

  .result svg {
    width: 16px;
    height: 16px;
    fill: none;
    stroke: currentColor;
    stroke-width: 3;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  .result code {
    font-family: inherit;
    font-weight: 500;
    color: var(--text-muted);
  }

  @media (prefers-reduced-motion: reduce) {
    .beam, .done .result { animation: none !important; }
    .corner, .code { transition: none; }
  }
</style>
