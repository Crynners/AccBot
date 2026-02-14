(function () {
  'use strict';

  var DEFAULT_LANG = 'en';
  var SUPPORTED = ['en', 'cs'];
  var current = DEFAULT_LANG;

  function detect() {
    var hash = window.location.hash.replace('#', '');
    if (SUPPORTED.indexOf(hash) !== -1) return hash;
    var saved = localStorage.getItem('accbot-lang');
    if (saved && SUPPORTED.indexOf(saved) !== -1) return saved;
    var nav = (navigator.language || '').slice(0, 2);
    if (SUPPORTED.indexOf(nav) !== -1) return nav;
    return DEFAULT_LANG;
  }

  function apply(translations) {
    var els = document.querySelectorAll('[data-i18n]');
    for (var i = 0; i < els.length; i++) {
      var key = els[i].getAttribute('data-i18n');
      if (translations[key]) {
        els[i].textContent = translations[key];
      }
    }
    var placeholders = document.querySelectorAll('[data-i18n-placeholder]');
    for (var j = 0; j < placeholders.length; j++) {
      var pk = placeholders[j].getAttribute('data-i18n-placeholder');
      if (translations[pk]) {
        placeholders[j].setAttribute('placeholder', translations[pk]);
      }
    }
    document.documentElement.setAttribute('lang', current);
    if (translations.meta_title) {
      document.title = translations.meta_title;
    }
  }

  function setLang(lang) {
    if (SUPPORTED.indexOf(lang) === -1) lang = DEFAULT_LANG;
    current = lang;
    localStorage.setItem('accbot-lang', lang);
    window.location.hash = lang;

    var btns = document.querySelectorAll('.lang-btn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].classList.toggle('active', btns[i].getAttribute('data-lang') === lang);
    }

    if (lang === DEFAULT_LANG) {
      fetch('locales/en.json').then(function (r) { return r.json(); }).then(apply);
      return;
    }

    fetch('locales/' + lang + '.json')
      .then(function (r) { return r.json(); })
      .then(apply)
      .catch(function () {
        fetch('locales/en.json').then(function (r) { return r.json(); }).then(apply);
      });
  }

  function init() {
    var btns = document.querySelectorAll('.lang-btn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].addEventListener('click', function (e) {
        e.preventDefault();
        setLang(this.getAttribute('data-lang'));
      });
    }
    var lang = detect();
    setLang(lang);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.AccBotI18n = { setLang: setLang, current: function () { return current; } };
})();
