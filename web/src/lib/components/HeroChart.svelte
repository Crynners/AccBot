<script>
  // Decorative backdrop for the hero: a volatile price line with regular purchases on it,
  // and the coin stack those purchases build (it climbs faster when the price dips).
  const W = 1000;
  const H = 420;
  const STEPS = 120;
  const BUYS = 16;

  const price = (u) =>
    0.55 + 0.22 * Math.sin(u * 5.2 + 0.4) + 0.1 * Math.sin(u * 13.7 + 1.3) + 0.05 * Math.sin(u * 31 + 2) + 0.18 * u;

  const pts = Array.from({ length: STEPS + 1 }, (_, i) => {
    const u = i / STEPS;
    return [u * W, price(u)];
  });
  const pMin = Math.min(...pts.map((p) => p[1]));
  const pMax = Math.max(...pts.map((p) => p[1]));
  const py = (v) => 40 + (1 - (v - pMin) / (pMax - pMin)) * (H * 0.55);
  const pricePath = 'M' + pts.map(([x, v]) => `${x.toFixed(1)},${py(v).toFixed(1)}`).join('L');

  const buys = Array.from({ length: BUYS }, (_, i) => {
    const u = (i + 0.5) / BUYS;
    return { x: u * W, y: py(price(u)), coins: 1 / price(u) };
  });

  let total = 0;
  const stackRaw = [[0, 0]];
  for (const b of buys) {
    total += b.coins;
    stackRaw.push([b.x, total]);
  }
  stackRaw.push([W, total]);
  const sy = (v) => H - 20 - (v / total) * (H * 0.62);
  const stackPts = stackRaw.map(([x, v]) => [x, sy(v)]);
  const stackPath = 'M' + stackPts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join('L');
  const stackArea = `${stackPath}L${W},${H}L0,${H}Z`;
</script>

<svg class="hero-chart" viewBox="0 0 {W} {H}" preserveAspectRatio="none" aria-hidden="true">
  <defs>
    <linearGradient id="hc-area" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#4ECCA3" stop-opacity="0.22" />
      <stop offset="100%" stop-color="#4ECCA3" stop-opacity="0" />
    </linearGradient>
  </defs>
  <path class="area" d={stackArea} fill="url(#hc-area)" />
  <path class="price" d={pricePath} pathLength="1" />
  <path class="stack" d={stackPath} pathLength="1" />
  {#each buys as b, i (i)}
    <line class="drop" x1={b.x} y1={b.y} x2={b.x} y2={stackPts[i + 1][1]} style="--i: {i}" />
    <circle class="buy" cx={b.x} cy={b.y} r="4.5" style="--i: {i}" />
  {/each}
</svg>

<style>
  .hero-chart {
    position: absolute;
    left: 50%;
    top: 50%;
    width: min(1100px, 100vw);
    height: 420px;
    transform: translate(-50%, -46%);
    z-index: 0;
    pointer-events: none;
    overflow: visible;
    -webkit-mask-image: linear-gradient(90deg, transparent, #000 14%, #000 86%, transparent);
    mask-image: linear-gradient(90deg, transparent, #000 14%, #000 86%, transparent);
  }

  .price {
    fill: none;
    stroke: rgba(160, 160, 200, 0.35);
    stroke-width: 1.5;
    vector-effect: non-scaling-stroke;
    stroke-dasharray: 1;
    animation: draw 2.4s cubic-bezier(0.4, 0, 0.2, 1) both;
  }

  .stack {
    fill: none;
    stroke: var(--accent);
    stroke-width: 2.5;
    vector-effect: non-scaling-stroke;
    stroke-dasharray: 1;
    animation: draw 2.4s cubic-bezier(0.4, 0, 0.2, 1) 0.5s both;
  }

  .area {
    animation: fade 1.2s ease 1.8s both;
  }

  .buy {
    fill: var(--bg);
    stroke: var(--accent);
    stroke-width: 2;
    vector-effect: non-scaling-stroke;
    transform-box: fill-box;
    transform-origin: center;
    animation: pop 0.4s cubic-bezier(0.2, 0.9, 0.3, 1.4) calc(0.15s + var(--i) * 0.14s) both;
  }

  .drop {
    stroke: rgba(78, 204, 163, 0.18);
    stroke-width: 1;
    stroke-dasharray: 2 4;
    vector-effect: non-scaling-stroke;
    animation: fade 0.6s ease calc(0.5s + var(--i) * 0.14s) both;
  }

  @keyframes draw {
    from { stroke-dashoffset: 1; }
    to { stroke-dashoffset: 0; }
  }

  @keyframes pop {
    from { transform: scale(0); }
  }

  @keyframes fade {
    from { opacity: 0; }
  }

  @media (max-width: 560px) {
    .hero-chart {
      height: 300px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .price, .stack, .area, .buy, .drop {
      animation: none;
    }
  }
</style>
