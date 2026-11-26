(function () {
  'use strict';

  var DEFAULT_LANG = 'en';
  // Order matches the language picker.
  var SUPPORTED = ['en', 'cs', 'sk', 'de', 'fr', 'es', 'it', 'pt', 'pl', 'uk', 'ru', 'zh', 'ja'];
  // Languages that use the Czech app screenshots; everyone else sees the English ones.
  var CZECH_SCREENSHOTS = ['cs', 'sk'];
  var current = DEFAULT_LANG;
  var cache = {};
  var _tr = {};

  function isLangHash(hash) {
    return SUPPORTED.indexOf(hash) !== -1;
  }

  function detect() {
    var hash = window.location.hash.replace('#', '');
    if (isLangHash(hash)) return hash;
    var saved = null;
    try { saved = localStorage.getItem('accbot-lang'); } catch (e) { /* storage blocked */ }
    if (saved && isLangHash(saved)) return saved;
    var navs = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || ''];
    for (var i = 0; i < navs.length; i++) {
      var code = (navs[i] || '').slice(0, 2).toLowerCase();
      if (isLangHash(code)) return code;
    }
    return DEFAULT_LANG;
  }

  function load(lang) {
    if (cache[lang]) return Promise.resolve(cache[lang]);
    return fetch('locales/' + lang + '.json?v=3')
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (data) {
        cache[lang] = data;
        return data;
      });
  }

  function apply(translations) {
    _tr = translations;
    var els = document.querySelectorAll('[data-i18n]');
    for (var i = 0; i < els.length; i++) {
      var key = els[i].getAttribute('data-i18n');
      var val = translations[key];
      if (val) {
        if (val.indexOf('<') !== -1) els[i].innerHTML = val;
        else els[i].textContent = val;
      }
    }
    var attrs = ['placeholder', 'alt', 'aria-label', 'title'];
    attrs.forEach(function (attr) {
      var nodes = document.querySelectorAll('[data-i18n-' + attr + ']');
      for (var j = 0; j < nodes.length; j++) {
        var k = nodes[j].getAttribute('data-i18n-' + attr);
        if (translations[k]) nodes[j].setAttribute(attr, translations[k]);
      }
    });

    var shotLang = CZECH_SCREENSHOTS.indexOf(current) !== -1 ? 'cs' : 'en';
    var shots = document.querySelectorAll('img[data-shot]');
    for (var s = 0; s < shots.length; s++) {
      var src = 'img/screenshots/' + shotLang + '/' + shots[s].getAttribute('data-shot') + '.webp';
      if (shots[s].getAttribute('src') !== src) shots[s].setAttribute('src', src);
    }

    document.documentElement.setAttribute('lang', current);
    var title = translations[document.body.getAttribute('data-title-key') || 'meta_title'];
    if (title) document.title = title;
    var desc = document.querySelector('meta[name="description"]');
    if (desc && translations.meta_description) desc.setAttribute('content', translations.meta_description);

    document.dispatchEvent(new CustomEvent('accbot:lang', { detail: { lang: current } }));
  }

  function setLang(lang) {
    if (!isLangHash(lang)) lang = DEFAULT_LANG;
    current = lang;
    try { localStorage.setItem('accbot-lang', lang); } catch (e) { /* storage blocked */ }

    // Keep the language in the URL so links can be shared, but never overwrite a section anchor.
    var hash = window.location.hash.replace('#', '');
    if (!hash || isLangHash(hash)) {
      history.replaceState(null, '', window.location.pathname + window.location.search + '#' + lang);
    }

    var select = document.getElementById('lang-select');
    if (select) select.value = lang;

    // English is the base: other languages are layered on top of it, so a missing key falls back to English.
    load(DEFAULT_LANG)
      .then(function (base) {
        if (lang === DEFAULT_LANG) return base;
        return load(lang)
          .then(function (tr) { return Object.assign({}, base, tr); })
          .catch(function () { return base; });
      })
      .then(apply);
  }

  function init() {
    var select = document.getElementById('lang-select');
    if (select) {
      select.addEventListener('change', function () { setLang(this.value); });
    }
    window.addEventListener('hashchange', function () {
      var hash = window.location.hash.replace('#', '');
      if (isLangHash(hash) && hash !== current) setLang(hash);
    });
    setLang(detect());
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.AccBotI18n = {
    setLang: setLang,
    current: function () { return current; },
    t: function (key) { return _tr[key] || key; }
  };
})();
