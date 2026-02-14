(function () {
  'use strict';

  // Scroll-reveal animation with IntersectionObserver
  function initScrollReveal() {
    var els = document.querySelectorAll('.reveal');
    if (!('IntersectionObserver' in window)) {
      for (var i = 0; i < els.length; i++) els[i].classList.add('visible');
      return;
    }
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.1 });
    els.forEach(function (el) { observer.observe(el); });
  }

  // Smooth scroll for nav links
  function initSmoothScroll() {
    var links = document.querySelectorAll('a[href^="#"]');
    links.forEach(function (link) {
      link.addEventListener('click', function (e) {
        var id = this.getAttribute('href');
        if (id.length <= 1) return;
        var target = document.querySelector(id);
        if (!target) return;
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth' });
        // Close mobile nav
        var nav = document.querySelector('.nav-links');
        if (nav) nav.classList.remove('open');
        var burger = document.querySelector('.burger');
        if (burger) burger.classList.remove('open');
      });
    });
  }

  // Mobile hamburger menu
  function initBurger() {
    var burger = document.querySelector('.burger');
    var nav = document.querySelector('.nav-links');
    if (!burger || !nav) return;
    burger.addEventListener('click', function () {
      nav.classList.toggle('open');
      burger.classList.toggle('open');
    });
  }

  // Header background on scroll
  function initHeaderScroll() {
    var header = document.querySelector('.site-header');
    if (!header) return;
    window.addEventListener('scroll', function () {
      if (window.scrollY > 40) {
        header.classList.add('scrolled');
      } else {
        header.classList.remove('scrolled');
      }
    }, { passive: true });
  }

  // Active nav section highlighting
  function initActiveNav() {
    var sections = document.querySelectorAll('section[id]');
    var navLinks = document.querySelectorAll('.nav-links a[href^="#"]');
    if (!sections.length || !navLinks.length) return;
    if (!('IntersectionObserver' in window)) return;
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          var id = entry.target.getAttribute('id');
          navLinks.forEach(function (link) {
            link.classList.toggle('active', link.getAttribute('href') === '#' + id);
          });
        }
      });
    }, { rootMargin: '-20% 0px -70% 0px' });
    sections.forEach(function (s) { observer.observe(s); });
  }

  function init() {
    initScrollReveal();
    initSmoothScroll();
    initBurger();
    initHeaderScroll();
    initActiveNav();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
