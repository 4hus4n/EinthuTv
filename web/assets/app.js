/* EinthuTv web — front end */
(function () {
  'use strict';

  var LANGS = ['malayalam', 'tamil', 'hindi', 'telugu', 'kannada', 'bengali', 'marathi', 'punjabi'];
  var THIS_YEAR = new Date().getFullYear();
  var SITE_PAGES_PER_SCREEN = 4;

  var view = document.getElementById('view');
  var state = {
    lang: load('lang', 'malayalam'),
    year: parseInt(load('year', '2026'), 10) || THIS_YEAR
  };
  if (LANGS.indexOf(state.lang) < 0) state.lang = 'malayalam';

  // ---------- Small helpers ----------

  function load(k, d) { try { return localStorage.getItem('einthutv.' + k) || d; } catch (e) { return d; } }
  function save(k, v) { try { localStorage.setItem('einthutv.' + k, v); } catch (e) {} }
  function langOf(m) { return (m && m.lang) || state.lang; }
  function cap(s) { return s.charAt(0).toUpperCase() + s.slice(1); }

  function el(tag, attrs, kids) {
    var n = document.createElement(tag);
    if (attrs) Object.keys(attrs).forEach(function (k) {
      var v = attrs[k];
      if (v == null || v === false) return;
      if (k === 'text') n.textContent = v;
      else if (k === 'class') n.className = v;
      else if (k.slice(0, 2) === 'on') n.addEventListener(k.slice(2), v);
      else n.setAttribute(k, v === true ? '' : v);
    });
    (kids || []).forEach(function (c) { if (c) n.appendChild(typeof c === 'string' ? document.createTextNode(c) : c); });
    return n;
  }

  function qs(o) {
    return Object.keys(o).filter(function (k) { return o[k] !== undefined && o[k] !== ''; })
      .map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(o[k]); }).join('&');
  }
  function parseQs(s) {
    var o = {};
    (s || '').split('&').forEach(function (p) {
      if (!p) return;
      var i = p.indexOf('=');
      o[decodeURIComponent(i < 0 ? p : p.slice(0, i))] = i < 0 ? '' : decodeURIComponent(p.slice(i + 1));
    });
    return o;
  }

  var chev = function () {
    var s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    s.setAttribute('viewBox', '0 0 24 24'); s.setAttribute('class', 'chev');
    s.innerHTML = '<path fill="currentColor" d="M8.6 16.6 13.2 12 8.6 7.4 10 6l6 6-6 6z"/>';
    return s;
  };

  // ---------- Data ----------

  var memo = {};
  function api(params) {
    var key = qs(params);
    if (memo[key]) return memo[key];
    var p = fetch('api.php?' + key, { headers: { Accept: 'application/json' } })
      .then(function (r) { return r.json().catch(function () { return { ok: false, error: 'Bad response from server' }; }); })
      .then(function (j) { if (!j.ok) throw new Error(j.error || 'Failed'); return j; });
    memo[key] = p;
    p.catch(function () { delete memo[key]; });
    return p;
  }

  var lists = {
    year: function (lang, y) { return { find: 'Year', lang: lang, year: y }; },
    trending: function (lang) { return { find: 'Popularity', lang: lang, ptype: 'view', tp: 'l30d' }; },
    recent: function (lang) { return { find: 'Recent', lang: lang }; },
    staff: function (lang) { return { find: 'StaffPick', lang: lang }; }
  };

  function titleFor(p) {
    var L = cap(p.lang || state.lang);
    switch (p.find) {
      case 'Year': return L + ' ' + p.year;
      case 'Decade': return L + ' ' + p.decade + 's';
      case 'Alphabets': return L + ' · ' + p.alpha;
      case 'Numbers': return L + ' · 0–9';
      case 'Recent': return 'Recently Added · ' + L;
      case 'StaffPick': return 'Staff Picks · ' + L;
      case 'RegionalHit': return 'Regional Hits · ' + L;
      case 'ComingSoon': return 'Coming Soon · ' + L;
      case 'Popularity':
        if (p.ptype === 'rating') return 'Top Rated · ' + L;
        if (p.ptype === 'comment') return 'Most Discussed · ' + L;
        return (p.tp === 'alltime' ? 'Most Watched · ' : 'Trending · ') + L;
      default: return L + ' Movies';
    }
  }

  function listHref(p) { return '#/list?' + qs(p); }

  // ---------- Pieces ----------

  function badges(m) {
    return el('span', { class: 'badges' }, [
      el('span', { class: 'badge', text: 'HD' }),
      m.subs ? el('span', { class: 'badge', text: 'CC' }) : null
    ]);
  }

  function posterEl(m) {
    var box = el('div', { class: 'poster' }, [el('div', { class: 'ph', text: m.title })]);
    if (m.poster && !/default-img/.test(m.poster)) {
      var img = el('img', { src: m.poster, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer' });
      img.addEventListener('error', function () { img.remove(); });
      box.appendChild(img);
    }
    return box;
  }

  function card(m, onFocus) {
    var c = el('button', { class: 'card', type: 'button', onclick: function () { openSheet(m); } }, [
      posterEl(m),
      el('h3', { text: m.title }),
      el('div', { class: 'sub', text: [m.year, cap(langOf(m))].filter(Boolean).join('  •  ') }),
      badges(m)
    ]);
    if (onFocus) {
      c.addEventListener('focus', function () { onFocus(m); });
      c.addEventListener('mouseenter', function () { onFocus(m); });
    }
    return c;
  }

  function seeAllCard(href) {
    return el('a', { class: 'card see-all', href: href }, [
      el('div', { class: 'poster' }, [chev(), el('span', { text: 'See all' })])
    ]);
  }

  function skeletons(n) {
    var out = [];
    for (var i = 0; i < n; i++) out.push(el('div', { class: 'card skel' }, [el('div', { class: 'poster' }), el('h3'), el('div', { class: 'sub' })]));
    return out;
  }

  function errorBox(msg, retry) {
    return el('div', { class: 'msg' }, [
      el('div', { text: msg }),
      retry ? el('button', { class: 'btn btn-outline', type: 'button', text: 'Try again', onclick: retry }) : null
    ]);
  }

  function selectBox(label, value, options, onChange) {
    var s = el('select', { 'aria-label': label, onchange: function () { onChange(s.value); } },
      options.map(function (o) { return el('option', { value: o[0], text: o[1], selected: String(o[0]) === String(value) }); }));
    return el('label', { class: 'select' }, [el('small', { text: label }), s]);
  }

  function langSelect(onChange) {
    return selectBox('Language', state.lang, LANGS.map(function (l) { return [l, cap(l)]; }), function (v) {
      state.lang = v; save('lang', v); onChange();
    });
  }

  function yearSelect(onChange) {
    var ys = [];
    for (var y = THIS_YEAR; y >= 1961; y--) ys.push([y, String(y)]);
    return selectBox('Year', state.year, ys, function (v) {
      state.year = parseInt(v, 10); save('year', v); onChange();
    });
  }

  // ---------- Home ----------

  // ---------- Hero carousel (trending, auto-rotating) ----------

  var heroTimer = null;
  function stopHero() { if (heroTimer) { clearInterval(heroTimer); heroTimer = null; } }

  function heroCarousel() {
    stopHero();
    var L = cap(state.lang);
    var heroBg = el('div', { class: 'hero-bg' });
    var heroPoster = el('div', { class: 'hero-poster' });
    var eyebrow = el('div', { class: 'hero-eyebrow', text: 'Trending in ' + L });
    var heroTitle = el('h1', { text: 'Loading…' });
    var heroMeta = el('div', { class: 'meta' });
    var heroDesc = el('p');
    var dots = el('div', { class: 'hero-dots', role: 'tablist', 'aria-label': 'Trending movies' });
    var inner = el('div', { class: 'hero-inner' });
    var slides = [], idx = 0, current = null, paused = false, resumeAt = 0;

    var playBtn = el('button', { class: 'btn btn-primary', type: 'button', text: '▶  Play',
      onclick: function () { if (current) openSheet(current); } });

    function paint(m) {
      current = m;
      heroTitle.textContent = m.title;
      heroMeta.textContent = '';
      heroMeta.appendChild(el('span', { text: [m.year, L].filter(Boolean).join('  •  ') }));
      heroMeta.appendChild(badges(m));
      heroDesc.textContent = m.synopsis || '';
      var bg = m.poster && !/default-img/.test(m.poster) ? 'url("' + m.poster.replace(/"/g, '%22') + '")' : 'none';
      heroBg.style.backgroundImage = bg;
      heroPoster.style.backgroundImage = bg;
      Array.prototype.forEach.call(dots.children, function (d, i) {
        d.classList.toggle('on', i === idx);
        d.setAttribute('aria-selected', i === idx ? 'true' : 'false');
      });
    }

    function go(i, instant) {
      if (!slides.length) return;
      idx = (i + slides.length) % slides.length;
      var m = slides[idx];
      if (instant) { paint(m); return; }
      inner.classList.add('fading'); heroBg.classList.add('fading');
      setTimeout(function () {
        paint(m);
        inner.classList.remove('fading'); heroBg.classList.remove('fading');
      }, 260);
      // Preload the next poster so the change is smooth
      var nx = slides[(idx + 1) % slides.length];
      if (nx && nx.poster) { var im = new Image(); im.referrerPolicy = 'no-referrer'; im.src = nx.poster; }
    }

    function tick() {
      if (!document.body.contains(section)) { stopHero(); return; }
      if (paused || Date.now() < resumeAt || !sheet.hidden || document.hidden) return;
      go(idx + 1);
    }
    function nudge() { resumeAt = Date.now() + 8000; }

    var section = el('section', { class: 'hero' }, [heroBg, inner]);
    inner.appendChild(el('div', { class: 'hero-text' }, [
      eyebrow, heroTitle, heroMeta, heroDesc,
      el('div', { class: 'actions' }, [
        playBtn,
        el('a', { class: 'btn btn-outline', href: listHref(lists.trending(state.lang)), text: 'See all' })
      ]),
      dots
    ]));
    inner.appendChild(heroPoster);

    // Pause while the user is pointing at / focused in the hero
    section.addEventListener('mouseenter', function () { paused = true; });
    section.addEventListener('mouseleave', function () { paused = false; nudge(); });
    section.addEventListener('focusin', function () { paused = true; });
    section.addEventListener('focusout', function () { paused = false; nudge(); });

    // Swipe on phones
    var tx = null, ty = null;
    section.addEventListener('touchstart', function (e) { tx = e.touches[0].clientX; ty = e.touches[0].clientY; }, { passive: true });
    section.addEventListener('touchend', function (e) {
      if (tx == null) return;
      var dx = e.changedTouches[0].clientX - tx, dy = e.changedTouches[0].clientY - ty;
      tx = null;
      if (Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy) * 1.3) { go(idx + (dx < 0 ? 1 : -1)); nudge(); }
    }, { passive: true });

    api(Object.assign({ page: 1, pages: 2 }, lists.trending(state.lang))).then(function (j) {
      slides = j.movies.filter(function (m) { return m.poster && !/default-img/.test(m.poster); }).slice(0, 8);
      if (!slides.length) slides = j.movies.slice(0, 8);
      if (!slides.length) { heroTitle.textContent = 'No trending movies'; return; }
      dots.textContent = '';
      slides.forEach(function (m, i) {
        dots.appendChild(el('button', { class: 'dot', type: 'button', role: 'tab', 'aria-label': m.title,
          onclick: function () { go(i); nudge(); } }));
      });
      go(0, true);
      heroTimer = setInterval(tick, 6000);
    }).catch(function () {
      heroTitle.textContent = 'Couldn’t load trending movies';
    });

    return section;
  }

  // ---------- Home ----------

  function renderHome() {
    view.textContent = '';
    var L = cap(state.lang);

    view.appendChild(el('div', { class: 'filters' }, [langSelect(renderHome), yearSelect(renderHome)]));
    view.appendChild(heroCarousel());

    var rows = [
      { title: 'New in ' + L + ' ' + state.year, p: lists.year(state.lang, state.year), pages: 3, main: true },
      { title: 'Trending in ' + L, p: lists.trending(state.lang), pages: 2 },
      { title: 'Recently Added', p: lists.recent(state.lang), pages: 2 },
      { title: 'Staff Picks', p: lists.staff(state.lang), pages: 1 }
    ];

    rows.forEach(function (r) {
      var strip = el('div', { class: 'strip' }, skeletons(7));
      var sec = el('section', { class: 'row' }, [
        el('div', { class: 'row-head' }, [el('a', { href: listHref(r.p) }, [el('h2', { text: r.title }), chev()])]),
        strip
      ]);
      view.appendChild(sec);

      function run() {
        strip.textContent = '';
        skeletons(7).forEach(function (s) { strip.appendChild(s); });
        api(Object.assign({ page: 1, pages: r.pages }, r.p)).then(function (j) {
          strip.textContent = '';
          if (!j.movies.length) {
            if (r.main) strip.appendChild(errorBox('No movies in this list yet.'));
            else sec.remove();
            return;
          }
          j.movies.forEach(function (m) { strip.appendChild(card(m)); });
          strip.appendChild(seeAllCard(listHref(r.p)));
        }).catch(function (e) {
          strip.textContent = '';
          if (r.main) strip.appendChild(errorBox(e.message, run));
          else sec.remove();
        });
      }
      run();
    });
  }

  // ---------- Grid (Movies / See all) ----------

  function renderList(p) {
    view.textContent = '';
    p.lang = p.lang || state.lang;
    var screen = Math.max(1, parseInt(p.screen || '1', 10));
    delete p.screen;

    var grid = el('div', { class: 'grid' }, skeletons(12));
    var prev = el('button', { class: 'btn btn-outline', type: 'button', text: '‹ Prev', disabled: screen <= 1,
      onclick: function () { go(screen - 1); } });
    var next = el('button', { class: 'btn btn-outline', type: 'button', text: 'Next ›', disabled: true,
      onclick: function () { go(screen + 1); } });

    function go(n) { location.hash = '#/list?' + qs(Object.assign({}, p, { screen: n })); }

    view.appendChild(heroCarousel());
    view.appendChild(el('div', { class: 'page-head' }, [
      el('h1', { text: titleFor(p) }),
      el('div', { class: 'pager' }, [prev, el('span', { text: 'Page ' + screen }), next])
    ]));
    view.appendChild(grid);

    function run() {
      grid.textContent = '';
      skeletons(12).forEach(function (s) { grid.appendChild(s); });
      var first = (screen - 1) * SITE_PAGES_PER_SCREEN + 1;
      api(Object.assign({}, p, { page: first, pages: SITE_PAGES_PER_SCREEN })).then(function (j) {
        grid.textContent = '';
        if (!j.movies.length) {
          grid.replaceWith(errorBox(screen > 1 ? 'That’s the end of this list.' : 'No movies found here.'));
          return;
        }
        j.movies.forEach(function (m) { grid.appendChild(card(m)); });
        next.disabled = !j.hasMore;
        var f = grid.querySelector('.card'); if (f && document.activeElement === document.body) f.focus({ preventScroll: true });
      }).catch(function (e) {
        grid.textContent = '';
        grid.appendChild(errorBox(e.message, run));
      });
    }
    run();
  }

  // ---------- Browse ----------

  function renderBrowse() {
    view.textContent = '';
    var lang = state.lang;
    view.appendChild(heroCarousel());
    view.appendChild(el('div', { class: 'page-head' }, [el('h1', { text: 'Browse' })]));
    view.appendChild(el('div', { class: 'filters' }, [langSelect(renderBrowse)]));

    function section(title, items) {
      view.appendChild(el('section', { class: 'row' }, [
        el('div', { class: 'row-head' }, [el('h2', { text: title })]),
        el('div', { class: 'chips' }, items.map(function (it) {
          return el('a', { class: 'chip' + (it.ext ? ' ext' : ''), href: it.href, text: it.label,
            target: it.ext ? '_blank' : null, rel: it.ext ? 'noopener' : null });
        }))
      ]));
    }
    function L(label, p) { return { label: label, href: listHref(Object.assign({ lang: lang }, p)) }; }

    section('Lists', [
      L('Recently Added', { find: 'Recent' }),
      L('Trending', { find: 'Popularity', ptype: 'view', tp: 'l30d' }),
      L('Most Watched', { find: 'Popularity', ptype: 'view', tp: 'alltime' }),
      L('Top Rated', { find: 'Popularity', ptype: 'rating', tp: 'alltime' }),
      L('Staff Picks', { find: 'StaffPick' }),
      L('Regional Hits', { find: 'RegionalHit' }),
      L('Coming Soon', { find: 'ComingSoon' })
    ]);
    var years = [];
    for (var y = THIS_YEAR; y >= 1961; y--) years.push(L(String(y), { find: 'Year', year: y }));
    section('Years', years);
    section('Decades', [2020, 2010, 2000, 1990, 1980, 1970, 1960].map(function (d) {
      return L(d + 's', { find: 'Decade', decade: d });
    }));
    var az = [L('0–9', { find: 'Numbers' })];
    for (var c = 65; c <= 90; c++) az.push(L(String.fromCharCode(c), { find: 'Alphabets', alpha: String.fromCharCode(c) }));
    section('A – Z', az);
    section('Search & more', [
      { label: 'Search / Cast on Einthusan', href: 'https://einthusan.tv/movie/browse/?lang=' + lang, ext: true }
    ]);
  }

  // ---------- Search (all languages) ----------

  function renderSearch(q) {
    view.textContent = '';
    q = (q || '').trim();
    var input = el('input', { type: 'search', class: 'search-input', placeholder: 'Movie name…',
      value: q, 'aria-label': 'Search movies', autocomplete: 'off', enterkeyhint: 'search' });
    var form = el('form', { class: 'search-form', role: 'search', onsubmit: function (e) {
      e.preventDefault();
      var v = input.value.trim();
      if (v.length < 2) { status.textContent = 'Type at least 2 letters.'; return; }
      location.hash = '#/search?' + qs({ q: v });
    } }, [input, el('button', { class: 'btn btn-primary', type: 'submit', text: 'Search' })]);
    var status = el('div', { class: 'search-status' });
    var results = el('div');

    view.appendChild(el('div', { class: 'page-head' }, [el('h1', { text: 'Search all languages' })]));
    view.appendChild(form);
    view.appendChild(status);
    view.appendChild(results);

    if (q.length < 2) { setTimeout(function () { input.focus(); }, 50); return; }

    status.textContent = 'Searching all languages for “' + q + '”…';
    api({ action: 'search', q: q, lang: state.lang }).then(function (j) {
      var total = 0;
      j.groups.forEach(function (g) {
        if (!g.movies.length) return;
        total += g.movies.length;
        var strip = el('div', { class: 'strip' });
        g.movies.forEach(function (m) { m.lang = g.lang; strip.appendChild(card(m)); });
        results.appendChild(el('section', { class: 'row' }, [
          el('div', { class: 'row-head' }, [el('h2', { text: cap(g.lang) + ' · ' + g.movies.length })]),
          strip
        ]));
      });
      status.textContent = total
        ? total + ' movies found for “' + q + '”'
        : 'No movies found for “' + q + '”. Try a shorter name or different spelling.';
    }).catch(function (e) {
      status.textContent = '';
      results.appendChild(errorBox(e.message, function () { renderSearch(q); }));
    });
  }

  // ---------- Movie sheet ----------

  var sheet = document.getElementById('sheet');
  var lastFocus = null;

  function openSheet(m) {
    lastFocus = document.activeElement;
    var img = document.getElementById('sheetPoster');
    img.hidden = !(m.poster && !/default-img/.test(m.poster));
    if (!img.hidden) img.src = m.poster;
    document.getElementById('sheetTitle').textContent = m.title;
    document.getElementById('sheetMeta').textContent = [m.year, cap(langOf(m))].filter(Boolean).join('  •  ');
    var b = document.getElementById('sheetBadges');
    b.textContent = ''; b.appendChild(badges(m));
    document.getElementById('sheetDesc').textContent = m.synopsis || '';
    var w = document.getElementById('sheetWatch');
    w.href = m.url;
    w.textContent = '▶  Watch in ' + cap(langOf(m));
    sheet.hidden = false;
    document.body.style.overflow = 'hidden';
    w.focus();
    loadVersions(m);
  }

  // Same film in other languages (dubbed versions), found via its director / IMDb id
  var versionsToken = 0;
  function loadVersions(m) {
    var box = document.getElementById('sheetLangs');
    var token = ++versionsToken;
    box.textContent = '';
    if (!m.director && !m.lead) {
      box.appendChild(el('span', { class: 'versions-wait', text: '' }));
      box.parentNode.hidden = true;
      return;
    }
    box.parentNode.hidden = false;
    box.appendChild(el('span', { class: 'versions-wait', text: 'Checking other languages…' }));
    api({
      action: 'versions', lang: langOf(m), url: m.url, year: m.year, imdb: m.imdb,
      director: m.director, lead: m.lead, leadRole: m.leadRole
    }).then(function (j) {
      if (token !== versionsToken) return;
      box.textContent = '';
      if (!j.versions.length) {
        box.appendChild(el('span', { class: 'versions-wait-none', text: 'Only in ' + cap(langOf(m)) }));
        return;
      }
      j.versions.forEach(function (v) {
        box.appendChild(el('a', { class: 'lang-btn', href: v.url, target: '_blank', rel: 'noopener',
          title: v.title, onclick: function () { setTimeout(closeSheet, 300); } }, [
          '▶ ' + cap(v.lang),
          v.title && v.title.toLowerCase() !== m.title.toLowerCase() ? el('small', { text: v.title }) : null
        ]));
      });
    }).catch(function () {
      if (token !== versionsToken) return;
      box.textContent = '';
      box.appendChild(el('span', { class: 'versions-wait-none', text: 'Couldn’t check other languages' }));
    });
  }

  function closeSheet() {
    if (sheet.hidden) return;
    sheet.hidden = true;
    document.body.style.overflow = '';
    if (lastFocus && lastFocus.focus) lastFocus.focus({ preventScroll: true });
  }

  sheet.addEventListener('click', function (e) { if (e.target.closest('[data-close]')) closeSheet(); });
  document.getElementById('sheetWatch').addEventListener('click', function () { setTimeout(closeSheet, 300); });

  // ---------- Remote / arrow-key navigation (TV browsers) ----------

  function focusables() {
    var root = sheet.hidden ? document : sheet;
    return Array.prototype.filter.call(
      root.querySelectorAll('a[href], button:not([disabled]), select'),
      function (n) { var r = n.getBoundingClientRect(); return r.width > 0 && r.height > 0 && !n.closest('[hidden]'); });
  }

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' || e.key === 'Backspace' && e.target.tagName !== 'INPUT') {
      if (!sheet.hidden) { e.preventDefault(); closeSheet(); return; }
    }
    var dir = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[e.key];
    if (!dir) return;
    var cur = document.activeElement;
    if (cur && cur.tagName === 'SELECT') return;
    if (!cur || cur === document.body) {
      var f = focusables()[0]; if (f) { e.preventDefault(); f.focus(); }
      return;
    }
    var a = cur.getBoundingClientRect();
    var ax = a.left + a.width / 2, ay = a.top + a.height / 2;
    var best = null, bestScore = Infinity;
    focusables().forEach(function (n) {
      if (n === cur) return;
      var r = n.getBoundingClientRect();
      var bx = r.left + r.width / 2, by = r.top + r.height / 2;
      var dx = bx - ax, dy = by - ay;
      var along = dx * dir[0] + dy * dir[1];
      if (along <= 4) return;
      var across = Math.abs(dir[0] ? dy : dx);
      var score = along + across * 2.5;
      if (score < bestScore) { bestScore = score; best = n; }
    });
    if (best) {
      e.preventDefault();
      best.focus({ preventScroll: true });
      best.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
    }
  });

  // ---------- Router ----------

  function route() {
    closeSheet();
    stopHero();
    var h = location.hash.replace(/^#/, '') || '/';
    var path = h.split('?')[0];
    var params = parseQs(h.split('?')[1]);
    var tab = 'home';
    window.scrollTo(0, 0);

    if (path === '/movies') { tab = 'movies'; renderList(lists.year(state.lang, state.year)); }
    else if (path === '/list') { tab = 'movies'; renderList(params); }
    else if (path === '/browse') { tab = 'browse'; renderBrowse(); }
    else if (path === '/search') { tab = 'search'; renderSearch(params.q); }
    else renderHome();

    document.querySelectorAll('.tabs a').forEach(function (a) { a.classList.toggle('on', a.getAttribute('data-tab') === tab); });
  }

  window.addEventListener('hashchange', route);
  route();
})();
