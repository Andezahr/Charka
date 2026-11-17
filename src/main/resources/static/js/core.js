// Charka, часть фронта «core» — запросы (CSRF, срок 20 с), журнал ошибок скрипта, окна поверх свёрнутого, бросок «катится».
// ARC-02: обычный скрипт (не модуль) — части делят одну глобальную область и грузятся по порядку
// (fragments/layout.html, defer): объявления видны всем частям, вызовы идут из обработчиков после загрузки всех.

// Общий JS для всех страниц Charka

// U-03: CSRF. Сервер кладёт токен в cookie XSRF-TOKEN; каждый запрос, кроме чтения, несёт его заголовком —
// формы обновления на месте, ход фишки, автосохранение карты
// A-14: у каждого запроса срок — 20 с (файл — 60 с). Без него оборвавшийся на телефоне запрос висит вечно:
// формы ждут его и не отправляются, опрос хода и ленты останавливается — «приложение перестало отвечать»
const REQUEST_TIMEOUT = 20000;
const UPLOAD_TIMEOUT = 60000;
(() => {
  const plain = window.fetch.bind(window);
  const bounded = (input, init) => {
    if (init.signal || init.keepalive) return plain(input, init);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), init.body instanceof FormData ? UPLOAD_TIMEOUT : REQUEST_TIMEOUT);
    return plain(input, { ...init, signal: controller.signal }).finally(() => clearTimeout(timer));
  };
  window.fetch = (input, init = {}) => {
    const method = (init.method || 'GET').toUpperCase();
    if (method === 'GET' || method === 'HEAD') return bounded(input, init);
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    if (!match) return bounded(input, init);
    const headers = new Headers(init.headers || {});
    headers.set('X-XSRF-TOKEN', decodeURIComponent(match[1]));
    return bounded(input, { ...init, headers });
  };
})();

// A-14: ошибки скрипта уходят в журнал сервера — по ним видно, что ломается у игроков; не больше 5 за страницу
(() => {
  let sent = 0;
  const report = (message, stack) => {
    if (sent++ >= 5) return;
    try {
      fetch('/client-error', { method: 'POST', keepalive: true, body: new URLSearchParams({
        page: location.pathname + location.search, message: String(message).slice(0, 500), stack: String(stack || '').slice(0, 2000) }) })
        .catch(() => { /* сервер недоступен — некуда писать */ });
    } catch (e) { /* только журнал */ }
  };
  window.addEventListener('error', e => report(e.message, e.error && e.error.stack));
  window.addEventListener('unhandledrejection', e => report(e.reason && e.reason.message || e.reason, e.reason && e.reason.stack));
  window.reportClientError = report;
})();

// KF-06: окно внутри свёрнутого <details> или скрытой вкладки не рисуется, а showModal всё равно делает страницу
// неотзывчивой — «экран боя завис, помогает только перезагрузка» (диалог атаки лежит в свёрнутом Char Info).
// BF-01: раскрыть предков мало — панель «Действия вне очереди» — меню (details[data-menu]), и то же нажатие,
// что открыло окно, сворачивает её обратно («нажатие мимо»). Поэтому окно из свёрнутого или скрытого места
// на время переезжает в <body> и возвращается на место, когда закрыто
(() => {
  const show = HTMLDialogElement.prototype.showModal;
  HTMLDialogElement.prototype.showModal = function () {
    const parent = this.parentElement;
    let folded = false;
    for (let node = parent; node; node = node.parentElement) {
      if (node.tagName === 'DETAILS' && !node.open) folded = true;
    }
    const hidden = parent && parent !== document.body && !parent.getClientRects().length && getComputedStyle(parent).display !== 'contents';
    if (parent && parent !== document.body && (folded || hidden)) {
      const placeholder = document.createComment('dialog');
      this.replaceWith(placeholder);
      document.body.appendChild(this);
      this.addEventListener('close', () => {
        if (placeholder.isConnected) placeholder.replaceWith(this);
      }, { once: true });
    }
    return show.call(this);
  };
})();

// Обработчики элементов страницы: вызывается при загрузке и после каждого обновления на месте.
// Обработчики на document сюда не класть, они накопятся: они в конце файла
// J-02: бросок «катится» — число быстро меняется, кубик крутится, затем встаёт с толчком; итог и фишки проявляются после.
// Около 0,7 с: ощутимо, но не задерживает стол. Новая запись ленты катится один раз (ключи в sessionStorage), итог
// атаки в строке ([data-roll-now]) — всегда: он приходит только в ответ на действие
const ROLL_MS = 700;
const FEED_SEEN_KEY = 'charka.feed.seen';

function rollAnimation(holder, own) {
  const roll = holder.matches('[data-roll-anim]') ? holder : holder.querySelector('[data-roll-anim]');
  if (!roll || roll.dataset.rolling) return;
  const value = roll.querySelector('[data-roll-value]') || roll;
  const final = Number(roll.dataset.final);
  if (!Number.isFinite(final)) return;
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  roll.dataset.rolling = '1';
  const low = Math.max(1, final - 9);
  const high = Math.max(final + 9, 10);
  const start = performance.now();
  holder.classList.add('is-rolling');
  holder.classList.remove('is-landed');
  let next = 0;
  const tick = now => {
    const t = (now - start) / ROLL_MS;
    if (t >= 1) {
      value.textContent = final;
      holder.classList.remove('is-rolling');
      // перезапуск анимации толчка
      void holder.offsetWidth;
      holder.classList.add('is-landed');
      delete roll.dataset.rolling;
      if (own && navigator.vibrate) navigator.vibrate(holder.querySelector('.jchip-crit') ? [30, 40, 60] : 25);
      return;
    }
    // замедляется к концу, как кубик
    if (now >= next) {
      let shown;
      do { shown = low + Math.floor(Math.random() * (high - low + 1)); } while (shown === Number(value.textContent) && high > low);
      value.textContent = shown;
      next = now + 40 + 160 * t * t;
    }
    requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

// KF-03: итог действия катится один раз — опрос ленты зовёт revealRolls снова, и без метки кубик крутился дважды
function revealRolls() {
  document.querySelectorAll('[data-roll-now]:not([data-rolled])').forEach(holder => {
    holder.dataset.rolled = '1';
    rollAnimation(holder, true);
  });
  const rows = [...document.querySelectorAll('[data-feed-key]')];
  if (!rows.length) return;
  let stored = null;
  try { stored = JSON.parse(sessionStorage.getItem(FEED_SEEN_KEY) || 'null'); } catch (e) { /* только удобство */ }
  // первый взгляд на ленту — история не катится
  const first = stored === null;
  const seen = new Set(stored || []);
  rows.forEach(row => {
    const key = row.dataset.feedKey;
    if (seen.has(key)) return;
    seen.add(key);
    if (!first) {
      rollAnimation(row, false);
      loudRoll(row);
    }
  });
  try { sessionStorage.setItem(FEED_SEEN_KEY, JSON.stringify([...seen].slice(-400))); } catch (e) { /* только удобство */ }
}
