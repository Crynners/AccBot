(function () {
  'use strict';

  var STOSUJ_FEE = 0.015;
  var ACCBOT_FEE = 0.006;

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
        for (var j = 0; j < btns.length; j++) btns[j].classList.remove('active');
        this.classList.add('active');
      });
    }

    calcBtn.addEventListener('click', calculate);
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

  function fmtCZK(n) {
    return new Intl.NumberFormat('cs-CZ', {
      style: 'currency', currency: 'CZK', maximumFractionDigits: 0
    }).format(n);
  }

  function fmtBTC(n) {
    return n.toFixed(8) + ' BTC';
  }

  function fmtSats(n) {
    return new Intl.NumberFormat('cs-CZ').format(Math.round(n * 1e8)) + ' sats';
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

  function fetchPrices(startTs, endTs) {
    var allPrices = [];

    function fetchChunk(toTs) {
      var daysNeeded = Math.ceil((toTs - startTs) / 86400) + 1;
      var limit = Math.min(daysNeeded, 2000);

      return fetch(
        'https://min-api.cryptocompare.com/data/v2/histoday?fsym=BTC&tsym=CZK&limit=' + limit + '&toTs=' + toTs
      )
        .then(function (r) {
          if (!r.ok) throw new Error('HTTP ' + r.status);
          return r.json();
        })
        .then(function (data) {
          if (data.Response !== 'Success') throw new Error(data.Message || 'API error');
          var points = data.Data.Data;

          for (var i = 0; i < points.length; i++) {
            if (points[i].close > 0 && points[i].time >= startTs) {
              allPrices.push([points[i].time * 1000, points[i].close]);
            }
          }

          var earliest = points[0] ? points[0].time : startTs;
          if (limit === 2000 && earliest > startTs) {
            return fetchChunk(earliest);
          }

          allPrices.sort(function (a, b) { return a[0] - b[0]; });
          return allPrices;
        });
    }

    return fetchChunk(endTs);
  }

  function fetchCurrentPrice() {
    return fetch('https://min-api.cryptocompare.com/data/price?fsym=BTC&tsyms=CZK')
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (data) {
        if (!data.CZK) throw new Error('No CZK price');
        return data.CZK;
      });
  }

  function calculate() {
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

    resultsEl.style.display = 'block';
    loadingEl.style.display = 'flex';
    contentEl.style.display = 'none';
    errorEl.style.display = 'none';

    setTimeout(function () {
      resultsEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);

    var startTs = Math.floor(startDate.getTime() / 1000);
    var endTs = Math.floor(now.getTime() / 1000);

    Promise.all([fetchPrices(startTs, endTs), fetchCurrentPrice()])
      .then(function (results) {
        var prices = results[0];
        var curPrice = results[1];

        if (!prices.length) throw new Error('No price data');

        var dates = genBuyDates(startDate, now, interval);
        if (!dates.length) throw new Error('No buy dates');

        var sTotal = 0, aTotal = 0;
        var sHist = [], aHist = [];

        for (var i = 0; i < dates.length; i++) {
          var p = findPrice(prices, dates[i].getTime());
          sTotal += (amount * (1 - STOSUJ_FEE)) / p;
          aTotal += (amount * (1 - ACCBOT_FEE)) / p;
          sHist.push([dates[i].getTime(), sTotal]);
          aHist.push([dates[i].getTime(), aTotal]);
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

        loadingEl.style.display = 'none';
        contentEl.style.display = 'block';
      })
      .catch(function () {
        loadingEl.style.display = 'none';
        errorEl.style.display = 'block';
      });
  }

  /* ===== Interactive Chart ===== */

  function drawChart(sData, aData, rawPrices) {
    var el = document.getElementById('compare-chart');
    if (!el || sData.length < 2) return;

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

    // BTC price range (right Y) — only prices in chart time range
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
      svg += '<text x="' + sx(t).toFixed(1) + '" y="' + (H - 6) + '" text-anchor="middle" fill="rgba(255,255,255,0.35)" font-size="10" font-family="Inter,sans-serif">' + lbl + '</text>';
    }

    // Left Y-axis labels (sats)
    for (var i = 0; i <= 4; i++) {
      var v = (maxBtc / 4) * i;
      var yy = syB(v);
      svg += '<text x="' + (L - 8) + '" y="' + (yy + 3.5).toFixed(1) + '" text-anchor="end" fill="rgba(255,255,255,0.4)" font-size="10" font-family="Inter,sans-serif">' + fmtAxisSats(v) + '</text>';
    }

    // Right Y-axis labels (CZK price)
    for (var i = 0; i <= 4; i++) {
      var p = minP + (pRange / 4) * i;
      var yy = syP(p);
      svg += '<text x="' + (W - R + 8) + '" y="' + (yy + 3.5).toFixed(1) + '" text-anchor="start" fill="rgba(91,141,239,0.55)" font-size="10" font-family="Inter,sans-serif">' + fmtAxisCZK(p) + ' Kč</text>';
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
    dSvg += '<text x="' + (L - 8) + '" y="' + (dH - dB + 3) + '" text-anchor="end" fill="rgba(255,255,255,0.3)" font-size="9" font-family="Inter,sans-serif">0</text>';
    dSvg += '<text x="' + (L - 8) + '" y="' + (dT + 8) + '" text-anchor="end" fill="rgba(78,204,163,0.6)" font-size="9" font-family="Inter,sans-serif">+' + fmtAxisSats(maxDiff / 1.15) + '</text>';
    // Label
    var diffLabel = tr('compare_diff_chart_label');
    dSvg += '<text x="' + (L + 4) + '" y="' + (dH - 4) + '" fill="rgba(78,204,163,0.5)" font-size="9" font-family="Inter,sans-serif">' + diffLabel + '</text>';
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
      var dateStr = dd.getDate() + '. ' + (dd.getMonth() + 1) + '. ' + dd.getFullYear();
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

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
