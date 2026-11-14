// Charka, часть фронта «navigation» — обновление страницы на месте, запуск.
// ARC-02: обычный скрипт (не модуль) — части делят одну глобальную область и грузятся по порядку
// (fragments/layout.html, defer): объявления видны всем частям, вызовы идут из обработчиков после загрузки всех.

// Обновление страницы на месте: формы и ссылки идут через fetch, новая страница заменяет старую без мигания,
// прокрутка остаётся. Сервер отвечает обычным HTML, на редирект — адресом с якорем (InPlaceRedirectFilter).
// Без JS всё работает обычными переходами
const IN_PLACE_HEADER = 'X-Charka-In-Place';
const LOCATION_HEADER = 'X-Charka-Location';
let lastVisit = 0;
let submitting = false;

// Страница с якорем: заголовок нужной строки на экране — прокрутка не нужна, иначе переходим к ней
const revealAnchor = () => {
  const anchored = location.hash && document.getElementById(location.hash.slice(1));
  if (!anchored) return;
  // строка в свёрнутом блоке (запасное оружие, R-07): раскрыть, иначе её не видно
  for (let details = anchored.closest('details'); details; details = details.parentElement.closest('details')) details.open = true;
  const top = anchored.getBoundingClientRect().top;
  const topbar = document.querySelector('.topbar');
  if (top < (topbar ? topbar.offsetHeight : 0) || top > innerHeight - 80) anchored.scrollIntoView();
};

// Раскрытые блоки переживают обновление. Меню и всё внутри диалогов закрываются, как после перехода
const openableDetails = () => [...document.querySelectorAll('details:not([data-menu])')]
  .filter(details => !details.closest('dialog'));
const detailsKey = details => {
  const holder = details.parentElement.closest('[id]');
  const inHolder = [...(holder || document).querySelectorAll('details:not([data-menu])')];
  return (holder ? holder.id : '') + '/' + inHolder.indexOf(details);
};

const replacePage = html => {
  const next = new DOMParser().parseFromString(html, 'text/html');
  const opened = new Set(openableDetails().filter(details => details.open).map(detailsKey));
  document.title = next.title;
  document.body.replaceWith(next.body);
  openableDetails().filter(details => opened.has(detailsKey(details))).forEach(details => { details.open = true; });
  safely('initPage', initPage);
};

const fetchPage = async (url, init) => {
  const response = await fetch(url, { cache: 'no-cache', ...init, headers: { [IN_PLACE_HEADER]: '1' } });
  const redirect = response.headers.get(LOCATION_HEADER);
  if (redirect) return fetchPage(new URL(redirect, location.href).href);
  return { response, url };
};

// Сервер недоступен: действие могло не выполниться, страница и введённое остаются
const showConnectionError = timedOut => {
  const alert = document.createElement('div');
  alert.className = 'alert alert-error';
  alert.setAttribute('role', 'alert');
  const text = document.createElement('span');
  text.className = 'alert-text';
  text.textContent = timedOut
    ? 'Сервер не ответил за 20 секунд. Проверьте, прошло ли действие, и повторите.'
    : 'Нет связи с сервером. Проверьте, прошло ли действие, и повторите.';
  const close = document.createElement('button');
  close.type = 'button';
  close.className = 'alert-close';
  close.setAttribute('aria-label', 'Закрыть');
  close.textContent = '×';
  alert.append(text, close);
  (document.querySelector('main') || document.body).prepend(alert);
};

// KF-06: ответ-ошибка на действие: текст причины со страницы ошибки (или текстом), в открытом окне — внутри него
// (поверх модального окна ничего не видно), иначе — плашкой внизу экрана, которая не прячет страницу
const showActionError = (status, body, pressed) => {
  let message = '';
  try {
    const page = new DOMParser().parseFromString(body, 'text/html');
    const sub = page.querySelector('.page-sub');
    message = sub ? sub.textContent.trim() : (page.querySelector('.page-title') ? '' : page.body.textContent.trim().slice(0, 300));
  } catch (e) { /* без текста */ }
  const title = status === 404 ? 'Не найдено' : status === 403 ? 'Нет доступа' : status >= 500 ? 'Ошибка сервера' : 'Действие не выполнено';
  const alert = document.createElement('div');
  alert.className = 'alert alert-error action-error';
  alert.setAttribute('role', 'alert');
  const text = document.createElement('span');
  text.className = 'alert-text';
  text.textContent = title + (message ? ': ' + message : '') + (status >= 500 ? '. Обновите страницу и проверьте, что изменилось.' : '');
  const close = document.createElement('button');
  close.type = 'button';
  close.className = 'alert-close';
  close.setAttribute('aria-label', 'Закрыть');
  close.textContent = '×';
  close.addEventListener('click', () => alert.remove());
  alert.append(text, close);
  const dialog = pressed && pressed.closest && pressed.closest('dialog[open]');
  if (dialog) {
    dialog.querySelectorAll('.action-error').forEach(old => old.remove());
    dialog.prepend(alert);
    return;
  }
  document.querySelectorAll('.action-error.is-floating').forEach(old => old.remove());
  alert.classList.add('is-floating');
  document.body.appendChild(alert);
};

// scroll — куда вернуться при ходьбе по истории; без него та же страница остаётся на месте, новая открывается сверху.
// pressed — нажатая кнопка: её строка остаётся под пальцем, даже если сообщение над ней исчезло или добавилось
const visit = async (url, init, scroll, pressed) => {
  const ticket = ++lastVisit;
  const pressedRow = pressed && pressed.closest('[id]');
  const pressedId = pressedRow && pressedRow.id;
  try {
    const page = await fetchPage(url, init);
    // пока шёл ответ, нажали другую ссылку
    if (ticket !== lastVisit) return;
    if (!(page.response.headers.get('Content-Type') || '').includes('text/html')) {
      location.assign(page.url);
      return;
    }
    const html = await page.response.text();
    if (ticket !== lastVisit) return;
    // KF-06: действие не прошло — страница (и бой на ней) остаётся, причина видна там, где нажимали
    if (!page.response.ok && init && init.method === 'POST') {
      showActionError(page.response.status, html, pressed);
      return;
    }
    const target = new URL(page.url, location.href);
    const samePage = target.pathname + target.search === location.pathname + location.search;
    const x = scrollX;
    const y = scrollY;
    const pressedTop = pressedRow && pressedRow.isConnected ? pressedRow.getBoundingClientRect().top : null;
    // страница ошибки адрес не меняет: форма осталась на прежней
    if (page.response.ok && scroll === undefined) {
      if (samePage) {
        history.replaceState(null, '', target.href);
      } else {
        history.replaceState({ scroll: y }, '');
        history.pushState(null, '', target.href);
      }
    }
    replacePage(html);
    if (scroll !== undefined) scrollTo(0, scroll);
    else scrollTo(samePage ? x : 0, samePage ? y : 0);
    const kept = samePage && pressedTop !== null && document.getElementById(pressedId);
    if (kept) scrollBy(0, kept.getBoundingClientRect().top - pressedTop);
    revealAnchor();
  } catch (e) {
    if (ticket === lastVisit) showConnectionError(e && e.name === 'AbortError');
  }
};

document.addEventListener('submit', async e => {
  // отменено подтверждением или форма сама шлёт запрос (проверка навыка)
  if (e.defaultPrevented) return;
  const form = e.target;
  const action = (e.submitter && e.submitter.getAttribute('formaction')) || form.getAttribute('action') || location.href;
  const method = (form.getAttribute('method') || 'get').toLowerCase();
  if (method === 'dialog') return;
  e.preventDefault();
  // файлы (импорт кампании) уходят как multipart, остальное — обычной формой
  const data = form.enctype === 'multipart/form-data' ? new FormData(form, e.submitter) : new URLSearchParams(new FormData(form, e.submitter));
  if (method === 'get') {
    const url = new URL(action, location.href);
    url.search = data.toString();
    visit(url.href);
    return;
  }
  // второе нажатие, пока идёт первое, не должно выстрелить ещё раз
  if (submitting) return;
  // UX-33: после формы открыть нужную вкладку (бросок из окна кубиков — «Чат», где виден итог)
  if (form.dataset.tabAfter) {
    try { localStorage.setItem('charka.tabs.battle-side', form.dataset.tabAfter); } catch (e) { /* только удобство */ }
  }
  submitting = true;
  document.documentElement.classList.add('is-busy');
  try {
    await visit(new URL(action, location.href).href, { method: 'POST', body: data }, undefined, e.submitter || form);
  } finally {
    // A-14: что бы ни случилось с ответом, следующая форма должна отправляться
    submitting = false;
    document.documentElement.classList.remove('is-busy');
  }
});

document.addEventListener('click', e => {
  const link = e.target.closest('a[href]');
  if (!link || e.defaultPrevented || e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
  if (link.target || link.hasAttribute('download')) return;
  const url = new URL(link.href);
  if (url.origin !== location.origin) return;
  // переход внутри страницы браузер делает сам
  if (url.hash && url.pathname === location.pathname && url.search === location.search) return;
  e.preventDefault();
  visit(url.href);
});

// Адресом и прокруткой управляем сами: иначе при «Назад» браузер сдвинул бы старую страницу до прихода новой
history.scrollRestoration = 'manual';
window.addEventListener('popstate', e => visit(location.href, undefined, (e.state && e.state.scroll) || 0));
window.addEventListener('pagehide', () => history.replaceState({ scroll: scrollY }, ''));

document.addEventListener('DOMContentLoaded', () => {
  initPage();
  if (history.state && history.state.scroll) scrollTo(0, history.state.scroll);
  revealAnchor();
});
