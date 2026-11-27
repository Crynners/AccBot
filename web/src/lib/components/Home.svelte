<script>
  import HeroChart from './HeroChart.svelte';
  import ShotCarousel from './ShotCarousel.svelte';
  import StrategySim from './StrategySim.svelte';
  import WithdrawalMock from './WithdrawalMock.svelte';
  import QrScanMock from './QrScanMock.svelte';
  import Support from './Support.svelte';
  import { fmtDate, fmtMoney, fmtNum, tpl } from '$lib/format.js';
  import { tilt } from '$lib/tilt.js';

  let { t, lang, isHome = true } = $props();
  const home = () => (isHome ? '#' : `/${lang}/`);
  const sec = (id) => (isHome ? `#${id}` : `/${lang}/#${id}`);
  const shot = (name) => `/img/screenshots/${['cs', 'sk'].includes(lang) ? 'cs' : 'en'}/${name}.webp`;
  const shots = $derived([
    { src: shot('01_dashboard'), caption: t('shot_dashboard') },
    { src: shot('03_portfolio_full'), caption: t('shot_portfolio') },
    { src: shot('06_plan_details'), caption: t('shot_plan') },
    { src: shot('07_history'), caption: t('shot_history') },
    { src: shot('04_notifications'), caption: t('shot_notifications') },
    { src: shot('05_settings'), caption: t('shot_settings') }
  ]);
  const txDate = (iso, exchange) => `${fmtDate(lang, iso)}, ${exchange}`;
</script>

<main>
    <!-- ==================== Hero ==================== -->
    <section class="hero">
      <div class="container">
        <div class="hero-content">
          <h1><span class="accent">{t('hero_tagline')}</span></h1>
          <p class="subtitle">{t('hero_subtitle')}</p>
          <div class="hero-ctas">
            <a href="https://play.google.com/store/apps/details?id=com.accbot.dca" class="store-badge" target="_blank" rel="noopener">
              <svg width="160" height="48" viewBox="0 0 160 48" xmlns="http://www.w3.org/2000/svg">
                <rect width="160" height="48" rx="6" fill="#000"/>
                <rect x="0.5" y="0.5" width="159" height="47" rx="5.5" fill="none" stroke="#A6A6A6" stroke-width="1"/>
                <text x="55" y="16" fill="#FFF" font-family="Inter,Arial,sans-serif" font-size="7" font-weight="400" letter-spacing="0.5">GET IT ON</text>
                <text x="55" y="33" fill="#FFF" font-family="Inter,Arial,sans-serif" font-size="15" font-weight="600">Google Play</text>
                <g transform="translate(14,8)">
                  <path d="M1.2,0.5C0.9,0.8 0.7,1.3 0.7,1.9V30.1c0,0.6 0.2,1.1 0.5,1.4l0.1,0.1L17.4,16 17.4,15.8 1.2,0.5Z" fill="#4285F4"/>
                  <path d="M22.7,21.3 17.4,16 17.4,15.8 22.7,10.5l0.1,0.1c2,1.1 3.5,2 3.5,2l-0.1,0.1c0,0 -1.5,0.9 -3.5,2L22.7,21.3Z" fill="#FBBC04"/>
                  <path d="M22.8,21.2 17.4,16 1.3,31.5c0.6,0.7 1.7,0.7 2.9,0.1L22.8,21.2Z" fill="#EA4335"/>
                  <path d="M22.8,10.6 4.2,0.3C3,0.3 1.9,0.3 1.3,0.5L17.4,16 22.8,10.6Z" fill="#34A853"/>
                </g>
              </svg>
            </a>
            <a href="https://github.com/crynners/AccBot" class="btn btn-outline" target="_blank" rel="noopener">
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
              <span>{t('hero_cta_github')}</span>
            </a>
            <span class="store-badge disabled">
              <svg width="160" height="48" viewBox="0 0 160 48" xmlns="http://www.w3.org/2000/svg">
                <rect width="160" height="48" rx="6" fill="#000"/>
                <rect x="0.5" y="0.5" width="159" height="47" rx="5.5" fill="none" stroke="#A6A6A6" stroke-width="1"/>
                <text x="52" y="16" fill="#FFF" font-family="Inter,Arial,sans-serif" font-size="7" font-weight="400" letter-spacing="0.5">COMING SOON</text>
                <text x="52" y="33" fill="#FFF" font-family="Inter,Arial,sans-serif" font-size="15" font-weight="600">App Store</text>
                <g transform="translate(16,9) scale(0.058)" fill="#FFF">
                  <path d="M376.2,337.5c-8.8,20.4-13,29.5-24.3,47.4-15.8,25-38,56.2-65.5,56.4-24.5,0.2-30.8-16-64-15.8-33.3,0.2-40.1,16.1-64.6,15.9-27.5-0.2-48.5-28.4-64.3-53.4C48.7,316.2,21.8,222.8,69.4,161.3c16.9-21.8,41.8-34.6,68.2-35,24.3-0.4,47.2,16.4,62,16.4,14.9,0,42.7-20.3,72-17.3,12.3,0.5,46.7,5,68.8,37.4C337.7,164.9,297.3,206.5,297.7,253,298.2,308,348.3,327.6,376.2,337.5zM268,57.7c12.9-15.6,22.7-37.7,19.1-60.1-21.2,1.4-46,14.9-60.5,32.4-13.2,15.9-24.1,38.3-19.9,60.5C229.7,91.6,253.5,75.2,268,57.7z"/>
                </g>
              </svg>
            </span>
          </div>
        </div>

        <!-- Real app screenshot -->
        <div class="hero-mockup" use:tilt>
          <HeroChart />
          <div class="phone-frame phone-frame-shot">
            <img width="540" height="1200" fetchpriority="high" alt={t('shot_dashboard_alt')} src={shot('01_dashboard')}>
          </div>
        </div>
      </div>
    </section>

    <!-- ==================== Value Proposition ==================== -->
    <section class="section-alt">
      <div class="container">
        <div class="value-grid">
          <div class="value-card reveal">
            <div class="icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>
            </div>
            <h3>{t('value_custody_title')}</h3>
            <p>{t('value_custody_desc')}</p>
          </div>
          <div class="value-card reveal">
            <div class="icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/></svg>
            </div>
            <h3>{t('value_auto_title')}</h3>
            <p>{t('value_auto_desc')}</p>
          </div>
          <div class="value-card reveal">
            <div class="icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
            </div>
            <h3>{t('value_open_title')}</h3>
            <p>{t('value_open_desc')}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ==================== Features Deep Dive ==================== -->
    <section id="features">
      <div class="container">
        <div class="section-header reveal">
          <h2>{t('features_title')}</h2>
        </div>

        <!-- Smart DCA Strategies -->
        <div class="feature-block reveal">
          <div class="feature-text">
            <h3>{t('features_strategies_title')}</h3>
            <p>{t('features_strategies_desc')}</p>
            <div class="strategy-cards">
              <div class="strategy-card">
                <span class="dot green"></span>
                <div class="strategy-card-info">
                  <strong>{t('features_strategies_classic')}</strong>
                  <span>{t('features_strategies_classic_desc')}</span>
                </div>
              </div>
              <div class="strategy-card">
                <span class="dot blue"></span>
                <div class="strategy-card-info">
                  <strong>{t('features_strategies_ath')}</strong>
                  <span>{t('features_strategies_ath_desc')}</span>
                </div>
              </div>
              <div class="strategy-card">
                <span class="dot orange"></span>
                <div class="strategy-card-info">
                  <strong>{t('features_strategies_fg')}</strong>
                  <span>{t('features_strategies_fg_desc')}</span>
                </div>
              </div>
            </div>
          </div>
          <div class="feature-visual">
            <StrategySim {t} {lang} />
          </div>
        </div>

        <!-- Portfolio Analytics -->
        <div class="feature-block reverse reveal">
          <div class="feature-text">
            <h3>{t('features_portfolio_title')}</h3>
            <p>{t('features_portfolio_desc')}</p>
          </div>
          <div class="feature-visual">
            <div class="mock-tabs">
              <div class="mock-tab active">{t('mock_holdings')}</div>
              <div class="mock-tab">{t('mock_by_exchange')}</div>
              <div class="mock-tab">{t('mock_performance')}</div>
            </div>
            <div class="mock-stat-row">
              <div class="mock-stat">
                <div class="mock-stat-label">{t('mock_value')}</div>
                <div class="mock-stat-value">{fmtMoney(lang, 4218)}</div>
              </div>
              <div class="mock-stat">
                <div class="mock-stat-label">{t('mock_invested')}</div>
                <div class="mock-stat-value">{fmtMoney(lang, 3750)}</div>
              </div>
              <div class="mock-stat">
                <div class="mock-stat-label">{t('mock_profit')}</div>
                <div class="mock-stat-value green">+{fmtNum(lang, 12.48, 2)} %</div>
              </div>
            </div>
            <div class="mock-chart" style="height:80px">
              <svg viewBox="0 0 240 80" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="cg2" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#4ECCA3" stop-opacity="0.2"/>
                    <stop offset="100%" stop-color="#4ECCA3" stop-opacity="0"/>
                  </linearGradient>
                </defs>
                <path d="M0,70 L30,65 L60,60 L90,55 L120,50 L150,45 L180,40 L210,30 L240,25" fill="none" stroke="#4ECCA3" stroke-width="2"/>
                <path d="M0,70 L30,65 L60,60 L90,55 L120,50 L150,45 L180,40 L210,30 L240,25 L240,80 L0,80 Z" fill="url(#cg2)"/>
                <path d="M0,72 L30,70 L60,68 L90,66 L120,64 L150,62 L180,60 L210,58 L240,56" fill="none" stroke="var(--text-muted)" stroke-width="1" stroke-dasharray="4 4" opacity="0.4"/>
              </svg>
            </div>
            <div style="display:flex;gap:16px;font-size:0.625rem;color:var(--text-muted)">
              <span style="display:flex;align-items:center;gap:4px"><span style="width:8px;height:2px;background:var(--accent);display:inline-block"></span> {t('mock_value')}</span>
              <span style="display:flex;align-items:center;gap:4px"><span style="width:8px;height:2px;background:var(--text-muted);display:inline-block;opacity:0.4"></span> {t('mock_invested')}</span>
            </div>
          </div>
        </div>

        <!-- Transaction History -->
        <div class="feature-block reveal">
          <div class="feature-text">
            <h3>{t('features_history_title')}</h3>
            <p>{t('features_history_desc')}</p>
          </div>
          <div class="feature-visual">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
              <span style="font-size:0.75rem;font-weight:600">{t('mock_history')}</span>
              <span style="font-size:0.625rem;color:var(--accent);border:1px solid var(--accent);border-radius:4px;padding:2px 8px">{t('mock_export')}</span>
            </div>
            <div class="mock-tx-row">
              <div class="mock-tx-left">
                <div class="mock-tx-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/></svg></div>
                <div>
                  <div class="mock-tx-pair">BTC/EUR</div>
                  <div class="mock-tx-date">{txDate('2026-11-14', 'Coinmate')}</div>
                </div>
              </div>
              <div>
                <div class="mock-tx-amount" style="color:var(--accent)">+{fmtNum(lang, 0.00082, 5)} BTC</div>
                <div class="mock-tx-fiat">{fmtMoney(lang, 50, 'EUR', 2)}</div>
              </div>
            </div>
            <div class="mock-tx-row">
              <div class="mock-tx-left">
                <div class="mock-tx-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/></svg></div>
                <div>
                  <div class="mock-tx-pair">ETH/EUR</div>
                  <div class="mock-tx-date">{txDate('2026-11-10', 'Kraken')}</div>
                </div>
              </div>
              <div>
                <div class="mock-tx-amount" style="color:var(--accent)">+{fmtNum(lang, 0.0125, 4)} ETH</div>
                <div class="mock-tx-fiat">{fmtMoney(lang, 25, 'EUR', 2)}</div>
              </div>
            </div>
            <div class="mock-tx-row">
              <div class="mock-tx-left">
                <div class="mock-tx-icon" style="background:rgba(233,69,96,0.1)"><svg viewBox="0 0 24 24" fill="none" stroke="var(--error)" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg></div>
                <div>
                  <div class="mock-tx-pair">BTC/EUR</div>
                  <div class="mock-tx-date">{txDate('2026-11-07', 'Coinmate')}</div>
                </div>
              </div>
              <div>
                <div class="mock-tx-amount" style="color:var(--error)">{t('mock_failed')}</div>
                <div class="mock-tx-fiat">{t('mock_insufficient')}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Sandbox Mode -->
        <div class="feature-block reverse reveal">
          <div class="feature-text">
            <h3>{t('features_sandbox_title')}</h3>
            <p>{t('features_sandbox_desc')}</p>
          </div>
          <div class="feature-visual">
            <div class="mock-sandbox-banner">{t('mock_sandbox_banner')}</div>
            <div class="mock-toggle-row">
              <div>
                <div class="mock-toggle-label">{t('mock_sandbox_label')}</div>
                <div class="mock-toggle-desc">{t('mock_sandbox_desc')}</div>
              </div>
              <div class="mock-toggle"></div>
            </div>
            <div class="mock-card" style="margin-top:16px;border-color:rgba(255,167,38,0.2)">
              <div class="mock-card-label" style="color:var(--warning)">{t('mock_testnet_title')}</div>
              <div style="font-size:0.75rem;color:var(--text-muted);margin-top:6px">{t('mock_testnet_desc')}</div>
            </div>
          </div>
        </div>

        <!-- Withdrawal reminders -->
        <div class="feature-block reveal">
          <div class="feature-text">
            <h3>{t('features_withdrawal_title')}</h3>
            <p>{t('features_withdrawal_desc')}</p>
          </div>
          <div class="feature-visual">
            <WithdrawalMock {t} {lang} />
          </div>
        </div>

        <!-- QR Code Setup -->
        <div class="feature-block reverse reveal">
          <div class="feature-text">
            <h3>{t('features_qr_title')}</h3>
            <p>{t('features_qr_desc')}</p>
          </div>
          <div class="feature-visual">
            <QrScanMock {t} />
          </div>
        </div>

        <!-- Encrypted Backup -->
        <div class="feature-block reveal">
          <div class="feature-text">
            <h3>{t('features_backup_title')}</h3>
            <p>{t('features_backup_desc')}</p>
          </div>
          <div class="feature-visual">
            <div class="mock-card">
              <div style="display:flex;align-items:center;gap:10px;margin-bottom:12px">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                <span style="font-size:0.875rem;font-weight:600">accbot-backup-2026-03.enc</span>
              </div>
              <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px">
                <span style="font-size:0.625rem;background:rgba(247,147,26,0.12);color:var(--accent);padding:2px 8px;border-radius:4px;font-weight:600">AES-256-GCM</span>
                <span style="font-size:0.625rem;background:var(--surface-alt);color:var(--text-muted);padding:2px 8px;border-radius:4px">{t('mock_backup_password')}</span>
              </div>
              <div style="font-size:0.6875rem;color:var(--text-muted);display:flex;flex-direction:column;gap:4px">
                <div style="display:flex;align-items:center;gap:6px">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                  <span>{tpl(t('mock_backup_plans_tpl'), 3)}</span>
                </div>
                <div style="display:flex;align-items:center;gap:6px">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                  <span>{tpl(t('mock_backup_tx_tpl'), fmtNum(lang, 847))}</span>
                </div>
                <div style="display:flex;align-items:center;gap:6px">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                  <span>{t('mock_backup_keys')}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="more-features reveal">
          <h3>{t('more_title')}</h3>
          <div class="more-grid">
            <div class="more-card">
              <div class="more-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/></svg></div>
              <h4>{t('more_sells_title')}</h4>
              <p>{t('more_sells_desc')}</p>
            </div>
            <div class="more-card">
              <div class="more-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg></div>
              <h4>{t('more_import_title')}</h4>
              <p>{t('more_import_desc')}</p>
            </div>
            <div class="more-card">
              <div class="more-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg></div>
              <h4>{t('more_share_title')}</h4>
              <p>{t('more_share_desc')}</p>
            </div>
            <div class="more-card">
              <div class="more-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg></div>
              <h4>{t('more_watch_title')}</h4>
              <p>{t('more_watch_desc')}</p>
            </div>
          </div>
        </div>

      </div>
    </section>

    <!-- ==================== Screenshots ==================== -->
    <section id="screenshots" class="section-alt">
      <div class="container">
        <div class="section-header reveal">
          <h2>{t('screenshots_title')}</h2>
          <p>{t('screenshots_subtitle')}</p>
        </div>
      </div>
      <div class="container reveal">
        <ShotCarousel {t} {shots} />
      </div>
    </section>

    <!-- ==================== Supported Exchanges ==================== -->
    <section id="exchanges">
      <div class="container">
        <div class="section-header reveal">
          <h2>{t('exchanges_title')}</h2>
          <p>{t('exchanges_subtitle')}</p>
        </div>
        <div class="exchange-logos reveal">
          <div class="exchange-logo">
            <div class="exchange-icon">
              <img src="/img/exchanges/ic_exchange_coinmate.png" alt="Coinmate" width="32" height="32">
            </div>
            <span class="exchange-name">Coinmate</span>
          </div>
          <div class="exchange-logo">
            <div class="exchange-icon">
              <img src="/img/exchanges/ic_exchange_binance.png" alt="Binance" width="32" height="32">
            </div>
            <span class="exchange-name">Binance</span>
          </div>
          <div class="exchange-logo">
            <div class="exchange-icon">
              <img src="/img/exchanges/ic_exchange_kraken.png" alt="Kraken" width="32" height="32">
            </div>
            <span class="exchange-name">Kraken</span>
          </div>
          <div class="exchange-logo">
            <div class="exchange-icon">
              <img src="/img/exchanges/ic_exchange_kucoin.png" alt="KuCoin" width="32" height="32">
            </div>
            <span class="exchange-name">KuCoin</span>
          </div>
          <div class="exchange-logo">
            <div class="exchange-icon">
              <img src="/img/exchanges/ic_exchange_coinbase.png" alt="Coinbase" width="32" height="32">
            </div>
            <span class="exchange-name">Coinbase</span>
          </div>
        </div>
        <div class="exchange-capabilities">
          <table>
            <caption>{t('exchanges_capabilities')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('exchanges_col_exchange')}</th>
                <th scope="col">{t('exchanges_col_sandbox')}</th>
                <th scope="col">{t('exchanges_col_sells')}</th>
                <th scope="col">{t('exchanges_col_import')}</th>
              </tr>
            </thead>
            <tbody>
              <tr><th scope="row">Binance</th><td>{t('exchanges_yes')}</td><td>{t('exchanges_yes')}</td><td>API</td></tr>
              <tr><th scope="row">Coinmate</th><td>{t('exchanges_no')}</td><td>{t('exchanges_yes')}</td><td>API, CSV</td></tr>
              <tr><th scope="row">Kraken</th><td>{t('exchanges_no')}</td><td>{t('exchanges_no')}</td><td>API</td></tr>
              <tr><th scope="row">KuCoin</th><td>{t('exchanges_no')}</td><td>{t('exchanges_no')}</td><td>{t('exchanges_no')}</td></tr>
              <tr><th scope="row">Coinbase</th><td>{t('exchanges_no')}</td><td>{t('exchanges_no')}</td><td>API</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- ==================== Security ==================== -->
    <section id="security" class="section-alt">
      <div class="container">
        <div class="section-header reveal">
          <h2>{t('security_title')}</h2>
          <p>{t('security_subtitle')}</p>
        </div>
        <div class="security-grid">
          <div class="security-item reveal">
            <div class="security-check">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
            <div>
              <strong>{t('security_aes')}</strong>
              <span>{t('security_aes_desc')}</span>
            </div>
          </div>
          <div class="security-item reveal">
            <div class="security-check">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
            <div>
              <strong>{t('security_local')}</strong>
              <span>{t('security_local_desc')}</span>
            </div>
          </div>
          <div class="security-item reveal">
            <div class="security-check">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
            <div>
              <strong>{t('security_direct')}</strong>
              <span>{t('security_direct_desc')}</span>
            </div>
          </div>
          <div class="security-item reveal">
            <div class="security-check">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
            <div>
              <strong>{t('security_biometric')}</strong>
              <span>{t('security_biometric_desc')}</span>
            </div>
          </div>
          <div class="security-item reveal">
            <div class="security-check">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
            <div>
              <strong>{t('security_opensource')}</strong>
              <span>{t('security_opensource_desc')}</span>
            </div>
          </div>
          <div class="security-item reveal">
            <div class="security-check">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
            <div>
              <strong>{t('security_nocloud')}</strong>
              <span>{t('security_nocloud_desc')}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ==================== How It Works ==================== -->
    <section>
      <div class="container">
        <div class="section-header reveal">
          <h2>{t('howto_title')}</h2>
        </div>
        <div class="steps">
          <div class="step reveal">
            <div class="step-number">1</div>
            <h3>{t('howto_step1_title')}</h3>
            <p>{t('howto_step1_desc')}</p>
          </div>
          <div class="step reveal">
            <div class="step-number">2</div>
            <h3>{t('howto_step2_title')}</h3>
            <p>{t('howto_step2_desc')}</p>
          </div>
          <div class="step reveal">
            <div class="step-number">3</div>
            <h3>{t('howto_step3_title')}</h3>
            <p>{t('howto_step3_desc')}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ==================== Compare ==================== -->
    <section id="compare" class="section-alt">
      <div class="container">
        <div class="section-header reveal">
          <h2>{t('compare_title')}</h2>
          <p>{t('compare_subtitle')}</p>
        </div>

        <p class="compare-note reveal">{t('compare_note')}</p>

        <div class="compare-form reveal" id="compare-form">
          <div class="compare-inputs">
            <div class="compare-input-group">
              <label for="compare-start">{t('compare_start_label')}</label>
              <input type="date" id="compare-start">
            </div>
            <div class="compare-input-group">
              <label for="compare-amount">{t('compare_amount_label')}</label>
              <input type="number" id="compare-amount" value="1000" min="1" step="100">
            </div>
            <div class="compare-input-group">
              <span class="compare-input-label" id="compare-interval-label">{t('compare_interval_label')}</span>
              <div class="compare-interval-btns" role="group" aria-labelledby="compare-interval-label">
                <button type="button" class="compare-interval-btn" aria-pressed="false" data-interval="daily">{t('compare_daily')}</button>
                <button type="button" class="compare-interval-btn active" aria-pressed="true" data-interval="weekly">{t('compare_weekly')}</button>
                <button type="button" class="compare-interval-btn" aria-pressed="false" data-interval="monthly">{t('compare_monthly')}</button>
              </div>
            </div>
            <div class="compare-input-group">
              <span class="compare-input-label" id="compare-plan-label">{t('compare_plan_label')}</span>
              <div class="compare-interval-btns" role="group" aria-labelledby="compare-plan-label">
                <button type="button" class="compare-interval-btn" aria-pressed="false" data-plan="free">FREE</button>
                <button type="button" class="compare-interval-btn active" aria-pressed="true" data-plan="premium">PREMIUM</button>
                <button type="button" class="compare-interval-btn" aria-pressed="false" data-plan="pro">PRO</button>
              </div>
              <p class="compare-plan-note" id="compare-plan-note"></p>
            </div>
          </div>
          <button type="button" class="btn btn-primary compare-calc-btn" id="compare-calc">{t('compare_calculate')}</button>
        </div>
        <div id="compare-status" class="compare-status" role="status"></div>

        <div id="compare-results" class="compare-results" style="display:none">
          <div id="compare-loading" class="compare-loading"></div>
          <div id="compare-error" class="compare-error" style="display:none" role="alert">{t('compare_error')}</div>
          <div id="compare-content" style="display:none">
            <div class="compare-summary">
              <span id="compare-summary-text"></span>
            </div>

            <div class="compare-cards">
              <div class="compare-card stosuj">
                <div class="compare-card-header">
                  <span class="compare-card-name">Štosuj</span>
                  <span class="compare-card-fee" id="stosuj-fee-label">{t('compare_fee_stosuj')}</span>
                </div>
                <div class="compare-stat">
                  <span class="compare-stat-label">{t('compare_fees_paid')}</span>
                  <span class="compare-stat-value" id="stosuj-fees"></span>
                </div>
                <div class="compare-stat-sub" id="stosuj-sub"></div>
                <div class="compare-stat">
                  <span class="compare-stat-label">{t('compare_btc_accumulated')}</span>
                  <span class="compare-stat-value" id="stosuj-btc"></span>
                </div>
                <div class="compare-stat-sub" id="stosuj-sats"></div>
                <div class="compare-stat">
                  <span class="compare-stat-label">{t('compare_current_value')}</span>
                  <span class="compare-stat-value" id="stosuj-value"></span>
                </div>
              </div>

              <div class="compare-card accbot">
                <div class="compare-card-header">
                  <span class="compare-card-name">AccBot</span>
                  <span class="compare-card-fee">{t('compare_fee_accbot')}</span>
                </div>
                <div class="compare-stat">
                  <span class="compare-stat-label">{t('compare_fees_paid')}</span>
                  <span class="compare-stat-value" id="accbot-fees"></span>
                </div>
                <div class="compare-stat">
                  <span class="compare-stat-label">{t('compare_btc_accumulated')}</span>
                  <span class="compare-stat-value" id="accbot-btc"></span>
                </div>
                <div class="compare-stat-sub" id="accbot-sats"></div>
                <div class="compare-stat">
                  <span class="compare-stat-label">{t('compare_current_value')}</span>
                  <span class="compare-stat-value" id="accbot-value"></span>
                </div>
              </div>
            </div>

            <div class="compare-savings">
              <span id="compare-savings-text"></span>
            </div>

            <div class="compare-chart-container">
              <h3>{t('compare_chart_title')}</h3>
              <div id="compare-chart"></div>
              <div class="compare-legend">
                <span class="compare-legend-item price">{t('compare_legend_price')}</span>
                <span class="compare-legend-item stosuj">{t('compare_legend_stosuj')}</span>
                <span class="compare-legend-item accbot">{t('compare_legend_accbot')}</span>
              </div>
            </div>

            <details id="compare-table" class="compare-table">
              <summary>{t('compare_table_label')}</summary>
              <div class="compare-table-scroll">
                <table aria-labelledby="compare-table-heading">
                  <caption id="compare-table-heading">{t('compare_chart_title')}</caption>
                  <thead>
                    <tr>
                      <th scope="col">{t('compare_table_date')}</th>
                      <th scope="col">{t('compare_legend_price')}</th>
                      <th scope="col">Štosuj</th>
                      <th scope="col">AccBot</th>
                    </tr>
                  </thead>
                  <tbody id="compare-table-body"></tbody>
                </table>
              </div>
            </details>
            <p class="compare-disclaimer">{t('compare_disclaimer')}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ==================== FAQ ==================== -->
    <section id="faq">
      <div class="container">
        <div class="section-header reveal">
          <h2>{t('faq_title')}</h2>
        </div>
        <div class="faq-list reveal">
          <details class="faq-item">
            <summary>{t('faq_q1')}</summary>
            <p>{t('faq_a1')}</p>
          </details>
          <details class="faq-item">
            <summary>{t('faq_q2')}</summary>
            <p>{t('faq_a2')}</p>
          </details>
          <details class="faq-item">
            <summary>{t('faq_q3')}</summary>
            <p>{t('faq_a3')}</p>
          </details>
          <details class="faq-item">
            <summary>{t('faq_q4')}</summary>
            <p>{t('faq_a4')}</p>
          </details>
          <details class="faq-item">
            <summary>{t('faq_q5')}</summary>
            <p>{t('faq_a5')}</p>
          </details>
          <details class="faq-item">
            <summary>{t('faq_q6')}</summary>
            <p>{t('faq_a6')}</p>
          </details>
          <details class="faq-item">
            <summary>{t('faq_q7')}</summary>
            <p>{t('faq_a7')}</p>
          </details>
        </div>
      </div>
    </section>

    <!-- ==================== Support ==================== -->
    <section id="support" class="section-alt">
      <div class="container">
        <div class="section-header reveal">
          <h2>{t('support_title')}</h2>
          <p>{t('support_subtitle')}</p>
        </div>
        <Support {t} />
      </div>
    </section>

    <!-- ==================== Download CTA ==================== -->
    <section id="download" class="download-section">
      <div class="container">
        <div class="section-header reveal">
          <h2>{t('download_title')}</h2>
          <p>{t('download_subtitle')}</p>
        </div>
        <div class="download-buttons reveal">
          <a href="https://play.google.com/store/apps/details?id=com.accbot.dca" class="store-badge" target="_blank" rel="noopener">
            <svg width="160" height="48" viewBox="0 0 160 48" xmlns="http://www.w3.org/2000/svg">
              <rect width="160" height="48" rx="6" fill="#000"/>
              <rect x="0.5" y="0.5" width="159" height="47" rx="5.5" fill="none" stroke="#A6A6A6" stroke-width="1"/>
              <text x="55" y="16" fill="#FFF" font-family="Inter,Arial,sans-serif" font-size="7" font-weight="400" letter-spacing="0.5">GET IT ON</text>
              <text x="55" y="33" fill="#FFF" font-family="Inter,Arial,sans-serif" font-size="15" font-weight="600">Google Play</text>
              <g transform="translate(14,8)">
                <path d="M1.2,0.5C0.9,0.8 0.7,1.3 0.7,1.9V30.1c0,0.6 0.2,1.1 0.5,1.4l0.1,0.1L17.4,16 17.4,15.8 1.2,0.5Z" fill="#4285F4"/>
                <path d="M22.7,21.3 17.4,16 17.4,15.8 22.7,10.5l0.1,0.1c2,1.1 3.5,2 3.5,2l-0.1,0.1c0,0 -1.5,0.9 -3.5,2L22.7,21.3Z" fill="#FBBC04"/>
                <path d="M22.8,21.2 17.4,16 1.3,31.5c0.6,0.7 1.7,0.7 2.9,0.1L22.8,21.2Z" fill="#EA4335"/>
                <path d="M22.8,10.6 4.2,0.3C3,0.3 1.9,0.3 1.3,0.5L17.4,16 22.8,10.6Z" fill="#34A853"/>
              </g>
            </svg>
          </a>
          <a href="https://github.com/crynners/AccBot/releases" class="btn btn-outline" target="_blank" rel="noopener">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
            <span>{t('download_github')}</span>
          </a>
          <span class="store-badge disabled">
            <svg width="160" height="48" viewBox="0 0 160 48" xmlns="http://www.w3.org/2000/svg">
              <rect width="160" height="48" rx="6" fill="#000"/>
              <rect x="0.5" y="0.5" width="159" height="47" rx="5.5" fill="none" stroke="#A6A6A6" stroke-width="1"/>
              <text x="52" y="16" fill="#FFF" font-family="Inter,Arial,sans-serif" font-size="7" font-weight="400" letter-spacing="0.5">COMING SOON</text>
              <text x="52" y="33" fill="#FFF" font-family="Inter,Arial,sans-serif" font-size="15" font-weight="600">App Store</text>
              <g transform="translate(16,9) scale(0.058)" fill="#FFF">
                <path d="M376.2,337.5c-8.8,20.4-13,29.5-24.3,47.4-15.8,25-38,56.2-65.5,56.4-24.5,0.2-30.8-16-64-15.8-33.3,0.2-40.1,16.1-64.6,15.9-27.5-0.2-48.5-28.4-64.3-53.4C48.7,316.2,21.8,222.8,69.4,161.3c16.9-21.8,41.8-34.6,68.2-35,24.3-0.4,47.2,16.4,62,16.4,14.9,0,42.7-20.3,72-17.3,12.3,0.5,46.7,5,68.8,37.4C337.7,164.9,297.3,206.5,297.7,253,298.2,308,348.3,327.6,376.2,337.5zM268,57.7c12.9-15.6,22.7-37.7,19.1-60.1-21.2,1.4-46,14.9-60.5,32.4-13.2,15.9-24.1,38.3-19.9,60.5C229.7,91.6,253.5,75.2,268,57.7z"/>
              </g>
            </svg>
          </span>
        </div>
      </div>
    </section>
  </main>
