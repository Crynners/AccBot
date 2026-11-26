
var STOSUJ_FEE = 0.015;
var ACCBOT_FEE = 0.006;
var latestPurchases = [];

function init() {
  var calcBtn = document.getElementById('compare-calc');
  if (!calcBtn) return;

  var startInput = document.getElementById('compare-start');
  var now = new Date();
  var ago = new Date(now.getFullYear() - 1, now.getMonth(), now.getDate());
  startInput.value = isoDate(ago);
  startInput.max = isoDate(now);
  startInput.min = '2014-01-01';

  var btns = document.querySelectorAll('.compare-interval-btn');
  for (var i = 0; i < btns.length; i++) {
    btns[i].addEventListener('click', function () {
      for (var j = 0; j < btns.length; j++) {
        var selected = btns[j] === this;
        btns[j].classList.toggle('active', selected);
        btns[j].setAttribute('aria-pressed', String(selected));
      }
    });
  }

  calcBtn.addEventListener('click', calculate);
  document.getElementById('compare-table').addEventListener('toggle', renderPurchaseTable);
  document.getElementById('compare-amount').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') calculate();
  });
  document.getElementById('compare-start').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') calculate();
  });
}

function isoDate(d) {
  var m = d.getMonth() + 1;
  var day = d.getDate();
  return d.getFullYear() + '-' + (m < 10 ? '0' : '') + m + '-' + (day < 10 ? '0' : '') + day;
}

function getInterval() {
  var el = document.querySelector('.compare-interval-btn.active');
  return el ? el.getAttribute('data-interval') : 'weekly';
}

function genBuyDates(start, end, interval) {
  var result = [];
  var d = new Date(start);
  while (d <= end) {
    result.push(new Date(d));
    if (interval === 'daily') d.setDate(d.getDate() + 1);
    else if (interval === 'weekly') d.setDate(d.getDate() + 7);
    else d.setMonth(d.getMonth() + 1);
  }
  return result;
}

function findPrice(prices, ts) {
  var lo = 0, hi = prices.length - 1;
  while (lo < hi) {
    var mid = (lo + hi) >> 1;
    if (prices[mid][0] < ts) lo = mid + 1;
    else hi = mid;
  }
  if (lo > 0 && Math.abs(prices[lo - 1][0] - ts) < Math.abs(prices[lo][0] - ts)) lo--;
  return prices[lo][1];
}

function findPriceIdx(prices, ts) {
  var lo = 0, hi = prices.length - 1;
  while (lo < hi) {
    var mid = (lo + hi) >> 1;
    if (prices[mid][0] < ts) lo = mid + 1;
    else hi = mid;
  }
  if (lo > 0 && Math.abs(prices[lo - 1][0] - ts) < Math.abs(prices[lo][0] - ts)) lo--;
  return lo;
}

function lang() {
  return document.documentElement.lang || 'en';
}

function fmtCZK(n) {
  return new Intl.NumberFormat(lang(), {
    style: 'currency', currency: 'CZK', maximumFractionDigits: 0
  }).format(n);
}

function fmtBTC(n) {
  return n.toFixed(8) + ' BTC';
}

function fmtSats(n) {
  return new Intl.NumberFormat(lang()).format(Math.round(n * 1e8)) + ' sats';
}

function fmtAxisCZK(n) {
  if (n >= 1e6) return (n / 1e6).toFixed(1).replace('.0', '') + 'M';
  if (n >= 1e3) return Math.round(n / 1e3) + 'k';
  return String(Math.round(n));
}

function fmtAxisSats(btc) {
  var s = Math.round(btc * 1e8);
  if (s >= 1e6) return (s / 1e6).toFixed(1).replace('.0', '') + 'M';
  if (s >= 1e3) return Math.round(s / 1e3) + 'k';
  return String(s);
}

function tr(key, args) {
  var str = window.AccBotI18n && window.AccBotI18n.t ? window.AccBotI18n.t(key) : key;
  if (args) {
    for (var i = 0; i < args.length; i++) {
      str = str.replace('{' + i + '}', args[i]);
    }
  }
  return str;
}

function setText(id, val) {
  var el = document.getElementById(id);
  if (el) el.textContent = val;
}

/*
 * Price data comes from public endpoints that need no API key and allow CORS:
 *  - daily BTC/USD closes from Bitstamp (history back to 2011), Binance BTC/USDT as a fallback,
 *  - daily USD/CZK reference rates of the ECB via Frankfurter.
 * BTC/CZK is BTC/USD multiplied by the USD/CZK rate of the same (or the previous) business day.
 */
var DAY = 86400;

function getJson(url) {
  return fetch(url).then(function (r) {
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
  });
}

// Returns [[timestampMs, closeUsd], ...] sorted by time.
function fetchUsdBitstamp(startTs, endTs) {
  var out = [];
  function chunk(from) {
    return getJson('https://www.bitstamp.net/api/v2/ohlc/btcusd/?step=86400&limit=1000&start=' + from)
      .then(function (data) {
        var rows = (data && data.data && data.data.ohlc) || [];
        for (var i = 0; i < rows.length; i++) {
          var t = parseInt(rows[i].timestamp, 10);
          var c = parseFloat(rows[i].close);
          if (t <= endTs && c > 0) out.push([t * 1000, c]);
        }
        var last = rows.length ? parseInt(rows[rows.length - 1].timestamp, 10) : endTs;
        if (rows.length === 1000 && last + DAY <= endTs) return chunk(last + DAY);
        return out;
      });
  }
  return chunk(startTs);
}

function fetchUsdBinance(startTs, endTs) {
  var out = [];
  function chunk(fromMs) {
    return getJson('https://data-api.binance.vision/api/v3/klines?symbol=BTCUSDT&interval=1d&limit=1000&startTime=' + fromMs)
      .then(function (rows) {
        for (var i = 0; i < rows.length; i++) {
          var c = parseFloat(rows[i][4]);
          if (rows[i][0] <= endTs * 1000 && c > 0) out.push([rows[i][0], c]);
        }
        var last = rows.length ? rows[rows.length - 1][0] : endTs * 1000;
        if (rows.length === 1000 && last + DAY * 1000 <= endTs * 1000) return chunk(last + DAY * 1000);
        return out;
      });
  }
  return chunk(startTs * 1000);
}

function fetchUsdPrices(startTs, endTs) {
  return fetchUsdBitstamp(startTs, endTs)
    .then(function (rows) {
      if (!rows.length) throw new Error('No Bitstamp data');
      return rows;
    })
    .catch(function () { return fetchUsdBinance(startTs, endTs); });
}

// Returns [[timestampMs, usdCzk], ...] sorted by time. Starts a week early so the first day has a rate.
function fetchUsdCzk(startTs) {
  var from = isoDate(new Date((startTs - 7 * DAY) * 1000));
  return getJson('https://api.frankfurter.dev/v1/' + from + '..?from=USD&to=CZK').then(function (data) {
    var rates = (data && data.rates) || {};
    var out = Object.keys(rates).sort().map(function (d) {
      return [new Date(d + 'T00:00:00Z').getTime(), rates[d].CZK];
    });
    if (!out.length) throw new Error('No FX data');
    return out;
  });
}

// Rate of the last business day on or before ts.
function rateAt(rates, ts) {
  var lo = 0, hi = rates.length - 1;
  if (ts <= rates[0][0]) return rates[0][1];
  while (lo < hi) {
    var mid = (lo + hi + 1) >> 1;
    if (rates[mid][0] <= ts) lo = mid;
    else hi = mid - 1;
  }
  return rates[lo][1];
}

function fetchPrices(startTs, endTs) {
  return Promise.all([fetchUsdPrices(startTs, endTs), fetchUsdCzk(startTs)]).then(function (res) {
    var usd = res[0], fx = res[1];
    usd.sort(function (a, b) { return a[0] - b[0]; });
    return {
      prices: usd.map(function (p) { return [p[0], p[1] * rateAt(fx, p[0])]; }),
      lastRate: fx[fx.length - 1][1]
    };
  });
}

function fetchCurrentUsd() {
  return getJson('https://www.bitstamp.net/api/v2/ticker/btcusd/').then(function (data) {
    var p = parseFloat(data && data.last);
    if (!(p > 0)) throw new Error('No ticker');
    return p;
  });
}

function calculate() {
  var calcBtn = document.getElementById('compare-calc');
  if (calcBtn.disabled) return;
  var startVal = document.getElementById('compare-start').value;
  var amount = parseFloat(document.getElementById('compare-amount').value);
  var interval = getInterval();

  if (!startVal || isNaN(amount) || amount <= 0) return;

  var startDate = new Date(startVal + 'T00:00:00');
  var now = new Date();
  if (startDate >= now) return;

  var resultsEl = document.getElementById('compare-results');
  var loadingEl = document.getElementById('compare-loading');
  var contentEl = document.getElementById('compare-content');
  var errorEl = document.getElementById('compare-error');

  calcBtn.disabled = true;
  setText('compare-status', '');
  resultsEl.style.display = 'block';
  loadingEl.style.display = 'flex';
  contentEl.style.display = 'none';
  errorEl.style.display = 'none';

  setTimeout(function () {
    var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    resultsEl.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
  }, 100);

  var startTs = Math.floor(startDate.getTime() / 1000);
  var endTs = Math.floor(now.getTime() / 1000);

  Promise.all([fetchPrices(startTs, endTs), fetchCurrentUsd().catch(function () { return null; })])
    .then(function (results) {
      var prices = results[0].prices;
      if (!prices.length) throw new Error('No price data');
      // Live price if the ticker answered, otherwise the latest daily close.
      var curPrice = results[1] ? results[1] * results[0].lastRate : prices[prices.length - 1][1];

      var dates = genBuyDates(startDate, now, interval);
      if (!dates.length) throw new Error('No buy dates');

      var sTotal = 0, aTotal = 0;
      var sHist = [], aHist = [];
      var rows = [];

      for (var i = 0; i < dates.length; i++) {
        var p = findPrice(prices, dates[i].getTime());
        sTotal += (amount * (1 - STOSUJ_FEE)) / p;
        aTotal += (amount * (1 - ACCBOT_FEE)) / p;
        sHist.push([dates[i].getTime(), sTotal]);
        aHist.push([dates[i].getTime(), aTotal]);
        rows.push([dates[i], p, sTotal, aTotal]);
      }

      var invested = dates.length * amount;

      setText('stosuj-fees', fmtCZK(invested * STOSUJ_FEE));
      setText('stosuj-btc', fmtBTC(sTotal));
      setText('stosuj-sats', fmtSats(sTotal));
      setText('stosuj-value', fmtCZK(sTotal * curPrice));

      setText('accbot-fees', fmtCZK(invested * ACCBOT_FEE));
      setText('accbot-btc', fmtBTC(aTotal));
      setText('accbot-sats', fmtSats(aTotal));
      setText('accbot-value', fmtCZK(aTotal * curPrice));

      var extraBtc = aTotal - sTotal;
      var extraVal = extraBtc * curPrice;
      var feeSave = invested * (STOSUJ_FEE - ACCBOT_FEE);

      var sumEl = document.getElementById('compare-summary-text');
      var savEl = document.getElementById('compare-savings-text');

      sumEl.textContent = tr('compare_summary_tpl', [dates.length, fmtCZK(invested), fmtCZK(curPrice)]);
      savEl.innerHTML = tr('compare_savings_tpl', [fmtSats(extraBtc), fmtCZK(extraVal), fmtCZK(feeSave)]);

      drawChart(sHist, aHist, prices);
      latestPurchases = rows;
      renderPurchaseTable();

      loadingEl.style.display = 'none';
      contentEl.style.display = 'block';
      setText('compare-status', sumEl.textContent + '. ' + savEl.textContent);
    })
    .catch(function () {
      loadingEl.style.display = 'none';
      errorEl.style.display = 'block';
    })
    .finally(function () {
      calcBtn.disabled = false;
    });
}

function renderPurchaseTable() {
  var table = document.getElementById('compare-table');
  if (!table.open) return;
  var fragment = document.createDocumentFragment();
  var formatter = new Intl.DateTimeFormat(document.documentElement.lang || 'en');
  latestPurchases.forEach(function (values) {
    var row = document.createElement('tr');
    [formatter.format(values[0]), fmtCZK(values[1]), fmtBTC(values[2]), fmtBTC(values[3])].forEach(function (value) {
      var cell = document.createElement('td');
      cell.textContent = value;
      row.appendChild(cell);
    });
    fragment.appendChild(row);
  });
  document.getElementById('compare-table-body').replaceChildren(fragment);
}

/* ===== Interactive Chart ===== */

function drawChart(sData, aData, rawPrices) {
  var el = document.getElementById('compare-chart');
  if (!el) return;
  if (sData.length < 2) {
    el.replaceChildren();
    return;
  }

  // Dimensions
  var W = 700, H = 320;
  var L = 68, R = 68, T = 16, B = 36;
  var cW = W - L - R, cH = H - T - B;

  // Time range
  var minT = sData[0][0], maxT = sData[sData.length - 1][0];
  var tRange = maxT - minT || 1;

  // BTC accumulated range (left Y)
  var maxBtc = 0;
  for (var i = 0; i < aData.length; i++) {
    if (aData[i][1] > maxBtc) maxBtc = aData[i][1];
  }
  maxBtc = maxBtc * 1.08 || 1;

  // BTC price range (right Y), only prices in the chart time range
  var chartPrices = [];
  var minP = Infinity, maxP = 0;
  for (var i = 0; i < rawPrices.length; i++) {
    if (rawPrices[i][0] >= minT && rawPrices[i][0] <= maxT) {
      chartPrices.push(rawPrices[i]);
      if (rawPrices[i][1] < minP) minP = rawPrices[i][1];
      if (rawPrices[i][1] > maxP) maxP = rawPrices[i][1];
    }
  }
  var pPad = (maxP - minP) * 0.12 || 1;
  minP = Math.max(0, minP - pPad);
  maxP = maxP + pPad;
  var pRange = maxP - minP || 1;

  // Scale helpers
  function sx(t) { return L + (t - minT) / tRange * cW; }
  function syB(v) { return T + cH - (v / maxBtc) * cH; }
  function syP(p) { return T + cH - ((p - minP) / pRange) * cH; }

  // --- Build SVG ---
  var svg = '';

  // Horizontal grid (5 lines)
  for (var i = 1; i <= 4; i++) {
    var gy = T + (cH / 5) * i;
    svg += '<line x1="' + L + '" y1="' + gy.toFixed(0) + '" x2="' + (W - R) + '" y2="' + gy.toFixed(0) + '" stroke="rgba(255,255,255,0.06)" stroke-width="1"/>';
  }

  // X-axis labels
  var xTicks = Math.min(6, sData.length);
  for (var i = 0; i <= xTicks; i++) {
    var t = minT + (tRange / xTicks) * i;
    var d = new Date(t);
    var lbl = (d.getMonth() + 1) + '/' + String(d.getFullYear()).slice(2);
    svg += '<text x="' + sx(t).toFixed(1) + '" y="' + (H - 6) + '" text-anchor="middle" fill="#c0c5d4" font-size="10" font-family="Inter,sans-serif">' + lbl + '</text>';
  }

  // Left Y-axis labels (sats)
  for (var i = 0; i <= 4; i++) {
    var v = (maxBtc / 4) * i;
    var yy = syB(v);
    svg += '<text x="' + (L - 8) + '" y="' + (yy + 3.5).toFixed(1) + '" text-anchor="end" fill="#c0c5d4" font-size="10" font-family="Inter,sans-serif">' + fmtAxisSats(v) + '</text>';
  }

  // Right Y-axis labels (CZK price)
  for (var i = 0; i <= 4; i++) {
    var p = minP + (pRange / 4) * i;
    var yy = syP(p);
    svg += '<text x="' + (W - R + 8) + '" y="' + (yy + 3.5).toFixed(1) + '" text-anchor="start" fill="#a8c5ff" font-size="10" font-family="Inter,sans-serif">' + fmtAxisCZK(p) + (lang() === 'cs' || lang() === 'sk' ? ' Kč' : ' CZK') + '</text>';
  }

  // Axis lines
  svg += '<line x1="' + L + '" y1="' + T + '" x2="' + L + '" y2="' + (H - B) + '" stroke="rgba(255,255,255,0.1)" stroke-width="1"/>';
  svg += '<line x1="' + (W - R) + '" y1="' + T + '" x2="' + (W - R) + '" y2="' + (H - B) + '" stroke="rgba(91,141,239,0.2)" stroke-width="1"/>';
  svg += '<line x1="' + L + '" y1="' + (H - B) + '" x2="' + (W - R) + '" y2="' + (H - B) + '" stroke="rgba(255,255,255,0.1)" stroke-width="1"/>';

  // BTC price line (dashed, blue)
  if (chartPrices.length > 1) {
    var pp = chartPrices.map(function (d) { return sx(d[0]).toFixed(1) + ',' + syP(d[1]).toFixed(1); }).join(' ');
    svg += '<polyline points="' + pp + '" fill="none" stroke="#5B8DEF" stroke-width="1.5" stroke-dasharray="5 3" opacity="0.45" stroke-linejoin="round"/>';
  }

  // Fill area between AccBot and Štosuj (gradient)
  svg += '<defs><linearGradient id="diffGrad" x1="0" y1="0" x2="1" y2="0">' +
    '<stop offset="0%" stop-color="#4ECCA3" stop-opacity="0.04"/>' +
    '<stop offset="100%" stop-color="#4ECCA3" stop-opacity="0.28"/>' +
    '</linearGradient></defs>';
  var topP = aData.map(function (d) { return sx(d[0]).toFixed(1) + ',' + syB(d[1]).toFixed(1); }).join(' ');
  var botP = sData.slice().reverse().map(function (d) { return sx(d[0]).toFixed(1) + ',' + syB(d[1]).toFixed(1); }).join(' ');
  svg += '<polygon points="' + topP + ' ' + botP + '" fill="url(#diffGrad)"/>';

  // Štosuj line
  var sP = sData.map(function (d) { return sx(d[0]).toFixed(1) + ',' + syB(d[1]).toFixed(1); }).join(' ');
  svg += '<polyline points="' + sP + '" fill="none" stroke="#E94560" stroke-width="2" stroke-linejoin="round"/>';

  // AccBot line
  var aP = aData.map(function (d) { return sx(d[0]).toFixed(1) + ',' + syB(d[1]).toFixed(1); }).join(' ');
  svg += '<polyline points="' + aP + '" fill="none" stroke="#4ECCA3" stroke-width="2" stroke-linejoin="round"/>';

  // End-of-chart bracket showing the difference
  var li = sData.length - 1;
  var yEndS = syB(sData[li][1]);
  var yEndA = syB(aData[li][1]);
  var xEnd = sx(sData[li][0]);
  var gapPx = yEndS - yEndA;
  var diffSatsEnd = aData[li][1] - sData[li][1];

  if (gapPx > 6) {
    var bx = Math.min(xEnd + 8, W - R - 2);
    svg += '<line x1="' + bx.toFixed(1) + '" y1="' + yEndA.toFixed(1) + '" x2="' + bx.toFixed(1) + '" y2="' + yEndS.toFixed(1) + '" stroke="#4ECCA3" stroke-width="2" opacity="0.8"/>';
    svg += '<line x1="' + (bx - 4).toFixed(1) + '" y1="' + yEndA.toFixed(1) + '" x2="' + (bx + 1).toFixed(1) + '" y2="' + yEndA.toFixed(1) + '" stroke="#4ECCA3" stroke-width="2" opacity="0.8"/>';
    svg += '<line x1="' + (bx - 4).toFixed(1) + '" y1="' + yEndS.toFixed(1) + '" x2="' + (bx + 1).toFixed(1) + '" y2="' + yEndS.toFixed(1) + '" stroke="#4ECCA3" stroke-width="2" opacity="0.8"/>';
    var midY = (yEndA + yEndS) / 2;
    svg += '<text x="' + (bx - 7).toFixed(1) + '" y="' + (midY + 3.5).toFixed(1) + '" text-anchor="end" fill="#4ECCA3" font-size="10" font-weight="600" font-family="Inter,sans-serif">+' + fmtAxisSats(diffSatsEnd) + '</text>';
  }

  // End-point dots
  svg += '<circle cx="' + xEnd.toFixed(1) + '" cy="' + yEndS.toFixed(1) + '" r="3.5" fill="#E94560" stroke="var(--bg)" stroke-width="2"/>';
  svg += '<circle cx="' + xEnd.toFixed(1) + '" cy="' + yEndA.toFixed(1) + '" r="3.5" fill="#4ECCA3" stroke="var(--bg)" stroke-width="2"/>';

  // Hover elements (hidden)
  svg += '<line id="ch-hline" x1="0" y1="' + T + '" x2="0" y2="' + (H - B) + '" stroke="rgba(255,255,255,0.25)" stroke-width="1" stroke-dasharray="3 3" visibility="hidden"/>';
  svg += '<circle id="ch-dot-s" r="4" fill="#E94560" stroke="var(--bg)" stroke-width="2" visibility="hidden"/>';
  svg += '<circle id="ch-dot-a" r="4" fill="#4ECCA3" stroke="var(--bg)" stroke-width="2" visibility="hidden"/>';
  svg += '<circle id="ch-dot-p" r="3" fill="#5B8DEF" stroke="var(--bg)" stroke-width="2" visibility="hidden"/>';

  // Invisible overlay
  svg += '<rect x="' + L + '" y="' + T + '" width="' + cW + '" height="' + cH + '" fill="transparent" style="cursor:crosshair" id="ch-overlay"/>';

  el.innerHTML = '<svg viewBox="0 0 ' + W + ' ' + H + '" class="compare-svg">' + svg + '</svg>';

  // --- Difference sparkline below main chart ---
  var diffEl = document.getElementById('compare-diff-chart');
  if (!diffEl) {
    diffEl = document.createElement('div');
    diffEl.id = 'compare-diff-chart';
    diffEl.className = 'compare-diff-chart';
    el.parentElement.insertBefore(diffEl, el.nextSibling);
  }
  var dH = 80, dT = 6, dB = 22;
  var dcH = dH - dT - dB;
  var diffData = [];
  var maxDiff = 0;
  for (var i = 0; i < sData.length; i++) {
    var dd = aData[i][1] - sData[i][1];
    diffData.push([sData[i][0], dd]);
    if (dd > maxDiff) maxDiff = dd;
  }
  maxDiff = maxDiff * 1.15 || 1;

  function syD(v) { return dT + dcH - (v / maxDiff) * dcH; }

  var dSvg = '';
  // Fill under diff line
  var dfPts = diffData.map(function (d) { return sx(d[0]).toFixed(1) + ',' + syD(d[1]).toFixed(1); }).join(' ');
  var dfBase = sx(diffData[diffData.length - 1][0]).toFixed(1) + ',' + (dH - dB) + ' ' + sx(diffData[0][0]).toFixed(1) + ',' + (dH - dB);
  dSvg += '<defs><linearGradient id="diffFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#4ECCA3" stop-opacity="0.25"/><stop offset="100%" stop-color="#4ECCA3" stop-opacity="0.02"/></linearGradient></defs>';
  dSvg += '<polygon points="' + dfPts + ' ' + dfBase + '" fill="url(#diffFill)"/>';
  dSvg += '<polyline points="' + dfPts + '" fill="none" stroke="#4ECCA3" stroke-width="1.5" stroke-linejoin="round"/>';
  // Baseline
  dSvg += '<line x1="' + L + '" y1="' + (dH - dB) + '" x2="' + (W - R) + '" y2="' + (dH - dB) + '" stroke="rgba(255,255,255,0.08)" stroke-width="1"/>';
  // Y labels (0 and max)
  dSvg += '<text x="' + (L - 8) + '" y="' + (dH - dB + 3) + '" text-anchor="end" fill="#c0c5d4" font-size="9" font-family="Inter,sans-serif">0</text>';
  dSvg += '<text x="' + (L - 8) + '" y="' + (dT + 8) + '" text-anchor="end" fill="#81ddbf" font-size="9" font-family="Inter,sans-serif">+' + fmtAxisSats(maxDiff / 1.15) + '</text>';
  // Label
  var diffLabel = tr('compare_diff_chart_label');
  dSvg += '<text x="' + (L + 4) + '" y="' + (dH - 4) + '" fill="#81ddbf" font-size="9" font-family="Inter,sans-serif">' + diffLabel + '</text>';
  // End value
  var lastDiff = diffData[diffData.length - 1];
  dSvg += '<circle cx="' + sx(lastDiff[0]).toFixed(1) + '" cy="' + syD(lastDiff[1]).toFixed(1) + '" r="3" fill="#4ECCA3" stroke="var(--bg)" stroke-width="1.5"/>';
  dSvg += '<text x="' + (sx(lastDiff[0]) - 6).toFixed(1) + '" y="' + (syD(lastDiff[1]) - 6).toFixed(1) + '" text-anchor="end" fill="#4ECCA3" font-size="10" font-weight="600" font-family="Inter,sans-serif">+' + fmtAxisSats(lastDiff[1]) + '</text>';

  // Hover line for diff chart
  dSvg += '<line id="ch-dhline" x1="0" y1="' + dT + '" x2="0" y2="' + (dH - dB) + '" stroke="rgba(255,255,255,0.25)" stroke-width="1" stroke-dasharray="3 3" visibility="hidden"/>';
  dSvg += '<circle id="ch-dot-d" r="3" fill="#4ECCA3" stroke="var(--bg)" stroke-width="2" visibility="hidden"/>';
  dSvg += '<rect x="' + L + '" y="' + dT + '" width="' + cW + '" height="' + dcH + '" fill="transparent" style="cursor:crosshair" id="ch-doverlay"/>';

  diffEl.innerHTML = '<svg viewBox="0 0 ' + W + ' ' + dH + '" class="compare-svg compare-diff-svg">' + dSvg + '</svg>';

  // Tooltip element
  var container = el.parentElement;
  var tooltip = document.getElementById('compare-tooltip');
  if (!tooltip) {
    tooltip = document.createElement('div');
    tooltip.id = 'compare-tooltip';
    tooltip.className = 'compare-tooltip';
    container.appendChild(tooltip);
  }
  tooltip.style.display = 'none';

  // --- Hover interaction ---
  var overlay = document.getElementById('ch-overlay');
  var dOverlay = document.getElementById('ch-doverlay');
  var svgNode = el.querySelector('svg');
  var hLine = document.getElementById('ch-hline');
  var dotS = document.getElementById('ch-dot-s');
  var dotA = document.getElementById('ch-dot-a');
  var dotP = document.getElementById('ch-dot-p');
  var dhLine = document.getElementById('ch-dhline');
  var dotD = document.getElementById('ch-dot-d');

  function snapIdx(mouseT) {
    var idx = 0, best = Infinity;
    for (var i = 0; i < sData.length; i++) {
      var dd = Math.abs(sData[i][0] - mouseT);
      if (dd < best) { best = dd; idx = i; }
    }
    return idx;
  }

  function showHover(idx, e) {
    var buyT = sData[idx][0];
    var xp = sx(buyT);

    var pIdx = findPriceIdx(chartPrices, buyT);
    var priceAt = chartPrices[pIdx][1];

    // Main chart hover elements
    hLine.setAttribute('x1', xp.toFixed(1));
    hLine.setAttribute('x2', xp.toFixed(1));
    hLine.setAttribute('visibility', 'visible');
    dotS.setAttribute('cx', xp.toFixed(1));
    dotS.setAttribute('cy', syB(sData[idx][1]).toFixed(1));
    dotS.setAttribute('visibility', 'visible');
    dotA.setAttribute('cx', xp.toFixed(1));
    dotA.setAttribute('cy', syB(aData[idx][1]).toFixed(1));
    dotA.setAttribute('visibility', 'visible');
    dotP.setAttribute('cx', xp.toFixed(1));
    dotP.setAttribute('cy', syP(priceAt).toFixed(1));
    dotP.setAttribute('visibility', 'visible');

    // Diff chart hover elements
    dhLine.setAttribute('x1', xp.toFixed(1));
    dhLine.setAttribute('x2', xp.toFixed(1));
    dhLine.setAttribute('visibility', 'visible');
    dotD.setAttribute('cx', xp.toFixed(1));
    dotD.setAttribute('cy', syD(diffData[idx][1]).toFixed(1));
    dotD.setAttribute('visibility', 'visible');

    // Tooltip
    var dd = new Date(buyT);
    var dateStr = new Intl.DateTimeFormat(document.documentElement.lang || 'en').format(dd);
    var sB = sData[idx][1], aB = aData[idx][1];
    var diff = aB - sB;
    var diffLbl = tr('compare_diff_row');

    tooltip.innerHTML =
      '<div class="ct-date">' + dateStr + '</div>' +
      '<div class="ct-price">BTC: ' + fmtCZK(priceAt) + '</div>' +
      '<div class="ct-row stosuj"><span>Štosuj</span><span>' + fmtSats(sB) + '</span><span>' + fmtCZK(sB * priceAt) + '</span></div>' +
      '<div class="ct-row accbot"><span>AccBot</span><span>' + fmtSats(aB) + '</span><span>' + fmtCZK(aB * priceAt) + '</span></div>' +
      '<div class="ct-diff"><span>' + diffLbl + '</span><span>+' + fmtSats(diff) + '</span><span>+' + fmtCZK(diff * priceAt) + '</span></div>';

    tooltip.style.display = '';

    var cRect = container.getBoundingClientRect();
    var tx = e.clientX - cRect.left + 16;
    var ty = e.clientY - cRect.top - 10;
    if (tx + tooltip.offsetWidth + 8 > cRect.width) {
      tx = e.clientX - cRect.left - tooltip.offsetWidth - 16;
    }
    if (ty + tooltip.offsetHeight > cRect.height) {
      ty = cRect.height - tooltip.offsetHeight - 8;
    }
    if (ty < 0) ty = 8;
    tooltip.style.left = tx + 'px';
    tooltip.style.top = ty + 'px';
  }

  function hideHover() {
    hLine.setAttribute('visibility', 'hidden');
    dotS.setAttribute('visibility', 'hidden');
    dotA.setAttribute('visibility', 'hidden');
    dotP.setAttribute('visibility', 'hidden');
    dhLine.setAttribute('visibility', 'hidden');
    dotD.setAttribute('visibility', 'hidden');
    tooltip.style.display = 'none';
  }

  function onMove(e, svgRef) {
    var rect = svgRef.getBoundingClientRect();
    var mx = (e.clientX - rect.left) / rect.width * W;
    var t = minT + ((mx - L) / cW) * tRange;
    showHover(snapIdx(t), e);
  }

  overlay.addEventListener('mousemove', function (e) { onMove(e, svgNode); });
  overlay.addEventListener('mouseleave', hideHover);

  var diffSvgNode = diffEl.querySelector('svg');
  dOverlay.addEventListener('mousemove', function (e) { onMove(e, diffSvgNode); });
  dOverlay.addEventListener('mouseleave', hideHover);
}

export function initCompare() {
  init();
}
