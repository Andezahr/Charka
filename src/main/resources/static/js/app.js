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
// Свёрнутых предков раскрываем на время окна; если окно всё равно не видно (скрытая вкладка) — оно на время
// переезжает в <body> и возвращается на место, когда закрыто
(() => {
  const show = HTMLDialogElement.prototype.showModal;
  HTMLDialogElement.prototype.showModal = function () {
    const folded = [];
    for (let node = this.parentElement; node; node = node.parentElement) {
      if (node.tagName === 'DETAILS' && !node.open) folded.push(node);
    }
    folded.forEach(details => { details.open = true; });
    let placeholder = null;
    const parent = this.parentElement;
    if (parent && parent !== document.body && !parent.getClientRects().length && getComputedStyle(parent).display !== 'contents') {
      placeholder = document.createComment('dialog');
      this.replaceWith(placeholder);
      document.body.appendChild(this);
    }
    if (folded.length || placeholder) {
      this.addEventListener('close', () => {
        folded.forEach(details => { details.open = false; });
        if (placeholder && placeholder.isConnected) placeholder.replaceWith(this);
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
    if (!first) rollAnimation(row, false);
  });
  try { sessionStorage.setItem(FEED_SEEN_KEY, JSON.stringify([...seen].slice(-400))); } catch (e) { /* только удобство */ }
}

function initPage() {
  revealRolls();

  // R-14: экран боя — до низа окна под шапкой, какой бы высоты она ни была
  const battleScreen = document.querySelector('.battle-screen');
  if (battleScreen) {
    battleScreen.style.setProperty('--battle-offset', Math.round(battleScreen.getBoundingClientRect().top + scrollY + 12) + 'px');
  }

  // Вкладки: [data-tabs="ключ"] с кнопками [data-tab] и панелями [data-tab-panel]; выбор запоминается на устройстве.
  // [data-tab-go="вкладка"] где угодно на странице открывает её
  document.querySelectorAll('[data-tabs]').forEach(holder => {
    const key = 'charka.tabs.' + holder.dataset.tabs;
    const buttons = [...holder.querySelectorAll('[data-tab]')];
    const show = name => {
      if (!buttons.some(button => button.dataset.tab === name)) name = buttons[0] && buttons[0].dataset.tab;
      buttons.forEach(button => {
        button.classList.toggle('is-active', button.dataset.tab === name);
        button.setAttribute('aria-selected', button.dataset.tab === name);
      });
      holder.querySelectorAll('[data-tab-panel]').forEach(panel => { panel.hidden = panel.dataset.tabPanel !== name; });
      try { localStorage.setItem(key, name); } catch (e) { /* только удобство */ }
    };
    let stored = null;
    try { stored = localStorage.getItem(key); } catch (e) { /* только удобство */ }
    show(stored || holder.dataset.tabDefault);
    buttons.forEach(button => button.addEventListener('click', () => show(button.dataset.tab)));
    document.querySelectorAll('[data-tab-go]').forEach(go => go.addEventListener('click', () => {
      show(go.dataset.tabGo);
      holder.scrollIntoView({ block: 'nearest' });
    }));
  });

  // R-19: выбор файла кнопкой — имя выбранного файла вместо подписи
  document.querySelectorAll('[data-file-pick]').forEach(pick => {
    const input = pick.querySelector('input[type=file]');
    const label = pick.querySelector('[data-file-name]');
    const empty = label.textContent;
    input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      label.textContent = file ? '📄 ' + file.name : empty;
      pick.classList.toggle('is-picked', !!file);
    });
  });

  // Диалог по кнопке: [data-dialog-open="id диалога"]; закрывает [data-dialog-close] внутри него
  document.querySelectorAll('[data-dialog-open]').forEach(btn => {
    const dialog = document.getElementById(btn.dataset.dialogOpen);
    if (!dialog) return;
    btn.addEventListener('click', () => dialog.showModal());
    dialog.querySelector('[data-dialog-close]').addEventListener('click', () => dialog.close());
  });

  // Диалог атаки: поля зависят от режима, цели выбираются нажатием. Последние цель и расстояние запоминаются
  // для этого оружия: повторный выстрел — два нажатия
  document.querySelectorAll('[data-attack]').forEach(dialog => {
    const mode = dialog.querySelector('[data-attack-mode]');
    const form = dialog.querySelector('form');
    const memoryKey = 'charka.attack.' + dialog.dataset.attack;
    const targets = [...dialog.querySelectorAll('input[name="target"]')];
    const unlisted = dialog.querySelector('[data-attack-unlisted]');
    const nameField = dialog.querySelector('[data-attack-name]');
    const location = dialog.querySelector('[data-attack-location]');
    const distance = dialog.querySelector('[data-attack-distance]');
    const distanceInput = distance && distance.querySelector('input');
    let remembered = {};
    try { remembered = JSON.parse(localStorage.getItem(memoryKey)) || {}; } catch (e) { /* только удобство */ }

    let distanceValue = remembered.distance || '';
    if (distanceInput) distanceInput.value = distanceValue;
    const chosen = targets.filter(input => (remembered.targets || []).includes(input.value));
    (chosen.length ? chosen : targets.slice(0, 1)).forEach(input => { input.checked = true; });

    const syncName = () => { nameField.hidden = !unlisted.checked; };
    const syncBands = () => {
      dialog.querySelectorAll('[data-attack-band]').forEach(band => {
        band.classList.toggle('is-active', distanceInput.value !== '' && band.dataset.attackBand === distanceInput.value);
      });
    };
    const update = () => {
      const option = mode.selectedOptions[0];
      const multi = option.dataset.multi === 'true';
      const noRoll = option.dataset.noroll === 'true';
      dialog.querySelector('[data-attack-rounds]').hidden = option.dataset.rounds !== 'true';
      dialog.querySelector('[data-attack-width]').hidden = option.dataset.width !== 'true';
      dialog.querySelector('[data-attack-target-label]').textContent = multi ? 'Цели в зоне' : 'Цель';
      targets.forEach(input => { input.type = multi ? 'checkbox' : 'radio'; });
      // машину бьют не всем: при ударе её нет в списке, выбранная цель переходит на первого человека
      const objects = dialog.querySelector('[data-attack-objects]');
      if (objects) {
        const allowed = option.dataset.objects === 'true';
        objects.hidden = !allowed;
        if (!allowed && objects.querySelector('input:checked')) {
          objects.querySelectorAll('input').forEach(input => { input.checked = false; });
          if (!targets.some(input => input.checked) && targets[0]) targets[0].checked = true;
        }
      }
      syncName();
      if (location) {
        // у машины зон нет (с. 113)
        const canChoose = option.dataset.location === 'true' && !targets.some(input => input.checked && 'attackObject' in input.dataset);
        dialog.querySelector('[data-attack-location-field]').hidden = !canChoose;
        location.disabled = !canChoose;
        if (!canChoose) location.value = '';
      }
      if (distance) {
        // заряд на месте: броска на попадание и расстояния нет
        const noDistance = noRoll || option.dataset.nodistance === 'true';
        distance.hidden = noDistance;
        distanceInput.required = !noDistance;
        distanceInput.value = noDistance ? 0 : distanceValue;
        syncBands();
      }
      dialog.querySelector('[data-attack-roll]').hidden = noRoll;
    };
    if (distance) {
      distanceInput.addEventListener('input', () => { distanceValue = distanceInput.value; syncBands(); });
      dialog.querySelectorAll('[data-attack-band]').forEach(band => {
        band.addEventListener('click', () => {
          distanceInput.value = band.dataset.attackBand;
          distanceValue = distanceInput.value;
          syncBands();
        });
      });
    }
    targets.forEach(input => input.addEventListener('change', update));
    mode.addEventListener('change', update);
    form.addEventListener('submit', () => {
      try {
        localStorage.setItem(memoryKey, JSON.stringify({
          targets: targets.filter(input => input.checked).map(input => input.value),
          distance: distanceValue
        }));
      } catch (e) { /* только удобство */ }
    });
    update();
  });

  // Кнопка ✎ открывает/закрывает ближайшую inline-форму редактирования:
  // ищем ближайший [data-editable] и его прямого потомка .edit-panel
  document.querySelectorAll('[data-edit-toggle]').forEach(btn => {
    btn.addEventListener('click', e => {
      e.preventDefault();
      const holder = btn.closest('[data-editable]');
      const panel = holder && holder.querySelector(':scope > .edit-panel');
      if (!panel) return;
      const open = panel.classList.toggle('is-open');
      if (open) {
        // A-03: форма правки предмета приходит по первому ✎
        loadLazy(panel).then(() => {
          const first = panel.querySelector('input:not([type=hidden]), select, textarea');
          if (first) first.focus();
        });
      }
    });
  });

  // Подтверждение для опасных действий: <form data-confirm="Текст?">
  document.querySelectorAll('form[data-confirm]').forEach(form => {
    form.addEventListener('submit', e => {
      if (!confirm(form.dataset.confirm)) e.preventDefault();
    });
  });

  // A-11: «📖 Правила» — окно с содержимым по первому открытию, дальше из памяти страницы (lazyHtml)
  document.querySelectorAll('[data-reference-open]').forEach(button => {
    const dialog = button.nextElementSibling;
    button.addEventListener('click', async () => {
      dialog.showModal();
      if (!dialog.dataset.ready) {
        try {
          dialog.querySelector('[data-reference-body]').innerHTML = await lazyHtml(button.dataset.referenceSrc);
          dialog.dataset.ready = '1';
        } catch (e) {
          dialog.querySelector('[data-reference-body]').textContent = 'Справка не загрузилась — нет связи с сервером';
        }
      }
    });
    dialog.querySelector('[data-dialog-close]').addEventListener('click', () => dialog.close());
  });

  // R-04: «💬 Стол» — на странице с карточкой чата прокрутка к ней, иначе выезжающая панель; открытая панель
  // переживает обновление страницы после отправки (sessionStorage)
  document.querySelectorAll('[data-table-open]').forEach(button => {
    const drawer = button.parentElement.querySelector('[data-table-drawer]');
    const open = async () => {
      const pinned = document.querySelector('main [data-chat]');
      if (pinned) {
        pinned.scrollIntoView({ behavior: 'smooth', block: 'start' });
        return;
      }
      drawer.showModal();
      try { sessionStorage.setItem('charka.table', '1'); } catch (e) { /* только удобство */ }
      try {
        const url = button.dataset.tableSrc + '?returnTo=' + encodeURIComponent(location.pathname);
        drawer.querySelector('[data-table-body]').innerHTML = await fetch(url, { cache: 'no-store' }).then(r => r.text());
        safely('chat', initChat);
        const input = drawer.querySelector('input[name="text"]');
        if (input && !matchMedia('(pointer: coarse)').matches) input.focus();
      } catch (e) {
        drawer.querySelector('[data-table-body]').textContent = 'Стол не загрузился — нет связи с сервером';
      }
    };
    button.addEventListener('click', open);
    drawer.querySelector('[data-table-close]').addEventListener('click', () => drawer.close());
    drawer.addEventListener('close', () => { try { sessionStorage.removeItem('charka.table'); } catch (e) { /* только удобство */ } });
    let reopen = false;
    try { reopen = sessionStorage.getItem('charka.table') === '1'; } catch (e) { /* только удобство */ }
    if (reopen && !document.querySelector('main [data-chat]')) open();
  });

  // R-09a: «Частые навыки» — после проверки открыть «Стол»: итог там
  document.querySelectorAll('form[data-opens-table]').forEach(form => form.addEventListener('submit', () => {
    try { sessionStorage.setItem('charka.table', '1'); } catch (e) { /* только удобство */ }
  }));

  // R-04a: одна форма проверки — у выбранного персонажа его навыки с итогом, у мастера и систем без навыков — ±Мод и подпись
  document.querySelectorAll('[data-check-form]').forEach(holder => {
    const who = holder.querySelector('[data-check-who]');
    const me = holder.closest('[data-chat]') && holder.closest('[data-chat]').querySelector('[data-me]');
    if (me && [...who.options].some(option => option.value === me.value)) who.value = me.value;
    const sync = () => {
      let skilled = false;
      holder.querySelectorAll('[data-check-skill]').forEach(select => {
        const active = select.dataset.checkSkill === who.value;
        select.hidden = !active;
        select.disabled = !active;
        skilled = skilled || active;
      });
      holder.querySelectorAll('[data-check-mod]').forEach(field => { field.hidden = skilled; });
    };
    who.addEventListener('change', sync);
    if (me) me.addEventListener('change', () => { if ([...who.options].some(option => option.value === me.value)) { who.value = me.value; sync(); } });
    sync();
  });

  // A-02: приглашение — у ноутбука несколько адресов, QR-код того, что выбран
  document.querySelectorAll('[data-invite-select]').forEach(select => {
    const codes = select.closest('[data-invite]').querySelectorAll('[data-invite-qr]');
    select.addEventListener('change', () => codes.forEach((code, i) => { code.hidden = String(i) !== select.value; }));
  });

  // Форма создания счётчика: доп. поля показываются только для выбранного типа.
  // <select data-type-select> + блоки <div data-type-fields="ТИП">; поля скрытых блоков отключаются, чтобы не уходили на сервер
  document.querySelectorAll('[data-type-select]').forEach(select => {
    const form = select.closest('form');
    const initialField = form.querySelector('[data-initial-field]');
    const sync = () => {
      form.querySelectorAll('[data-type-fields]').forEach(block => {
        const active = block.dataset.typeFields === select.value;
        block.hidden = !active;
        block.querySelectorAll('input, select, textarea').forEach(el => { el.disabled = !active; });
      });
      // типам без начального значения (характеристики, броня) поле не нужно — прячем и шлём 0
      if (initialField) {
        const option = select.selectedOptions[0];
        const hide = option && option.dataset.initial === 'false';
        initialField.hidden = hide;
        if (hide) initialField.querySelector('input').value = 0;
      }
    };
    select.addEventListener('change', sync);
    sync();
  });

  initItemForms(document);

  // Окно «Добавить»: содержимое приходит при первом открытии (A-03) — setupAddDialog
  document.querySelectorAll('[data-add-dialog]').forEach(dialog => {
    document.querySelectorAll('[data-add-item]').forEach(button => button.addEventListener('click', async () => {
      if (!dialog.dataset.ready) {
        dialog.innerHTML = await lazyHtml(dialog.dataset.addSrc);
        initItemForms(dialog);
        setupAddDialog(dialog);
        dialog.dataset.ready = '1';
      }
      dialog.openFor(button);
    }));
  });

  // Мастер создания персонажа CP2020: остаток очков, производные, пакет роли, деньги. Сервер проверяет всё ещё раз (Cp2020Creation)
  document.querySelectorAll('[data-creation]').forEach(page => {
    const form = page.querySelector('[data-creation-form]');
    const method = form.querySelector('[data-creation-method]');
    const points = form.querySelector('[data-creation-points]');
    const rollButton = form.querySelector('[data-creation-roll]');
    const rollsText = form.querySelector('[data-creation-rolls]');
    const stats = [...form.querySelectorAll('[data-stat]')];
    const role = form.querySelector('[data-creation-role]');
    const pickups = form.querySelector('[data-creation-pickups]');
    const addButton = form.querySelector('[data-creation-add-pickup]');
    const rowTemplate = form.querySelector('[data-creation-pickup-row]');
    const submit = form.querySelector('[data-creation-submit]');
    const problem = form.querySelector('[data-creation-problem]');
    const btm = page.dataset.btm.split(',').map(Number);
    const stat = code => Number(stats.find(input => input.dataset.stat === code).value) || 0;
    const sum = inputs => inputs.reduce((total, input) => total + (Number(input.value) || 0), 0);
    let pickupCount = 0;

    const update = () => {
      const problems = [];
      const total = Number(points.value) || 0;
      const left = total - sum(stats);
      form.querySelector('[data-creation-left]').textContent = total ? 'осталось ' + left : '—';
      if (!total) problems.push('определите очки');
      else if (left !== 0) problems.push(left > 0 ? 'распределите ещё ' + left : 'лишних очков ' + (-left));
      const run = stat('ma') * 3;
      const body = stat('body');
      form.querySelector('[data-creation-derived]').textContent =
        'Бег ' + run + ' м · прыжок ' + Math.floor(run / 4) + ' м · переноска ' + body * 10 + ' кг · подъём ' + body * 40
        + ' кг · BTM −' + (btm[Math.min(body, btm.length - 1)] || 0) + ' · спасбросок ' + body + ' · человечность ' + stat('emp') * 10;

      const pack = form.querySelector('[data-creation-package]:not([hidden])');
      const career = pack ? [...pack.querySelectorAll('[data-creation-career]')] : [];
      const careerLeft = 40 - sum(career);
      form.querySelector('[data-creation-career-left]').textContent = 'осталось ' + careerLeft;
      if (!pack) problems.push('выберите роль');
      if (careerLeft < 0) problems.push('карьерных очков больше 40');
      const option = role.selectedOptions[0];
      const funds = form.querySelector('[data-creation-funds]');
      if (pack && option.dataset.salaries) {
        const special = Number(career.find(input => input.dataset.special === 'true').value) || 0;
        const salary = option.dataset.salaries.split(',')[Math.max(0, Math.min(special, 10) - 5)];
        funds.textContent = 'Стартовые деньги: ' + salary + ' eb в месяц × 1D6/3 мес. — бросок при создании';
      } else {
        funds.textContent = '';
      }

      const pickupInputs = [...pickups.querySelectorAll('input[type=number]')];
      const pickupLeft = stat('ref') + stat('int') - sum(pickupInputs);
      form.querySelector('[data-creation-pickup-left]').textContent = 'осталось ' + pickupLeft;
      if (pickupLeft < 0) problems.push('очков хобби больше REF + INT');
      problem.textContent = problems.length ? 'Ещё: ' + problems.join(', ') : '';
      submit.disabled = problems.length > 0;
    };

    method.addEventListener('change', () => {
      const option = method.selectedOptions[0];
      rollButton.hidden = !(method.value === 'RANDOM' || method.value === 'FAST');
      rollsText.hidden = true;
      points.readOnly = method.value !== 'MANUAL';
      points.value = option.dataset.points || '';
      update();
    });
    rollButton.addEventListener('click', async () => {
      const response = await fetch(page.dataset.rollUrl, { method: 'POST', body: new URLSearchParams({ method: method.value }) });
      if (!response.ok) return;
      const roll = await response.json();
      points.value = roll.total;
      rollsText.hidden = false;
      rollsText.textContent = 'Кубики: ' + roll.rolls.join(', ') + ' = ' + roll.total
        + (method.value === 'FAST' ? '. Разложены по статам по порядку, можно переставить' : '');
      if (method.value === 'FAST') stats.forEach((input, index) => { input.value = roll.rolls[index]; });
      update();
    });
    role.addEventListener('change', () => {
      form.querySelectorAll('[data-creation-package]').forEach(pack => {
        const active = pack.dataset.creationPackage === role.value;
        pack.hidden = !active;
        pack.querySelectorAll('input, select').forEach(input => { input.disabled = !active; });
      });
      update();
    });
    const addPickup = () => {
      const row = rowTemplate.content.firstElementChild.cloneNode(true);
      row.querySelector('select').name = 'pickup.' + pickupCount + '.skill';
      row.querySelector('input').name = 'pickup.' + pickupCount + '.level';
      pickupCount++;
      pickups.insertBefore(row, addButton.previousElementSibling);
      update();
    };
    addButton.addEventListener('click', addPickup);
    form.querySelector('[data-creation-lifepath]').addEventListener('click', async () => {
      const age = form.querySelector('[data-creation-age]').value;
      const response = await fetch(page.dataset.lifepathUrl, { method: 'POST', body: new URLSearchParams(age ? { age } : {}) });
      if (!response.ok) return;
      const lifepath = await response.json();
      form.querySelector('[data-creation-lifepath-json]').value = JSON.stringify(lifepath);
      const view = form.querySelector('[data-creation-lifepath-view]');
      view.replaceChildren(...lifepath.sections.map(section => {
        const block = document.createElement('div');
        const title = document.createElement('strong');
        title.textContent = section.title;
        const list = document.createElement('ul');
        list.append(...section.lines.map(line => { const item = document.createElement('li'); item.textContent = line; return item; }));
        block.append(title, list);
        return block;
      }));
      const language = form.querySelector('[data-creation-language]');
      language.replaceChildren(...lifepath.languages.map(choice =>
        new Option('Родной язык +8: ' + choice.name, choice.name + '|' + choice.family)));
      language.hidden = false;
    });
    form.addEventListener('input', update);
    addPickup();
    update();
  });

  // Регулярная трата из шаблона системы: заполняет название, сумму и период; у жилья — число комнат и зона-множитель
  document.querySelectorAll('[data-expense-form]').forEach(form => {
    const template = form.querySelector('[data-expense-template]');
    if (!template) return;
    const units = form.querySelector('[data-expense-units]');
    const multiplier = form.querySelector('[data-expense-multiplier]');
    const name = form.querySelector('input[name=name]');
    const amount = form.querySelector('input[name=amount]');
    const period = form.querySelector('input[name=periodDays]');
    const fill = () => {
      const option = template.selectedOptions[0];
      if (!option || !option.value) return;
      const count = Number(units.value) || 1;
      const factor = multiplier.hidden ? 1 : Number(multiplier.value) || 1;
      const zone = multiplier.hidden ? '' : ', ' + multiplier.selectedOptions[0].dataset.label;
      name.value = option.dataset.name + (option.dataset.unit ? ', ' + count + ' ' + option.dataset.unit : '') + zone;
      amount.value = Number(option.dataset.amount) * count * factor;
      period.value = option.dataset.period;
    };
    template.addEventListener('change', () => {
      const option = template.selectedOptions[0];
      const pairs = option.value && option.dataset.multipliers ? option.dataset.multipliers.split('|') : [];
      multiplier.innerHTML = '';
      pairs.forEach(pair => {
        const [label, factor] = pair.split(':');
        const choice = new Option(label + ' ×' + factor, factor);
        choice.dataset.label = label;
        multiplier.add(choice);
      });
      multiplier.hidden = pairs.length === 0;
      units.hidden = !(option.value && option.dataset.unit);
      units.value = 1;
      if (option.value) fill(); else { name.value = ''; amount.value = ''; period.value = 30; }
    });
    units.addEventListener('input', fill);
    multiplier.addEventListener('change', fill);
  });

  // Страница навыков. Видимость строк: изученные и спецспособности ролей; «Все навыки», прокачка, ручная правка
  // и поиск показывают весь каталог. Без JS видно всё, но проверка и правка требуют JS.
  document.querySelectorAll('[data-skills]').forEach(page => {
    const stored = {
      get(key) { try { return sessionStorage.getItem(key); } catch (e) { return null; } },
      set(key, value) { try { sessionStorage.setItem(key, value); } catch (e) { /* только удобство */ } }
    };
    const rows = [...page.querySelectorAll('[data-skill-row]')];
    const groups = [...page.querySelectorAll('[data-skills-group]')];
    const search = page.querySelector('[data-skills-search]');
    const allBtn = page.querySelector('[data-skills-all]');
    const improveBtn = page.querySelector('[data-skills-improve]');
    const empty = page.querySelector('[data-skills-empty]');
    let showAll = stored.get('skills-show-all') === '1';
    // режим прокачки переживает перезагрузку после каждого повышения
    let improving = Boolean(improveBtn) && stored.get('skills-improving') === '1';
    let editing = false;

    const render = () => {
      const query = search.value.trim().toLowerCase();
      rows.forEach(row => {
        const name = row.querySelector('[data-skill-name]').textContent.toLowerCase();
        const relevant = showAll || editing || improving || query
          || !row.classList.contains('is-zero') || row.dataset.always === 'true';
        row.hidden = !relevant || (query && !name.includes(query));
      });
      groups.forEach(group => {
        // кнопки «＋ Язык» видны в «Все навыки» и в прокачке, группа без строк там не прячется
        const hasRows = group.querySelector('[data-skill-row]:not([hidden])');
        group.hidden = !hasRows && !((showAll || improving) && !editing && !query);
      });
      page.classList.toggle('is-editing', editing);
      page.classList.toggle('is-improving', improving);
      page.classList.toggle('is-all', showAll && !editing && !improving && !query);
      page.classList.toggle('can-add', (showAll || improving) && !editing && !query);
      // в прокачке новый навык добавляется с нулевым уровнем: уровни покупаются за очки
      page.querySelectorAll('.skill-add-level').forEach(input => {
        input.value = improving ? 0 : 1;
      });
      allBtn.hidden = editing || improving;
      if (improveBtn) {
        improveBtn.hidden = editing;
        improveBtn.setAttribute('aria-pressed', String(improving));
        improveBtn.textContent = improving ? 'Закончить прокачку' : 'Прокачка';
      }
      allBtn.setAttribute('aria-pressed', String(showAll));
      allBtn.textContent = showAll ? 'Только изученные' : 'Все навыки';
      page.querySelector('.skills-toolbar [data-skills-edit]').hidden = editing || improving;
      const nothing = !rows.some(row => !row.hidden);
      empty.hidden = !nothing;
      empty.textContent = query
        ? 'Такого навыка нет.'
        : 'Изученных навыков пока нет. Откройте «Все навыки» или «Прокачку».';
    };

    search.addEventListener('input', render);
    allBtn.addEventListener('click', () => {
      showAll = !showAll;
      stored.set('skills-show-all', showAll ? '1' : '0');
      render();
    });
    if (improveBtn) {
      improveBtn.addEventListener('click', () => {
        improving = !improving;
        stored.set('skills-improving', improving ? '1' : '0');
        render();
      });
    }
    page.querySelectorAll('[data-skills-edit]').forEach(btn => {
      btn.addEventListener('click', () => {
        editing = !editing;
        if (!editing) {
          page.querySelectorAll('.skill-input').forEach(input => { input.value = input.defaultValue; });
        }
        render();
      });
    });
    // Панель статов: если группа скрыта фильтром, сначала показываем весь каталог, потом переходим
    page.querySelectorAll('[data-skills-jump]').forEach(link => {
      link.addEventListener('click', () => {
        const target = page.querySelector(link.getAttribute('href'));
        if (target && target.hidden) {
          search.value = '';
          showAll = true;
          stored.set('skills-show-all', '1');
          render();
        }
      });
    });
    render();

    // Проверка навыка: бросок без перезагрузки, результат — прямо в диалоге, место на странице не теряется
    const dialog = page.querySelector('#skill-check-dialog');
    const form = dialog.querySelector('form');
    const difficulty = form.elements.difficulty;
    const result = dialog.querySelector('[data-check-result]');
    const error = dialog.querySelector('[data-check-error]');
    const submit = dialog.querySelector('[data-check-submit]');

    const syncPresets = () => {
      dialog.querySelectorAll('[data-dc]').forEach(btn => {
        btn.classList.toggle('is-active', btn.dataset.dc === difficulty.value);
      });
    };
    difficulty.value = stored.get('skills-last-dc') || '';
    syncPresets();
    difficulty.addEventListener('input', syncPresets);
    dialog.querySelectorAll('[data-dc]').forEach(btn => {
      btn.addEventListener('click', () => {
        // повторное нажатие снимает сложность
        difficulty.value = difficulty.value === btn.dataset.dc ? '' : btn.dataset.dc;
        syncPresets();
      });
    });

    // Режим «Против противника»: сложности нет, навыки выбранного персонажа подгружаются с сервера
    const opposedResult = dialog.querySelector('[data-opposed-result]');
    const opposedFields = dialog.querySelector('[data-opposed-fields]');
    const opponent = dialog.querySelector('[data-opponent]');
    const opponentSkill = dialog.querySelector('[data-opponent-skill]');
    let opposed = stored.get('skills-check-mode') === 'opposed';
    let skillsRequest = 0;

    const hideResults = () => {
      result.hidden = true;
      opposedResult.hidden = true;
      error.hidden = true;
      submit.textContent = '🎲 Бросить';
    };
    const showError = err => {
      result.hidden = true;
      opposedResult.hidden = true;
      error.textContent = err.message || 'Не удалось выполнить проверку';
      error.hidden = false;
    };
    const syncOpponentSkill = () => {
      const option = opponentSkill.selectedOptions[0];
      form.elements.opponentCode.value = option ? option.dataset.code : '';
      form.elements.opponentSkillId.value = option ? option.dataset.skillId || '' : '';
    };
    // Сразу предлагаем тот же навык, иначе первый изученный
    const fillOpponentSkills = skills => {
      const addGroup = (label, list) => {
        if (list.length === 0) return;
        const group = document.createElement('optgroup');
        group.label = label;
        list.forEach(skill => {
          const option = document.createElement('option');
          option.textContent = `${skill.name} — ${skill.total}`;
          option.dataset.code = skill.code;
          if (skill.skillId != null) option.dataset.skillId = skill.skillId;
          option.selected = !form.elements.skillId.value && !skill.skillId && skill.code === form.elements.code.value;
          group.append(option);
        });
        opponentSkill.append(group);
      };
      opponentSkill.replaceChildren();
      addGroup('Изученные', skills.filter(skill => skill.level > 0));
      addGroup('Остальные', skills.filter(skill => skill.level === 0));
      syncOpponentSkill();
    };
    const syncOpponent = async () => {
      const known = opponent.value !== '';
      dialog.querySelector('[data-opponent-known]').hidden = !known;
      dialog.querySelector('[data-opponent-free]').hidden = known;
      dialog.querySelector('[data-opponent-mod-label]').textContent = known ? 'Модификатор противника' : 'Стат + навык противника';
      const request = ++skillsRequest;
      opponentSkill.replaceChildren();
      syncOpponentSkill();
      if (!known || !opposed) {
        submit.disabled = false;
        return;
      }
      submit.disabled = true;
      try {
        const response = await fetch(opposedFields.dataset.api + opponent.value + '/skills');
        if (!response.ok) throw new Error(await response.text());
        const skills = await response.json();
        if (request === skillsRequest) fillOpponentSkills(skills);
      } catch (err) {
        if (request === skillsRequest) showError(err);
      } finally {
        if (request === skillsRequest) submit.disabled = false;
      }
    };
    const syncMode = () => {
      dialog.querySelector('[data-dc-fields]').hidden = opposed;
      opposedFields.hidden = !opposed;
      dialog.querySelectorAll('[data-check-mode]').forEach(btn => {
        btn.classList.toggle('is-active', (btn.dataset.checkMode === 'opposed') === opposed);
      });
      syncOpponent();
    };
    dialog.querySelectorAll('[data-check-mode]').forEach(btn => {
      btn.addEventListener('click', () => {
        opposed = btn.dataset.checkMode === 'opposed';
        stored.set('skills-check-mode', opposed ? 'opposed' : 'dc');
        hideResults();
        syncMode();
      });
    });
    const lastOpponent = stored.get('skills-last-opponent');
    if (lastOpponent !== null && [...opponent.options].some(option => option.value === lastOpponent)) {
      opponent.value = lastOpponent;
    }
    opponent.addEventListener('change', () => {
      stored.set('skills-last-opponent', opponent.value);
      hideResults();
      syncOpponent();
    });
    opponentSkill.addEventListener('change', syncOpponentSkill);

    page.querySelectorAll('[data-skill-check]').forEach(btn => {
      btn.addEventListener('click', () => {
        form.elements.code.value = btn.dataset.code;
        form.elements.skillId.value = btn.dataset.skillId || '';
        form.elements.modifier.value = 0;
        form.elements.opponentModifier.value = 0;
        if (form.elements.luck) form.elements.luck.value = 0;
        dialog.querySelector('.dialog-more').open = false;
        dialog.querySelector('[data-check-title]').textContent = btn.dataset.name;
        dialog.querySelector('[data-check-sum]').textContent = btn.dataset.sum;
        hideResults();
        syncMode();
        dialog.showModal();
      });
    });

    const renderCheck = (block, check) => {
      block.querySelector('[data-check-total]').textContent = check.total;
      block.querySelector('[data-check-steps]').replaceChildren(...check.steps.map(step => {
        const item = document.createElement('li');
        item.textContent = step;
        return item;
      }));
      block.classList.toggle('is-success', check.success === true);
      block.classList.toggle('is-fail', check.success === false);
    };

    form.addEventListener('submit', async e => {
      e.preventDefault();
      submit.disabled = true;
      error.hidden = true;
      if (!opposed) stored.set('skills-last-dc', difficulty.value);
      try {
        const body = new URLSearchParams();
        new FormData(form).forEach((value, key) => { if (value !== '') body.append(key, value); });
        const response = await fetch(form.action + (opposed ? '/opposed' : ''), { method: 'POST', body });
        if (!response.ok) throw new Error(await response.text());
        const check = await response.json();
        if (opposed) {
          ['initiator', 'opponent'].forEach(name => {
            const block = opposedResult.querySelector(`[data-side="${name}"]`);
            block.querySelector('[data-check-title-side]').textContent = check[name].label;
            renderCheck(block, check[name]);
          });
          dialog.querySelector('[data-opposed-verdict]').textContent = check.verdict;
          opposedResult.hidden = false;
          result.hidden = true;
        } else {
          dialog.querySelector('[data-check-outcome]').textContent = check.outcome;
          renderCheck(result, check);
          result.hidden = false;
          opposedResult.hidden = true;
        }
        submit.textContent = '🎲 Бросить ещё';
      } catch (err) {
        showError(err);
      } finally {
        submit.disabled = false;
        // результат выше формы: на низком экране фокус не должен увести его за край
        submit.focus({ preventScroll: true });
        dialog.scrollTop = 0;
      }
    });
    dialog.querySelector('[data-dialog-close]').addEventListener('click', () => dialog.close());
  });

  // Счётчик символов: <input data-count-to="hintId" maxlength="N">
  document.querySelectorAll('[data-count-to]').forEach(field => {
    const hint = document.getElementById(field.dataset.countTo);
    const max = Number(field.getAttribute('maxlength'));
    if (!hint || !max) return;
    const update = () => {
      const len = field.value.length;
      hint.textContent = `${len}/${max} символов`;
      hint.classList.toggle('over', len >= max);
      hint.classList.toggle('warn', len < max && len > max * 0.9);
    };
    field.addEventListener('input', update);
    update();
  });

  // Запоминаемый выбор: <select data-remember="ключ"> — последняя карта начала боя
  document.querySelectorAll('select[data-remember]').forEach(select => {
    const key = 'charka.remember.' + select.dataset.remember;
    try {
      const stored = localStorage.getItem(key);
      if (stored !== null && [...select.options].some(option => option.value === stored)) select.value = stored;
    } catch (e) { /* только удобство */ }
    select.addEventListener('change', () => {
      try { localStorage.setItem(key, select.value); } catch (e) { /* только удобство */ }
    });
  });
  // A-09: галочка тоже запоминается: <input type="checkbox" data-remember="ключ"> — «🙈 Скрыто» у мастера
  document.querySelectorAll('input[type=checkbox][data-remember]').forEach(box => {
    const key = 'charka.remember.' + box.dataset.remember;
    try { box.checked = localStorage.getItem(key) === '1'; } catch (e) { /* только удобство */ }
    box.addEventListener('change', () => {
      try { localStorage.setItem(key, box.checked ? '1' : ''); } catch (e) { /* только удобство */ }
    });
  });

  // A-14: каждая часть отдельно — ошибка в одной не мешает остальным
  safely('battle', initBattle);
  safely('editor', initEditor);
  safely('chat', initChat);
  safely('turn', initTurnWatch);
  safely('requests', initRequests);
}

function safely(name, init) {
  try {
    init();
  } catch (e) {
    console.error(name, e);
    if (window.reportClientError) window.reportClientError(name + ': ' + e.message, e.stack);
  }
}

// A-04: «мой ход» на телефоне игрока. Опрос /turn раз в 2 с; ход сменился — если подошла очередь игрока,
// вибрация, звук (галочка «Звук», запоминается) и заголовок вкладки; экран обновляется сам, если ничего не открыто
const turnWatch = { url: null, turn: null, mine: [], timer: null };
const TURN_SOUND_KEY = 'charka.turnSound';

function initTurnWatch() {
  clearTimeout(turnWatch.timer);
  const holder = document.querySelector('[data-turn-watch]');
  if (!holder) {
    turnWatch.url = null;
    return;
  }
  Object.assign(turnWatch, { url: holder.dataset.turnWatch, turn: holder.dataset.turn, mine: holder.dataset.mine.split(',') });
  if (holder.querySelector('.turn-panel.is-mine') && !document.title.startsWith('▶')) document.title = '▶ Ваш ход · ' + document.title;
  let sound = false;
  try { sound = localStorage.getItem(TURN_SOUND_KEY) === '1'; } catch (e) { /* без памяти — без звука */ }
  holder.querySelectorAll('[data-turn-sound]').forEach(box => {
    box.checked = sound;
    box.addEventListener('change', () => {
      try { localStorage.setItem(TURN_SOUND_KEY, box.checked ? '1' : ''); } catch (e) { /* только удобство */ }
      holder.querySelectorAll('[data-turn-sound]').forEach(other => { other.checked = box.checked; });
      if (box.checked) beep();
    });
  });
  turnWatch.timer = setTimeout(pollTurn, 2000);
}

async function pollTurn() {
  if (!turnWatch.url || !document.querySelector('[data-turn-watch]')) return;
  try {
    const response = await fetch(turnWatch.url, { cache: 'no-store' });
    if (response.ok) {
      const state = await response.json();
      if (state.turn !== turnWatch.turn) {
        turnWatch.turn = state.turn;
        turnChanged(turnWatch.mine.includes(String(state.current)));
        return;
      }
    }
  } catch (e) { /* сеть пропала: попробуем позже */ }
  turnWatch.timer = setTimeout(pollTurn, document.hidden ? 5000 : 2000);
}

function turnChanged(mine) {
  if (mine) {
    if (navigator.vibrate) navigator.vibrate([250, 120, 250]);
    let sound = false;
    try { sound = localStorage.getItem(TURN_SOUND_KEY) === '1'; } catch (e) { /* без звука */ }
    if (sound) beep();
  }
  // открытый диалог, раскрытое меню или поле с вводом не сбрасываем — полоса «Обновить»
  const busy = document.querySelector('dialog[open], details[data-menu][open]')
    || (document.activeElement && document.activeElement.matches('input:not([type=checkbox]), textarea, select') && document.activeElement.value);
  if (busy || submitting) {
    const bar = document.querySelector('[data-turn-changed]');
    if (bar) bar.hidden = false;
    if (mine) document.title = '▶ Ваш ход · ' + document.title.replace(/^▶ Ваш ход · /, '');
    turnWatch.timer = setTimeout(pollTurn, 2000);
    return;
  }
  visit(location.href);
}

// Короткий сигнал без файла: WebAudio, два тона
function beep() {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    const audio = beep.context || (beep.context = new AudioCtx());
    [0, 0.18].forEach((start, i) => {
      const tone = audio.createOscillator();
      const gain = audio.createGain();
      tone.frequency.value = i ? 990 : 660;
      gain.gain.setValueAtTime(0.18, audio.currentTime + start);
      gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + start + 0.16);
      tone.connect(gain).connect(audio.destination);
      tone.start(audio.currentTime + start);
      tone.stop(audio.currentTime + start + 0.17);
    });
  } catch (e) { /* звук не поддерживается */ }
}

// A-07: запросы бросков от мастера — карточки опрашиваются раз в 3 с (10 с в фоне), подмена только при изменении
const requestsPoll = { timer: null };

function initRequests() {
  clearTimeout(requestsPoll.timer);
  if (!document.querySelector('[data-requests]')) return;
  pollRequests();
}

async function pollRequests() {
  const slots = [...document.querySelectorAll('[data-requests]')];
  if (!slots.length) return;
  for (const slot of slots) {
    try {
      const response = await fetch(slot.dataset.requests, { cache: 'no-store' });
      if (response.ok) {
        const html = (await response.text()).trim();
        if (slot.dataset.html !== html) {
          const had = slot.dataset.html !== undefined && slot.dataset.html !== '';
          slot.dataset.html = html;
          slot.innerHTML = html;
          // новый запрос у игрока — как подошедший ход: короткая вибрация
          if (html && !had && slot.querySelector('.request-card:not(.is-gm)') && navigator.vibrate) navigator.vibrate(150);
        }
      }
    } catch (e) { /* сеть пропала: попробуем позже */ }
  }
  requestsPoll.timer = setTimeout(pollRequests, document.hidden ? 10000 : 3000);
}

// Чат кампании (U-06): лента опрашивается раз в 3 с (раз в 10 с после минуты тишины) с ETag и подменяется целиком,
// форма отправляется как обычно; после отправки курсор снова в поле
const chat = { url: null, etag: null, timer: null, quietSince: 0, refocus: false };

// KF-09: «💬 Только сообщения» — броски, проверки и журнал боя прячутся из ленты у этого игрока, на этом устройстве
const ONLY_MESSAGES_KEY = 'charka.chat.onlyMessages';
function syncOnlyMessages() {
  let on = false;
  try { on = localStorage.getItem(ONLY_MESSAGES_KEY) === '1'; } catch (e) { /* только удобство */ }
  document.querySelectorAll('[data-chat]').forEach(card => card.classList.toggle('only-messages', on));
  document.querySelectorAll('[data-chat-only-messages]').forEach(box => { box.checked = on; });
}
document.addEventListener('change', e => {
  if (!e.target.matches('[data-chat-only-messages]')) return;
  try { localStorage.setItem(ONLY_MESSAGES_KEY, e.target.checked ? '1' : '0'); } catch (err) { /* только удобство */ }
  syncOnlyMessages();
});

function initChat() {
  syncOnlyMessages();
  const card = document.querySelector('[data-chat]');
  clearTimeout(chat.timer);
  if (!card) {
    chat.url = null;
    return;
  }
  if (chat.url !== card.dataset.chat) Object.assign(chat, { url: card.dataset.chat, etag: null, quietSince: Date.now() });
  const me = card.querySelector('[data-me]');
  const key = meKey(chat.url);
  if (me) {
    try {
      const stored = localStorage.getItem(key);
      if (stored !== null && [...me.options].some(option => option.value === stored)) me.value = stored;
    } catch (e) { /* только удобство */ }
    me.addEventListener('change', () => {
      try { localStorage.setItem(key, me.value); } catch (e) { /* только удобство */ }
    });
  }
  const form = card.querySelector('form');
  form.addEventListener('submit', () => { chat.refocus = true; });
  if (chat.refocus) {
    chat.refocus = false;
    const input = form.querySelector('input[name="text"]');
    if (input) input.focus({ preventScroll: true });
  }
  chat.timer = setTimeout(pollChat, 3000);
}

async function pollChat() {
  const card = document.querySelector('[data-chat]');
  if (!card || card.dataset.chat !== chat.url) return;
  if (!document.hidden) {
    try {
      const response = await fetch(chat.url, { cache: 'no-store', headers: chat.etag ? { 'If-None-Match': chat.etag } : {} });
      if (response.status === 200) {
        // первый ответ после загрузки тоже подставляем: между отрисовкой страницы и опросом могли написать
        const changed = chat.etag !== null;
        chat.etag = response.headers.get('ETag');
        const feed = card.querySelector('[data-chat-feed]');
        if (feed) {
          feed.innerHTML = await response.text();
          revealRolls();
        }
        if (changed) chat.quietSince = Date.now();
      }
    } catch (e) { /* сеть пропала: попробуем позже */ }
  }
  chat.timer = setTimeout(pollChat, Date.now() - chat.quietSince > 60000 ? 10000 : 3000);
}

// A-03: тяжёлое (окно «Добавить», формы правки предметов) приходит по требованию и хранится в памяти страницы,
// вне initPage: после действия страница заменяется, а справочник тот же. Адрес несёт версию, если содержимое меняется
const lazyCache = new Map();
function lazyHtml(url) {
  if (!lazyCache.has(url)) {
    lazyCache.set(url, fetch(url, { headers: { 'X-Requested-With': 'fetch' } })
      .then(response => { if (!response.ok) throw new Error(response.status); return response.text(); })
      .catch(error => { lazyCache.delete(url); throw error; }));
  }
  return lazyCache.get(url);
}

// [data-lazy] внутри элемента: заменить содержимым по адресу; формы предмета внутри — настроить
async function loadLazy(root) {
  for (const slot of root.querySelectorAll('[data-lazy]')) {
    const url = slot.dataset.lazy;
    slot.removeAttribute('data-lazy');
    const holder = document.createElement('div');
    // форма правки меняется с предметом — её не кэшируем
    holder.innerHTML = await fetch(url).then(response => response.text());
    slot.replaceWith(...holder.childNodes);
  }
  initItemForms(root);
}

// Форма предмета: поля выбранного типа и список типичных значений — на странице и в догруженном
function initItemForms(root) {
  // Форма предмета: показываем поля выбранного типа; поля других типов выключены и не отправляются
  root.querySelectorAll('[data-item-form]:not([data-item-ready])').forEach(form => {
    form.dataset.itemReady = '1';
    const type = form.elements.itemType;
    if (!type) return;
    const sync = () => {
      form.querySelectorAll('[data-item-fields]').forEach(group => {
        const active = group.dataset.itemFields === type.value;
        group.hidden = !active;
        group.querySelectorAll('input, select').forEach(field => { field.disabled = !active; });
      });
    };
    type.addEventListener('change', sync);
    sync();
  });

  // Поле с типичными значениями: на сервер уходит текстовое поле за списком, оно видно только при «Другой…».
  // Если у поля есть data-name-prefix, выбор подставляет название предмета, пока его не ввели руками
  root.querySelectorAll('select[data-suggest]:not([data-suggest-ready])').forEach(select => {
    select.dataset.suggestReady = '1';
    const input = select.nextElementSibling;
    const name = select.form.elements.itemName;
    const prefix = select.dataset.namePrefix;
    let filled = '';
    const sync = () => {
      const custom = select.value === '__other';
      input.hidden = !custom;
      input.required = custom && select.required;
    };
    const fillName = () => {
      if (!prefix || (name.value && name.value !== filled)) return;
      filled = input.value ? prefix + input.value : '';
      name.value = filled;
    };
    const known = [...select.options].some(option => option.value === input.value);
    select.value = known ? input.value : '__other';
    select.hidden = false;
    sync();
    select.addEventListener('change', () => {
      const custom = select.value === '__other';
      input.value = custom ? '' : select.value;
      sync();
      fillName();
      if (custom) input.focus();
    });
    input.addEventListener('input', fillName);
  });
}

// Окно «Добавить»: одно на страницу, инвентарь подставляет кнопка (dialog.openFor). Запоминает последнюю вкладку и возвращает к инвентарю
function setupAddDialog(dialog) {
  const memoryKey = 'charka.addItem.tab';
  const stored = {
    get() { try { return localStorage.getItem(memoryKey); } catch (e) { return null; } },
    set(value) { try { localStorage.setItem(memoryKey, value); } catch (e) { /* без памяти — первая вкладка */ } }
  };
  const panels = [...dialog.querySelectorAll('[data-add-panel]')];
  const tabs = [...dialog.querySelectorAll('[data-add-tab]')];
  const show = code => {
    const panel = panels.find(candidate => candidate.dataset.addPanel === code) || panels[0];
    panels.forEach(candidate => { candidate.hidden = candidate !== panel; });
    tabs.forEach(tab => { tab.classList.toggle('is-active', tab.dataset.addTab === panel.dataset.addPanel); });
    const first = panel.querySelector('[data-add-search], input[name=itemName]');
    // на телефоне клавиатура закрыла бы половину списка
    if (first && dialog.open && !matchMedia('(pointer: coarse)').matches) first.focus();
  };
  const addTabs = dialog.querySelector('.add-tabs');
  let pendingConfirm = null;
  dialog.querySelectorAll('[data-add-form=catalog]').forEach(form => form.addEventListener('submit', e => {
    if (pendingConfirm && !confirm(pendingConfirm)) e.preventDefault();
  }));
  tabs.forEach(tab => tab.addEventListener('click', () => {
    stored.set(tab.dataset.addTab);
    show(tab.dataset.addTab);
  }));

  dialog.querySelector('[data-dialog-close]').addEventListener('click', () => dialog.close());
  dialog.openFor = button => {
    const items = dialog.dataset.base + button.dataset.addItem + '/items';
    // кнопка киберware: одна вкладка, и выбор сразу устанавливает
    const then = button.dataset.addThen || '';
    pendingConfirm = then ? button.dataset.addConfirm : null;
    dialog.querySelectorAll('[data-add-then]').forEach(input => { input.value = then; });
    dialog.querySelectorAll('[data-add-form=catalog] input[name=quantity]').forEach(input => { input.value = 1; input.hidden = !!then; });
    dialog.querySelectorAll('.add-own').forEach(own => { own.hidden = !!then; });
    if (addTabs) addTabs.hidden = !!then;
    dialog.querySelector('[data-add-target]').textContent = button.dataset.addTitle || 'Добавить в «' + button.dataset.addItemName + '»';
    dialog.querySelectorAll('[data-add-form=catalog]').forEach(form => { form.action = items + '/catalog'; });
    dialog.querySelectorAll('[data-add-form=own]').forEach(form => { form.action = items; });
    dialog.querySelectorAll('[data-add-anchor]').forEach(input => { input.value = 'inventory-' + button.dataset.addItem; });
    syncBuy(buys.length > 0 && buys[0].checked);
    dialog.showModal();
    show(button.dataset.addTabOpen || stored.get());
  };

  // Поиск: только совпавшие строки, группы раскрыты; Enter не должен добавить первую строку
  dialog.querySelectorAll('[data-add-search]').forEach(search => {
    const form = search.closest('form');
    search.addEventListener('keydown', e => { if (e.key === 'Enter') e.preventDefault(); });
    search.addEventListener('input', () => {
      const query = search.value.trim().toLowerCase();
      form.querySelectorAll('.add-entry').forEach(entry => { entry.hidden = !entry.dataset.search.includes(query); });
      form.querySelectorAll('[data-add-group]').forEach(group => {
        group.open = query !== '';
        group.hidden = query !== '' && !group.querySelector('.add-entry:not([hidden])');
      });
    });
  });
  // «Купить»: одна галочка на всех вкладках, запоминается; что не по карману — приглушено
  const buyKey = 'charka.addItem.buy';
  const buys = [...dialog.querySelectorAll('[data-add-buy]')];
  const syncBuy = checked => {
    buys.forEach(input => { input.checked = checked; });
    dialog.querySelectorAll('[data-add-form=catalog]').forEach(form => {
      const quantity = Number(form.querySelector('input[name=quantity]').value) || 1;
      form.querySelectorAll('.add-entry').forEach(entry => {
        entry.classList.toggle('is-dear', checked && Number(entry.dataset.cost) * quantity > Number(buys[0].dataset.balance));
      });
    });
  };
  buys.forEach(input => input.addEventListener('change', () => {
    try { localStorage.setItem(buyKey, input.checked ? '1' : ''); } catch (e) { /* только удобство */ }
    syncBuy(input.checked);
  }));
  dialog.querySelectorAll('[data-add-form=catalog] input[name=quantity]').forEach(input =>
    input.addEventListener('input', () => syncBuy(buys.length > 0 && buys[0].checked)));
  if (buys.length) {
    let remembered = false;
    try { remembered = localStorage.getItem(buyKey) === '1'; } catch (e) { /* без памяти — не покупать */ }
    syncBuy(remembered);
  }
  show(stored.get());
}

// Выбор из нескольких вариантов у фишки: второе нажатие мимо закрывает меню
document.addEventListener('click', e => {
  document.querySelectorAll('details[data-menu][open]').forEach(menu => {
    if (!menu.contains(e.target)) menu.open = false;
  });
});

// Закрытие уведомлений
document.addEventListener('click', e => {
  const close = e.target.closest('.alert-close');
  if (close) close.closest('.alert').remove();
  // A-01: плашка «↶ Отменить»
  const undoClose = e.target.closest('[data-undo-close]');
  if (undoClose) undoClose.closest('.undo-toast').remove();
});

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

// Поле боя (M-02..M-05, M-13): рисуется по JSON карты в своём контейнере, чужие изменения приходят опросом с ETag.
// Состояние вне initPage(): масштаб, сдвиг и выбранная фишка переживают обновление страницы на месте
const SVG_NS = 'http://www.w3.org/2000/svg';
const CELL = 32;
const POINTER_SHOW_MS = 4000;
const battle = {
  url: null, data: null, etag: null, timer: null, view: null, selected: undefined, reach: null, reachFor: null, vision: null,
  rulerMode: false, ruler: null, lastChange: 0, dragging: false, pointerMode: false, adding: false, placing: null, seen: new Map(), turn: null, stage: null
};

const svgEl = (name, attrs, parent) => {
  const el = document.createElementNS(SVG_NS, name);
  Object.entries(attrs || {}).forEach(([key, value]) => el.setAttribute(key, value));
  if (parent) parent.appendChild(el);
  return el;
};

// «Я — …» одно на кампанию: им подписаны сообщения чата и выбрана своя фишка
const meKey = url => {
  const match = /\/campaign\/(\d+)/.exec(url || '');
  return 'charka.me.campaign.' + (match ? match[1] : '');
};
const battleMe = () => {
  try { return localStorage.getItem(meKey(battle.url)) || ''; } catch (e) { return ''; }
};

// A-15: режим мастера на этом устройстве — cookie, её же проверяет сервер (GmMode). «За NPC» — только свои NPC и по бегу
const GM_MODE_COOKIE = 'charka.gmMode';
const gmModeNpc = () => document.cookie.split('; ').includes(GM_MODE_COOKIE + '=npc');
const battleGm = () => Boolean(battle.stage && battle.stage.dataset.gm === 'true');
// мастер в «За NPC»: фишки игроков не выбираются и не двигаются
const npcOnly = () => battleGm() && gmModeNpc();
const lockedToken = token => Boolean(token && npcOnly() && !token.npc);
function applyGmMode() {
  const npc = gmModeNpc();
  document.querySelectorAll('[data-gm-mode]').forEach(button => {
    button.textContent = npc ? '👹 За NPC' : '👁 Бог';
    button.classList.toggle('is-active', npc);
    button.title = npc
      ? 'За NPC: ходите только своими NPC и по бегу. Нажмите — режим «Бог»'
      : 'Бог: любые фишки, Shift — без лимита бега, ＋ Фишка, правка карты. Нажмите — режим «За NPC»';
  });
  document.querySelectorAll('[data-god-only]').forEach(el => { el.hidden = npc; });
}

// Мера «прямая»: та же формула, что MapRules.distance на сервере
const battleMeters = (x1, y1, x2, y2) => Math.round(Math.hypot(x2 - x1, y2 - y1) * battle.data.doc.cell);

const tokenById = id => battle.data && battle.data.tokens.find(token => token.id === id);

// M-22: уровень многоуровневой карты как плоский документ — та же формула, что MapDoc.level
const levelDoc = (doc, level) => {
  if (!level || !doc.levels || !doc.levels.length) return doc;
  const floor = doc.levels[level - 1];
  if (!floor) return doc;
  return { ...doc, walls: floor.walls, cells: floor.cells, stamps: floor.stamps, labels: floor.labels, zones: [], bg: null };
};
const levelName = (doc, level) => level ? doc.levels[level - 1].name : (doc.ground || '1 этаж');
const levelCount = doc => 1 + ((doc.levels && doc.levels.length) || 0);
// какой уровень показан: выбранный вкладкой, иначе уровень выбранной фишки
const battleLevel = () => {
  const count = battle.data ? levelCount(battle.data.doc) : 1;
  return battle.level !== undefined && battle.level < count ? battle.level : 0;
};

function initBattle() {
  const stage = document.querySelector('[data-battle]');
  if (!stage) {
    clearTimeout(battle.timer);
    battle.stage = null;
    return;
  }
  if (battle.url !== stage.dataset.battle) {
    Object.assign(battle, { url: stage.dataset.battle, data: null, etag: null, view: null, selected: undefined, reach: null, reachFor: null, vision: null });
  }
  battle.stage = stage;
  battle.turn = stage.dataset.turn;
  const wrap = stage.closest('[data-battle-wrap]');
  if (wrap) {
    // на телефоне карта свёрнута, пока её не открыли
    let stored = null;
    try { stored = localStorage.getItem('charka.battle.open'); } catch (e) { /* только удобство */ }
    wrap.open = stored !== null ? stored === '1' : innerWidth >= 768;
    wrap.addEventListener('toggle', () => {
      try { localStorage.setItem('charka.battle.open', wrap.open ? '1' : '0'); } catch (e) { /* только удобство */ }
      if (wrap.open) renderBattle();
    });
  }
  const me = document.querySelector('[data-battle-me]');
  if (me) {
    me.value = battleMe();
    if (me.selectedIndex < 0) me.value = '';
    me.addEventListener('change', () => {
      try { localStorage.setItem(meKey(battle.url), me.value); } catch (e) { /* только удобство */ }
      battle.selected = undefined;
      renderBattle();
    });
  }
  const modeButton = document.querySelector('[data-gm-mode]');
  if (modeButton) {
    modeButton.addEventListener('click', () => {
      const npc = !gmModeNpc();
      document.cookie = GM_MODE_COOKIE + '=' + (npc ? 'npc' : 'god') + '; path=/; max-age=31536000; SameSite=Lax';
      battle.adding = false;
      const chosen = tokenById(battle.selected);
      if (npc && lockedToken(chosen)) battle.selected = undefined;
      applyGmMode();
      battleHint(npc ? 'За NPC: двигаются только NPC, по бегу' : 'Бог: любые фишки, Shift — без лимита бега');
      renderBattle();
    });
  }
  applyGmMode();
  // «＋ Фишка» мастера: режим остаётся включённым — несколько бандитов подряд
  const addButton = document.querySelector('[data-battle-add]');
  if (addButton) {
    addButton.classList.toggle('is-active', battle.adding);
    addButton.addEventListener('click', () => {
      battle.adding = !battle.adding;
      battle.pointerMode = false;
      battle.rulerMode = false;
      battle.ruler = null;
      const ruler = document.querySelector('[data-battle-ruler]');
      if (ruler) ruler.classList.remove('is-active');
      addButton.classList.toggle('is-active', battle.adding);
      const pointer = document.querySelector('[data-battle-pointer]');
      if (pointer) pointer.classList.remove('is-active');
      battleHint(battle.adding ? 'Нажимайте клетки: каждое нажатие ставит фишку. Ещё раз «＋ Фишка» — выключить' : '');
    });
  }
  // KF-05: «＋ Предмет» мастера — как «＋ Фишка»: режим остаётся, каждое нажатие клетки кладёт выбранный предмет
  const lootButton = document.querySelector('[data-battle-loot]');
  if (lootButton) {
    lootButton.classList.toggle('is-active', !!battle.looting);
    lootButton.addEventListener('click', () => {
      battle.looting = !battle.looting;
      battle.adding = false;
      battle.pointerMode = false;
      battle.rulerMode = false;
      battle.ruler = null;
      document.querySelectorAll('[data-battle-pointer], [data-battle-add], [data-battle-ruler]').forEach(other => other.classList.remove('is-active'));
      lootButton.classList.toggle('is-active', battle.looting);
      battleHint(battle.looting ? 'Нажимайте клетки: каждое нажатие кладёт предмет. Ещё раз «＋ Предмет» — выключить' : '');
    });
    if (addButton) addButton.addEventListener('click', () => { battle.looting = false; lootButton.classList.remove('is-active'); });
  }
  // R-18a: линейка — тянуть от клетки к клетке, расстояние по мере системы; видна только на этом устройстве
  const rulerButton = document.querySelector('[data-battle-ruler]');
  if (rulerButton) {
    rulerButton.classList.toggle('is-active', battle.rulerMode);
    rulerButton.addEventListener('click', () => {
      battle.rulerMode = !battle.rulerMode;
      battle.pointerMode = false;
      battle.adding = false;
      if (!battle.rulerMode) battle.ruler = null;
      document.querySelectorAll('[data-battle-pointer], [data-battle-add]').forEach(other => other.classList.remove('is-active'));
      rulerButton.classList.toggle('is-active', battle.rulerMode);
      battleHint(battle.rulerMode ? 'Тяните от клетки к клетке — расстояние в метрах. Ещё раз «📏» — выключить' : '');
      renderBattle();
    });
  }
  const pointerButton = document.querySelector('[data-battle-pointer]');
  if (pointerButton) {
    pointerButton.classList.toggle('is-active', battle.pointerMode);
    pointerButton.addEventListener('click', () => {
      battle.pointerMode = !battle.pointerMode;
      battle.adding = false;
      battle.rulerMode = false;
      battle.ruler = null;
      if (rulerButton) rulerButton.classList.remove('is-active');
      pointerButton.classList.toggle('is-active', battle.pointerMode);
      if (addButton) addButton.classList.remove('is-active');
      battleHint(battle.pointerMode ? 'Нажмите клетку — её увидят все. От своей фишки можно протянуть стрелку' : '');
    });
  }
  document.querySelectorAll('[data-battle-zoom]').forEach(button => {
    button.addEventListener('click', () => {
      const factor = Number(button.dataset.battleZoom);
      if (!battle.view) return;
      if (factor === 0) battle.view = null;
      else zoomBattle(factor, battle.view.x + battle.view.w / 2, battle.view.y + battle.view.h / 2);
      renderBattle();
    });
  });
  // R-17: портрет в карусели очереди выбирает фишку на карте
  document.querySelectorAll('[data-select-token]').forEach(button => {
    button.addEventListener('click', () => {
      const id = Number(button.dataset.selectToken);
      if (!tokenById(id)) {
        battleError('Фишки нет на карте');
        return;
      }
      selectToken(id);
      if (innerWidth < 900) stage.scrollIntoView({ block: 'center', behavior: 'smooth' });
    });
  });
  if (battle.data) renderBattle();
  pollBattle();
}

const battleHint = text => {
  const hint = document.querySelector('[data-battle-hint]');
  if (hint) {
    hint.textContent = text || hint.dataset.default || '';
    hint.classList.toggle('is-error', false);
  }
};

const battleError = text => {
  const hint = document.querySelector('[data-battle-hint]');
  if (!hint) return;
  hint.textContent = text;
  hint.classList.add('is-error');
};

const scheduleBattle = delay => {
  clearTimeout(battle.timer);
  battle.timer = setTimeout(pollBattle, delay);
};

// Опрос: раз в секунду во время боя, раз в 5 с после минуты без изменений, на паузе, пока вкладка скрыта или фишку тянут
async function pollBattle() {
  if (!battle.stage || !battle.stage.isConnected) return;
  if (document.hidden || battle.dragging) {
    scheduleBattle(1000);
    return;
  }
  const url = battle.url;
  try {
    const response = await fetch(url, { cache: 'no-store', headers: battle.etag ? { 'If-None-Match': battle.etag } : {} });
    if (url !== battle.url) return;
    if (response.status === 200) {
      battle.etag = response.headers.get('ETag');
      battle.data = await response.json();
      battle.lastChange = Date.now();
      if (battle.reachFor) loadReach(battle.reachFor);
      renderBattle();
      checkBattleTurn();
    } else if (response.status === 404) {
      return;
    }
  } catch (e) { /* сеть пропала: попробуем позже */ }
  scheduleBattle(Date.now() - battle.lastChange > 60000 ? 5000 : 1000);
}

document.addEventListener('visibilitychange', () => { if (!document.hidden && battle.stage) pollBattle(); });

// Строки трекера не трогаем (в них открытые формы): если сменился ход или раунд — полоса с предложением обновить
function checkBattleTurn() {
  const bar = document.querySelector('[data-battle-turn]');
  if (!bar || !battle.data) return;
  bar.hidden = battle.data.turn === battle.turn;
}

async function loadReach(id) {
  battle.reachFor = id;
  try {
    const response = await fetch(battle.url + '/participants/' + id + '/reach', { cache: 'no-store' });
    if (!response.ok) return;
    const reach = await response.json();
    if (battle.reachFor !== id) return;
    // клетка → метры пути: подсказка при переносе показывает бег, а не прямую
    battle.reach = { meters: reach.meters, run: reach.run, cells: new Map(reach.cells.map(cell => [cell[0] + ',' + cell[1], cell[2]])) };
    renderBattle();
  } catch (e) { /* без области: только подсказка */ }
  loadVision(id);
}

// Поле видимости выбранной фишки: что вне его — в тени; чужие фишки помечены «не видна» и «частично»
async function loadVision(id) {
  try {
    const response = await fetch(battle.url + '/participants/' + id + '/view', { cache: 'no-store' });
    if (!response.ok || battle.reachFor !== id) return;
    const view = await response.json();
    if (battle.reachFor !== id) return;
    battle.vision = { id, w: view.w, cells: view.cells, targets: new Map(view.targets.map(target => [target.id, target])) };
    renderBattle();
  } catch (e) { /* без тени */ }
}

function selectToken(id) {
  battle.selected = id;
  const chosen = tokenById(id);
  if (chosen) battle.level = chosen.level || 0;
  battle.reach = null;
  battle.reachFor = null;
  battle.vision = null;
  if (id) loadReach(id);
  renderBattle();
}

const toSvgPoint = (svg, clientX, clientY) => {
  const point = svg.createSVGPoint();
  point.x = clientX;
  point.y = clientY;
  return point.matrixTransform(svg.getScreenCTM().inverse());
};

const zoomBattle = (factor, cx, cy) => {
  const view = battle.view;
  const w = Math.min(Math.max(view.w / factor, CELL * 4), CELL * 400);
  const h = w * view.h / view.w;
  battle.view = { x: cx - (cx - view.x) * w / view.w, y: cy - (cy - view.y) * h / view.h, w, h };
};

const MATERIAL_COLORS = { WALL: '#c7cfdb', DOOR: '#b7834a', WINDOW: '#7dd3fc', OBJECT: '#64748b', TERRAIN: '#3f3a2e' };
// местность своего цвета; остальная — общим цветом TERRAIN
const TERRAIN_COLORS = { water: '#1e40af', asphalt: '#2a303b', grass: '#1f4d2b', smoke: '#9ca3af', void: '#020305', stairs: '#6b5b3e' };
// M-23: взрывоопасное — своим цветом, чтобы его было видно издалека
const EXPLOSIVE_COLOR = '#c2410c';

// Карта без фишек: пол, сетка, зоны, местность, предметы, стены, подписи. Общая для поля боя и редактора.
// under(svg) рисует то, что ложится под стены (досягаемость); editing — показать проёмы, двери не нажимаются
function drawMapLayers(svg, doc, materials, objects, under, editing, backgroundUrl) {
  const width = doc.w * CELL;
  const height = doc.h * CELL;
  const defs = svgEl('defs', {}, svg);
  const pattern = svgEl('pattern', { id: 'battle-grid', width: CELL, height: CELL, patternUnits: 'userSpaceOnUse' }, defs);
  svgEl('path', { d: `M ${CELL} 0 L 0 0 0 ${CELL}`, class: 'battle-gridline' }, pattern);
  svgEl('rect', { x: 0, y: 0, width, height, class: 'battle-floor' }, svg);
  // подложка (M-11) под сеткой: стены обводятся поверх
  if (backgroundUrl && doc.bg) {
    svgEl('image', {
      href: backgroundUrl, x: doc.bg.x * CELL, y: doc.bg.y * CELL, width: doc.bg.w * CELL, height: doc.bg.h * CELL,
      preserveAspectRatio: 'none', opacity: doc.bg.opacity || 0.8, class: 'battle-background'
    }, svg);
  }
  svgEl('rect', { x: 0, y: 0, width, height, fill: 'url(#battle-grid)' }, svg);

  doc.zones.forEach(zone => svgEl('rect', {
    x: zone.x * CELL, y: zone.y * CELL, width: zone.w * CELL, height: zone.h * CELL, class: 'battle-zone ' + zone.kind
  }, svg));
  Object.entries(doc.cells).forEach(([key, code]) => {
    const [x, y] = key.split(',').map(Number);
    const material = materials.get(code);
    const kind = material && material.smoke ? ' battle-smoke' : material && material.difficult ? ' battle-difficult' : '';
    svgEl('rect', { x: x * CELL, y: y * CELL, width: CELL, height: CELL, class: 'battle-terrain' + kind, fill: TERRAIN_COLORS[code] || MATERIAL_COLORS.TERRAIN },
      svg).appendChild(svgEl('title', {})).textContent = (material ? material.label : code)
      + (material && material.difficult ? ' · трудная местность: бег вдвое дороже' : '')
      + (material && material.smoke ? ' · закрывает взгляд, стрельба вслепую' : '');
    if (code === 'stairs') {
      svgEl('text', { x: (x + 0.5) * CELL, y: (y + 0.5) * CELL, class: 'battle-stairs' }, svg).textContent = '⇅';
    }
  });
  doc.stamps.forEach(stamp => {
    const object = objects.get(stamp.code);
    if (!object) return;
    const rot = ((stamp.rot % 4) + 4) % 4;
    const w = rot % 2 === 0 ? object.w : object.h;
    const h = rot % 2 === 0 ? object.h : object.w;
    const group = svgEl('g', { class: 'battle-stamp' }, svg);
    svgEl('title', {}, group).textContent = object.label;
    for (let dy = 0; dy < h; dy++) {
      for (let dx = 0; dx < w; dx++) {
        const code = stampCell(object, rot, dx, dy);
        if (!code) continue;
        const material = materials.get(code);
        svgEl('rect', {
          x: (stamp.x + dx) * CELL + 1, y: (stamp.y + dy) * CELL + 1, width: CELL - 2, height: CELL - 2, rx: 3,
          fill: material && material.explosive ? EXPLOSIVE_COLOR : MATERIAL_COLORS.OBJECT, 'fill-opacity': material && material.sp >= 30 ? 0.95 : 0.7
        }, group);
        if (material && material.explosive && !editing) {
          svgEl('text', { x: (stamp.x + dx + 0.5) * CELL, y: (stamp.y + dy + 0.5) * CELL, class: 'battle-explosive' }, group).textContent = '💥';
        }
      }
    }
    svgEl('rect', { x: stamp.x * CELL, y: stamp.y * CELL, width: w * CELL, height: h * CELL, class: 'battle-stamp-outline' }, group);
  });

  if (under) under(svg);

  Object.entries(doc.walls).forEach(([key, value]) => {
    const [x, y, dir] = key.split(',');
    const cx = Number(x) * CELL;
    const cy = Number(y) * CELL;
    const [kind, , state] = value.split(':');
    if (value === 'gap' && !editing) return;
    const line = svgEl('line', dir === 'h'
      ? { x1: cx, y1: cy, x2: cx + CELL, y2: cy }
      : { x1: cx, y1: cy, x2: cx, y2: cy + CELL }, svg);
    const type = value === 'gap' ? 'gap' : kind === 'door' ? 'door' : kind === 'window' ? 'window' : 'wall';
    line.setAttribute('class', 'battle-edge ' + type + (state === 'open' ? ' open' : ''));
    const material = materials.get(type === 'wall' ? kind : value.split(':')[1]);
    const title = svgEl('title', {}, line);
    title.textContent = (material ? material.label + (material.sp ? ' SP ' + material.sp : '') : value)
      + (type === 'door' && !editing ? (state === 'open' ? ' · открыта, нажмите — закрыть' : ' · закрыта, нажмите — открыть') : '');
    if (!editing) {
      // M-23: правая кнопка или долгое нажатие по грани — выбить, разбить; дверь ещё и открывается нажатием
      line.dataset.edge = key;
      line.dataset.edgeKind = type;
    }
  });
  doc.labels.forEach(label => {
    svgEl('text', { x: (label.x + 0.5) * CELL, y: (label.y + 0.5) * CELL, class: 'battle-label' }, svg).textContent = label.text;
  });}

function renderBattle() {
  const stage = battle.stage;
  const data = battle.data;
  if (!stage || !data || !stage.isConnected) return;
  const level = battleLevel();
  const doc = levelDoc(data.doc, level);
  const width = doc.w * CELL;
  const height = doc.h * CELL;
  if (!battle.view) battle.view = { x: -CELL / 2, y: -CELL / 2, w: width + CELL, h: height + CELL };
  // высота поля — по пропорциям карты: широкая улица на телефоне не оставляет пустоты сверху и снизу
  // R-14: на экране боя поле занимает всю середину, высоту задаёт раскладка
  if (stage.clientWidth && !('battleFill' in stage.dataset)) {
    stage.style.height = Math.round(Math.min(innerHeight * 0.7, Math.max(240, stage.clientWidth * (doc.h + 1) / (doc.w + 1)))) + 'px';
  }
  const materials = new Map(data.materials.map(material => [material.code, material]));
  const objects = new Map(data.objects.map(object => [object.code, object]));
  const me = battleMe();
  // фишка ещё не выбрана: своя по «Я — …», у мастера — чей ход; null — выбор снят нажатием
  if (battle.selected && !tokenById(battle.selected)) battle.selected = undefined;
  if (battle.selected === undefined) {
    battle.selected = null;
    const mine = data.tokens.find(token => String(token.character) === me && token.x !== null);
    const current = data.tokens.find(token => token.current && token.x !== null && !lockedToken(token));
    const chosen = mine || (me === '' ? current : null);
    if (chosen) {
      battle.selected = chosen.id;
      if (battle.level === undefined) battle.level = chosen.level || 0;
      loadReach(chosen.id);
    }
  }
  renderLevels();
  renderClimb();
  const selectedToken = tokenById(battle.selected);
  const onLevel = token => (token.level || 0) === level;

  const svg = svgEl('svg', {
    viewBox: [battle.view.x, battle.view.y, battle.view.w, battle.view.h].join(' '),
    class: 'battle-svg', role: 'img', 'aria-label': 'Карта боя', 'data-metric': data.metric
  });
  drawMapLayers(svg, doc, materials, objects, svg => {
    if (battle.reach && battle.selected && selectedToken && onLevel(selectedToken)) {
      const group = svgEl('g', { class: 'battle-reach' }, svg);
      battle.reach.cells.forEach((cost, key) => {
        const [x, y] = key.split(',').map(Number);
        svgEl('rect', { x: x * CELL, y: y * CELL, width: CELL, height: CELL }, group);
      });
    }
    // тень вне поля видимости: подряд идущие клетки строки — один прямоугольник
    // M-16: игроку без выбранной своей фишки — туман по всем его персонажам (сервер не отдал скрытые фишки)
    const own = battle.vision && battle.vision.id === battle.selected && battle.vision.cells && selectedToken && onLevel(selectedToken);
    const fogCells = data.fogLevels ? data.fogLevels[level] : data.fog;
    const vision = own ? battle.vision : fogCells ? { w: doc.w, cells: fogCells } : null;
    if (vision) {
      const fog = svgEl('g', { class: 'battle-fog' }, svg);
      const hatch = svgEl('pattern', { id: 'battle-fog-hatch', width: 10, height: 10, patternUnits: 'userSpaceOnUse', patternTransform: 'rotate(45)' }, fog);
      svgEl('rect', { width: 10, height: 10, class: 'battle-fog-shade' }, hatch);
      svgEl('line', { x1: 0, y1: 0, x2: 0, y2: 10, class: 'battle-fog-line' }, hatch);
      for (let y = 0; y * vision.w < vision.cells.length; y++) {
        let start = -1;
        for (let x = 0; x <= vision.w; x++) {
          const dark = x < vision.w && vision.cells[y * vision.w + x] === '0';
          if (dark && start < 0) start = x;
          if (!dark && start >= 0) {
            svgEl('rect', { x: start * CELL, y: y * CELL, width: (x - start) * CELL, height: CELL }, fog);
            start = -1;
          }
        }
      }
    }
  }, false, level === 0 ? data.backgroundUrl : null);

  const now = Date.now();
  data.pointers.forEach(pointer => {
    if (!battle.seen.has(pointer.id)) battle.seen.set(pointer.id, now);
    if (now - battle.seen.get(pointer.id) > POINTER_SHOW_MS) return;
    const group = svgEl('g', { class: 'battle-pointer', style: '--who:' + battleColor(pointer.who) }, svg);
    if (pointer.fromX !== null && pointer.fromX !== undefined) {
      svgEl('line', {
        x1: (pointer.fromX + 0.5) * CELL, y1: (pointer.fromY + 0.5) * CELL, x2: (pointer.x + 0.5) * CELL, y2: (pointer.y + 0.5) * CELL,
        class: 'battle-pointer-arrow'
      }, group);
    }
    svgEl('circle', { cx: (pointer.x + 0.5) * CELL, cy: (pointer.y + 0.5) * CELL, r: CELL * 0.45, class: 'battle-pointer-ring' }, group);
    svgEl('text', { x: (pointer.x + 0.5) * CELL, y: (pointer.y - 0.15) * CELL, class: 'battle-pointer-label' }, group).textContent = pointer.who;
  });

  const seen = battle.vision && battle.vision.id === battle.selected ? battle.vision.targets : null;
  // M-22: на показанном уровне — все фишки; с уровня ниже — те, что видны сквозь пустоту (притушены)
  const throughVoid = token => (token.level || 0) < level && doc.cells[token.x + ',' + token.y] === 'void';
  data.tokens.filter(token => token.x !== null && (onLevel(token) || throughVoid(token))).forEach(token => {
    const sight = seen && seen.get(token.id);
    const group = svgEl('g', {
      class: 'battle-token' + (token.npc ? ' npc' : ' player') + (onLevel(token) ? '' : ' is-below') + (token.current ? ' is-current' : '') + (token.out ? ' is-out' : '')
        + (token.done ? ' is-done' : '') + (token.alarming ? ' is-alarming' : '') + (token.id === battle.selected ? ' is-selected' : '')
        + (String(token.character) === me ? ' is-me' : '')
        + (sight && sight.visibility === 'none' ? ' is-unseen' : '') + (sight && sight.visibility === 'partial' ? ' is-partial' : ''),
      transform: `translate(${(token.x + 0.5) * CELL} ${(token.y + 0.5) * CELL})`,
      tabindex: 0, 'data-token': token.id, role: 'button',
      'aria-label': token.name + (token.status ? ', ' + token.status : '')
    }, svg);
    svgEl('title', {}, group).textContent = token.name + (onLevel(token) ? '' : ' · внизу: ' + levelName(data.doc, token.level || 0))
      + (token.status ? ' · ' + token.status : '')
      + (sight ? ' · ' + (sight.visibility === 'none' ? (sight.canShoot ? 'не видна, только вслепую' : 'не видна')
        : sight.visibility === 'partial' ? 'видна частично' : 'видна') + (sight.cover ? ', укрытие: ' + sight.cover : '') : '');
    svgEl('circle', { r: CELL * 0.46, class: 'battle-token-ring' }, group);
    svgEl('circle', { r: CELL * 0.38, class: 'battle-token-body' }, group);
    svgEl('text', { class: 'battle-token-text', dy: '0.35em' }, group).textContent = token.initials;
    // щиток: цель за укрытием или видна частично
    if (sight && sight.visibility !== 'none' && (sight.cover || sight.visibility === 'partial')) {
      svgEl('path', { d: 'M 0 -6 L 6 -3.5 L 6 1 Q 6 5.5 0 8 Q -6 5.5 -6 1 L -6 -3.5 Z', class: 'battle-token-cover',
        transform: `translate(${CELL * 0.33} ${-CELL * 0.33})` }, group);
    }
  });

  // KF-05: предметы на карте — значок в углу клетки поверх фишки: нажатие — «Подобрать»
  (data.loot || []).filter(loot => (loot.level || 0) === level).forEach(loot => {
    const group = svgEl('g', { class: 'battle-loot', transform: `translate(${(loot.x + 0.78) * CELL} ${(loot.y + 0.22) * CELL})`,
      tabindex: 0, role: 'button', 'data-loot': loot.id, 'aria-label': 'Предмет: ' + loot.name }, svg);
    svgEl('title', {}, group).textContent = loot.name + (loot.quantity > 1 ? ' ×' + loot.quantity : '') + ' — нажмите, чтобы подобрать';
    svgEl('rect', { x: -CELL * 0.2, y: -CELL * 0.2, width: CELL * 0.4, height: CELL * 0.4, rx: 3, class: 'battle-loot-box' }, group);
    svgEl('text', { class: 'battle-loot-icon', dy: '0.35em' }, group).textContent = '📦';
  });

  drawRuler(svg);
  stage.replaceChildren(svg);
  wireBattleSvg(svg);
  renderTray();
}

// M-22: вкладки уровней: название и сколько фишек там стоит
function renderLevels() {
  const holder = document.querySelector('[data-battle-levels]');
  if (!holder || !battle.data) return;
  const doc = battle.data.doc;
  const count = levelCount(doc);
  holder.hidden = count < 2;
  holder.replaceChildren();
  if (count < 2) return;
  const shown = battleLevel();
  for (let level = count - 1; level >= 0; level--) {
    const button = document.createElement('button');
    button.type = 'button';
    const here = battle.data.tokens.filter(token => token.x !== null && (token.level || 0) === level).length;
    button.className = 'btn btn-sm' + (level === shown ? ' btn-primary' : ' btn-ghost');
    button.textContent = levelName(doc, level) + (here ? ' · ' + here : '');
    button.addEventListener('click', () => {
      battle.level = level;
      renderBattle();
    });
    holder.appendChild(button);
  }
}

// M-22: выбранная фишка стоит на лестнице — кнопки «⇅ На 2 этаж»; та же проверка, что LevelMap.stairsFrom
function renderClimb() {
  const holder = document.querySelector('[data-battle-climb]');
  if (!holder || !battle.data) return;
  holder.replaceChildren();
  const token = tokenById(battle.selected);
  const doc = battle.data.doc;
  const stairsAt = level => level >= 0 && level < levelCount(doc) && levelDoc(doc, level).cells[token.x + ',' + token.y] === 'stairs';
  const from = token ? token.level || 0 : 0;
  const targets = token && token.x !== null && stairsAt(from) ? [from + 1, from - 1].filter(stairsAt) : [];
  holder.hidden = targets.length === 0 || lockedToken(token);
  targets.forEach(level => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm btn-primary';
    button.textContent = (level > from ? '⇡ ' : '⇣ ') + levelName(doc, level);
    button.title = 'По лестнице: ' + token.name + ' → ' + levelName(doc, level) + ' (бег за подъём — вдвое больше высоты)';
    button.addEventListener('click', async () => {
      try {
        const response = await fetch(battle.url + '/participants/' + token.id + '/climb', {
          method: 'POST', body: new URLSearchParams({ level })
        });
        const text = await response.text();
        if (!response.ok) {
          battleError(text);
          return;
        }
        battle.level = level;
        battleHint(token.name + ' → ' + text);
        loadReach(token.id);
      } catch (e) {
        battleError('Нет связи с сервером');
      }
      battle.lastChange = Date.now();
      pollBattle();
    });
    holder.appendChild(button);
  });
}

// Линейка поверх фишек: от центра клетки к центру клетки, подпись — метры по мере системы и клетки
function drawRuler(svg) {
  const old = svg.querySelector('.battle-ruler');
  if (old) old.remove();
  const ruler = battle.ruler;
  if (!ruler) return;
  const group = svgEl('g', { class: 'battle-ruler' }, svg);
  const [x1, y1, x2, y2] = [(ruler.from.x + 0.5) * CELL, (ruler.from.y + 0.5) * CELL, (ruler.to.x + 0.5) * CELL, (ruler.to.y + 0.5) * CELL];
  svgEl('line', { x1, y1, x2, y2, class: 'battle-ruler-line' }, group);
  svgEl('circle', { cx: x1, cy: y1, r: 4, class: 'battle-ruler-end' }, group);
  svgEl('circle', { cx: x2, cy: y2, r: 4, class: 'battle-ruler-end' }, group);
  const meters = battleMeters(ruler.from.x, ruler.from.y, ruler.to.x, ruler.to.y);
  const cells = Math.max(Math.abs(ruler.to.x - ruler.from.x), Math.abs(ruler.to.y - ruler.from.y));
  const label = svgEl('text', { x: (x1 + x2) / 2, y: (y1 + y2) / 2 - 8, class: 'battle-ruler-label' }, group);
  label.textContent = meters + ' м';
  battleHint('📏 ' + meters + ' м · ' + cells + ' кл. по большей стороне');
}

// Клетка предмета, повёрнутого на rot четвертей: та же формула, что MapObject.cellAt
const stampCell = (object, rot, dx, dy) => {
  let sx = dx;
  let sy = dy;
  if (rot === 1) { sx = dy; sy = object.h - 1 - dx; }
  if (rot === 2) { sx = object.w - 1 - dx; sy = object.h - 1 - dy; }
  if (rot === 3) { sx = object.w - 1 - dy; sy = dx; }
  return object.cells[sy * object.w + sx];
};

const battleColor = name => {
  let hash = 0;
  for (const char of String(name)) hash = (hash * 31 + char.charCodeAt(0)) % 360;
  return `hsl(${hash} 85% 62%)`;
};

function renderTray() {
  const tray = document.querySelector('[data-battle-tray]');
  if (!tray || !battle.data) return;
  const off = battle.data.tokens.filter(token => token.x === null && !token.out);
  tray.hidden = off.length === 0;
  tray.replaceChildren();
  if (!off.length) return;
  const label = document.createElement('span');
  label.className = 'label';
  label.textContent = 'Не на карте:';
  tray.appendChild(label);
  off.forEach(token => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm' + (battle.placing === token.id ? ' btn-primary' : ' btn-ghost');
    button.textContent = token.name;
    button.addEventListener('click', () => {
      battle.placing = battle.placing === token.id ? null : token.id;
      battleHint(battle.placing ? 'Нажмите клетку для ' + token.name : '');
      renderTray();
    });
    tray.appendChild(button);
  });
}

async function moveToken(token, x, y, free) {
  const before = { x: token.x, y: token.y, level: token.level };
  // M-22: на другой уровень — только из лотка или мастер без ограничения; иначе сервер откажет
  if (token.x === null || free) token.level = battleLevel();
  token.x = x;
  token.y = y;
  renderBattle();
  try {
    const response = await fetch(battle.url + '/participants/' + token.id + '/move', {
      method: 'POST', body: new URLSearchParams({ x, y, free: Boolean(free), level: battleLevel() })
    });
    if (!response.ok) {
      Object.assign(token, before);
      renderBattle();
      battleError(await response.text());
      return;
    }
    battleHint('');
    if (battle.reachFor === token.id) loadReach(token.id);
  } catch (e) {
    Object.assign(token, before);
    renderBattle();
    battleError('Нет связи с сервером: фишка не сдвинулась');
  }
  battle.lastChange = Date.now();
  pollBattle();
}

// Новая строка в трекере нужна сразу (её действия, оружие): страница обновляется на месте, карта и режим остаются
async function addToken(x, y) {
  const who = document.querySelector('[data-battle-add-who]');
  if (!who || !who.value) return;
  const [kind, value] = [who.value.slice(0, 2), who.value.slice(2)];
  const params = new URLSearchParams({ x, y, level: battleLevel() });
  if (kind === 'c:') params.set('character', value);
  else params.set('template', value);
  try {
    const response = await fetch(battle.url + '/tokens', { method: 'POST', body: params });
    const text = await response.text();
    if (!response.ok) {
      battleError(text);
      return;
    }
    battleHint(text + ' в бою');
    await visit(location.href);
  } catch (e) {
    battleError('Нет связи с сервером');
  }
}

// KF-05: предметы на карте. Адрес — рядом с картой боя: /campaign/{id}/encounter/loot
const lootUrl = () => battle.url.replace(/\/map$/, '/loot');

async function lootPost(path, params, done) {
  try {
    const response = await fetch(lootUrl() + path, { method: 'POST', body: new URLSearchParams(params) });
    const text = await response.text();
    if (!response.ok) {
      battleError(text || 'Не получилось');
      return;
    }
    battleHint(text);
    // строка участника получает новое оружие, отмена — плашку: страница обновляется на месте, карта и режим остаются
    battle.lastChange = Date.now();
    if (done) await visit(location.href);
    else pollBattle();
  } catch (e) {
    battleError('Нет связи с сервером');
  }
}

function placeLoot(x, y) {
  const what = document.querySelector('[data-battle-loot-what]');
  if (!what || !what.value) return;
  lootPost('', { key: what.value, x, y, level: battleLevel() }, false);
}

// Кто может подобрать: фишки на этой клетке и соседних, на том же уровне. Игрок — свои и выбранная, мастер — любые;
// выбранная и чей ход — первыми
function lootMenu(lootId, event) {
  const loot = (battle.data.loot || []).find(candidate => candidate.id === lootId);
  if (!loot) return;
  const gm = battle.stage && battle.stage.dataset.gm === 'true';
  const me = battleMe();
  const near = battle.data.tokens.filter(token => token.x !== null && !token.out && (token.level || 0) === (loot.level || 0)
      && Math.max(Math.abs(token.x - loot.x), Math.abs(token.y - loot.y)) <= 1
      && (gm ? !lockedToken(token) : (String(token.character) === me || token.id === battle.selected)))
    .sort((a, b) => (b.id === battle.selected) - (a.id === battle.selected) || (b.current === true) - (a.current === true));
  const actions = near.map(token => ['Подобрать: ' + token.name, null,
    () => lootPost('/' + loot.id + '/take', { participant: token.id }, true)]);
  if (gm) actions.push(['Убрать с карты', null, () => lootPost('/' + loot.id + '/remove', {}, false)]);
  const title = '📦 ' + loot.name + (loot.quantity > 1 ? ' ×' + loot.quantity : '');
  if (!actions.length) {
    battleHint(title + ': подойдите вплотную, чтобы подобрать');
    return;
  }
  battleMenuAt(event, title, actions);
}

async function sendPointer(x, y, from) {
  const me = battleMe();
  const meToken = battle.data.tokens.find(token => String(token.character) === me);
  const params = new URLSearchParams({ x, y, who: meToken ? meToken.name : 'Мастер' });
  if (from) {
    params.set('fromX', from.x);
    params.set('fromY', from.y);
  }
  try {
    await fetch(battle.url + '/pointer', { method: 'POST', body: params });
  } catch (e) { /* указатель не важен */ }
  battle.lastChange = Date.now();
  pollBattle();
}

function wireBattleSvg(svg) {
  let press = null;
  let longPress = null;
  const cellAt = event => {
    const point = toSvgPoint(svg, event.clientX, event.clientY);
    return { x: Math.floor(point.x / CELL), y: Math.floor(point.y / CELL), point };
  };
  const inside = cell => cell.x >= 0 && cell.y >= 0 && cell.x < battle.data.doc.w && cell.y < battle.data.doc.h;

  svg.addEventListener('pointerdown', event => {
    if (event.button !== 0) return;
    const lootEl = event.target.closest('[data-loot]');
    const tokenEl = lootEl ? null : event.target.closest('[data-token]');
    const start = cellAt(event);
    // после захвата указателя pointerup приходит на сам svg: дверь запоминаем здесь
    const door = event.target.closest('[data-edge]');
    const pressed = tokenEl ? tokenById(Number(tokenEl.dataset.token)) : null;
    // A-15: в «За NPC» фишка игрока — только цель (долгое нажатие — атака), не тянется и не выбирается
    const locked = lockedToken(pressed);
    press = { tokenEl: locked ? null : tokenEl, token: locked ? null : pressed, locked: locked ? pressed : null, door: door && door.dataset.edge, doorKind: door && door.dataset.edgeKind,
      x: event.clientX, y: event.clientY, start, moved: false, ruler: battle.rulerMode && inside(start),
      loot: lootEl ? Number(lootEl.dataset.loot) : null };
    svg.setPointerCapture(event.pointerId);
    if (press.ruler) {
      battle.ruler = { from: { x: start.x, y: start.y }, to: { x: start.x, y: start.y } };
      drawRuler(svg);
      return;
    }
    if (!battle.pointerMode) {
      // долгое нажатие: по фишке — атака (телефон без правой кнопки), по пустой клетке — указатель для всех
      longPress = setTimeout(() => {
        if (!press || press.moved) return;
        const held = press;
        press = null;
        // долгое нажатие на выбранную или свою фишку — не атака по себе
        const own = held.token && (held.token.id === battle.selected || String(held.token.character) === battleMe());
        if (held.locked) attackMenu(held.locked, event);
        else if (held.token && !own) attackMenu(held.token, event);
        else if (!held.token && held.door) edgeMenu(held.door, held.doorKind, event);
        else if (!held.token && explosiveAt(start.x, start.y)) explosiveMenu(start.x, start.y, event);
        else if (inside(start)) sendPointer(start.x, start.y);
      }, 600);
    }
  });

  // правая кнопка по фишке — атака по ней
  svg.addEventListener('contextmenu', event => {
    event.preventDefault();
    const tokenEl = event.target.closest('[data-token]');
    if (tokenEl) {
      attackMenu(tokenById(Number(tokenEl.dataset.token)), event);
      return;
    }
    const edge = event.target.closest('[data-edge]');
    if (edge) {
      edgeMenu(edge.dataset.edge, edge.dataset.edgeKind, event);
      return;
    }
    const cell = cellAt(event);
    if (explosiveAt(cell.x, cell.y)) explosiveMenu(cell.x, cell.y, event);
  });

  svg.addEventListener('pointermove', event => {
    if (!press) return;
    if (press.ruler) {
      const cell = cellAt(event);
      if (inside(cell) && (cell.x !== battle.ruler.to.x || cell.y !== battle.ruler.to.y)) {
        battle.ruler.to = { x: cell.x, y: cell.y };
        drawRuler(svg);
      }
      return;
    }
    const dx = event.clientX - press.x;
    const dy = event.clientY - press.y;
    if (!press.moved && Math.hypot(dx, dy) < 5) return;
    press.moved = true;
    clearTimeout(longPress);
    if (press.token && !battle.pointerMode) {
      battle.dragging = true;
      const cell = cellAt(event);
      press.tokenEl.setAttribute('transform', `translate(${cell.point.x} ${cell.point.y})`);
      // M-16: бег за раунд — MA × 3 м по пути в обход стен (с. 98); сервер сверит, мастер с Shift ставит без ограничения
      const known = battle.reach && battle.reachFor === press.token.id && battle.reach.run;
      const path = known ? battle.reach.cells.get(cell.x + ',' + cell.y) : undefined;
      const free = event.shiftKey && !npcOnly();
      const far = known && path === undefined && !free;
      press.tokenEl.classList.toggle('is-far', Boolean(far));
      if (free) battleHint('Без ограничения бега (мастер)');
      else if (!known) battleHint(battleMeters(press.token.x, press.token.y, cell.x, cell.y) + ' м');
      else if (far) battleHint('Дальше бега: осталось ' + battle.reach.meters + ' из ' + battle.reach.run + ' м');
      else battleHint(path + ' м · останется ' + (battle.reach.meters - path) + ' из ' + battle.reach.run + ' м · атака после бега — второе действие, −3');
    } else if (!press.token && !battle.pointerMode) {
      const scale = battle.view.w / svg.getBoundingClientRect().width;
      battle.view.x -= dx * scale;
      battle.view.y -= dy * scale;
      press.x = event.clientX;
      press.y = event.clientY;
      svg.setAttribute('viewBox', [battle.view.x, battle.view.y, battle.view.w, battle.view.h].join(' '));
    }
  });

  const finish = event => {
    clearTimeout(longPress);
    if (!press) return;
    const current = press;
    press = null;
    battle.dragging = false;
    const cell = cellAt(event);
    if (current.ruler) return;
    if (battle.pointerMode) {
      if (!inside(cell)) return;
      const from = current.token && current.moved && current.token.x !== null ? { x: current.token.x, y: current.token.y } : null;
      sendPointer(cell.x, cell.y, from);
      return;
    }
    if (current.token && current.moved) {
      if (inside(cell) && (cell.x !== current.token.x || cell.y !== current.token.y)) moveToken(current.token, cell.x, cell.y, event.shiftKey && !npcOnly());
      else renderBattle();
      return;
    }
    if (current.moved) return;
    if (event.altKey && inside(cell)) {
      sendPointer(cell.x, cell.y);
      return;
    }
    if (current.locked) {
      battleHint('За NPC: ' + current.locked.name + ' — фишка игрока. Атака по ней — правой кнопкой или долгим нажатием');
      return;
    }
    if (current.door && current.doorKind === 'door' && !current.token) {
      toggleDoor(current.door);
      return;
    }
    if (current.loot && !battle.looting) {
      lootMenu(current.loot, event);
      return;
    }
    if (current.token) {
      tokenClick(current.token);
      return;
    }
    if (battle.looting && !npcOnly() && inside(cell)) {
      placeLoot(cell.x, cell.y);
      return;
    }
    if (battle.adding && !npcOnly() && inside(cell) && !current.token) {
      addToken(cell.x, cell.y);
      return;
    }
    if (battle.placing && inside(cell)) {
      const token = tokenById(battle.placing);
      battle.placing = null;
      if (token) moveToken(token, cell.x, cell.y);
      return;
    }
    closeBattleMenu();
  };
  svg.addEventListener('pointerup', finish);
  svg.addEventListener('pointercancel', () => {
    clearTimeout(longPress);
    press = null;
    battle.dragging = false;
    renderBattle();
  });

  svg.addEventListener('wheel', event => {
    event.preventDefault();
    const point = toSvgPoint(svg, event.clientX, event.clientY);
    zoomBattle(event.deltaY < 0 ? 1.15 : 1 / 1.15, point.x, point.y);
    svg.setAttribute('viewBox', [battle.view.x, battle.view.y, battle.view.w, battle.view.h].join(' '));
  }, { passive: false });

  // стрелки двигают выбранную фишку на клетку
  svg.querySelectorAll('[data-token]').forEach(el => {
    el.addEventListener('keydown', event => {
      const steps = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
      const token = tokenById(Number(el.dataset.token));
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        tokenClick(token);
        return;
      }
      // клавиатура: A — атаковать эту фишку выбранной
      if (event.key === 'a' || event.key === 'A' || event.key === 'ф' || event.key === 'Ф') {
        event.preventDefault();
        attackMenu(token, null);
        return;
      }
      const step = steps[event.key];
      if (!step || !token) return;
      event.preventDefault();
      moveToken(token, token.x + step[0], token.y + step[1]).then(() => {
        const again = battle.stage && battle.stage.querySelector(`[data-token="${token.id}"]`);
        if (again) again.focus();
      });
    });
  });
}

async function toggleDoor(edge) {
  try {
    const response = await fetch(battle.url + '/door', { method: 'POST', body: new URLSearchParams({ edge, level: battleLevel() }) });
    if (!response.ok) battleError(await response.text());
  } catch (e) {
    battleError('Нет связи с сервером');
  }
  battle.lastChange = Date.now();
  pollBattle();
}

// M-23: взрывоопасный предмет в клетке (бочка, баллон)
function explosiveAt(x, y) {
  if (!battle.data) return false;
  const materials = new Map(battle.data.materials.map(material => [material.code, material]));
  const objects = new Map(battle.data.objects.map(object => [object.code, object]));
  return levelDoc(battle.data.doc, battleLevel()).stamps.some(stamp => {
    const object = objects.get(stamp.code);
    if (!object) return false;
    const rot = ((stamp.rot % 4) + 4) % 4;
    const w = rot % 2 === 0 ? object.w : object.h;
    const h = rot % 2 === 0 ? object.h : object.w;
    if (x < stamp.x || y < stamp.y || x >= stamp.x + w || y >= stamp.y + h) return false;
    const material = materials.get(stampCell(object, rot, x - stamp.x, y - stamp.y));
    return Boolean(material && material.explosive);
  });
}

// Меню по месту нажатия: заголовок и кнопки действий [подпись, пояснение, что сделать]
function battleMenuAt(event, title, actions) {
  const menu = document.querySelector('[data-battle-menu]');
  if (!menu) return;
  menu.replaceChildren();
  const head = document.createElement('div');
  head.className = 'battle-menu-head';
  head.textContent = title;
  menu.appendChild(head);
  actions.forEach(([label, note, run]) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm';
    button.textContent = label;
    button.addEventListener('click', () => {
      closeBattleMenu();
      run();
    });
    menu.appendChild(button);
    if (note) {
      const line = document.createElement('div');
      line.className = 'battle-menu-note';
      line.textContent = note;
      menu.appendChild(line);
    }
  });
  const holder = menu.parentElement.getBoundingClientRect();
  const x = event ? event.clientX - holder.left : holder.width / 2;
  const y = event ? event.clientY - holder.top : holder.height / 2;
  menu.style.left = Math.min(Math.max(8, x), holder.width - 260) + 'px';
  menu.hidden = false;
  menu.style.top = Math.min(Math.max(8, y), Math.max(8, holder.height - menu.offsetHeight - 8)) + 'px';
}

async function battlePost(path, params) {
  try {
    const response = await fetch(battle.url + path, { method: 'POST', body: new URLSearchParams({ ...params, level: battleLevel() }) });
    const text = await response.text();
    if (response.ok) battleHint(text);
    else battleError(text);
  } catch (e) {
    battleError('Нет связи с сервером');
  }
  battle.lastChange = Date.now();
  pollBattle();
}

// M-23: грань — выбить дверь, разбить окно; стену — мастер
function edgeMenu(edge, kind, event) {
  const actions = [];
  if (kind === 'door') actions.push(['💥 Выбить дверь', 'Станет проёмом. Бросок, если нужен, просит мастер', () => battlePost('/break', { edge })]);
  if (kind === 'window') actions.push(['💥 Разбить окно', 'Можно пролезть', () => battlePost('/break', { edge })]);
  if (kind === 'wall' && battleGm()) actions.push(['💥 Проломить стену', 'Мастер: стена станет проёмом', () => battlePost('/break', { edge })]);
  if (!actions.length) {
    battleHint(kind === 'wall' ? 'Стену ломает мастер — или взрыв' : '');
    return;
  }
  battleMenuAt(event, kind === 'door' ? 'Дверь' : kind === 'window' ? 'Окно' : 'Стена', actions);
}

// M-23: попали в бочку — взрыв задевает всех в радиусе; стрелок — выбранная или своя фишка
function explosiveMenu(x, y, event) {
  const shooter = tokenById(battle.selected) || battle.data.tokens.find(token => String(token.character) === battleMe());
  const params = { x, y };
  if (shooter) params.by = shooter.character;
  battleMenuAt(event, 'Взрывоопасно', [[
    '💥 Взорвать' + (shooter ? ' — стрелял ' + shooter.name : ''),
    'Если выстрел по ней попал: урон всем в радиусе, соседнее рвётся следом',
    () => battlePost('/detonate', params)
  ]]);
}

const closeBattleMenu = () => {
  const menu = document.querySelector('[data-battle-menu]');
  if (menu) menu.hidden = true;
};

// Своя фишка выбирается, чужая при выбранной своей — атака: оружие, цель, расстояние и укрытие уже в диалоге
// Нажатие на фишку — выбрать её (повторное — снять выбор). Атака — правой кнопкой или долгим нажатием по цели
function tokenClick(token) {
  if (!token) return;
  closeBattleMenu();
  selectToken(battle.selected === token.id ? null : token.id);
}

// Кто атакует: выбранная фишка, иначе своя по «Я — …», у мастера — чей ход
const battleAttacker = target => {
  const candidates = [tokenById(battle.selected), battle.data.tokens.find(token => String(token.character) === battleMe()),
    battle.data.tokens.find(token => token.current),
    // A-15: в «За NPC» без выбора атакует NPC (ближайший к цели), а не игрок
    npcOnly() ? battle.data.tokens.filter(token => token.npc && token.x !== null && token.id !== target.id)
      .sort((a, b) => Math.hypot(a.x - target.x, a.y - target.y) - Math.hypot(b.x - target.x, b.y - target.y))[0] : null];
  return candidates.find(token => token && token.id !== target.id && token.x !== null && !lockedToken(token)) || null;
};

// Меню атаки: расстояние, линия видимости и укрытие считает сервер, оружие — из строки атакующего
async function attackMenu(token, event) {
  if (!token || token.x === null) return;
  const attacker = battleAttacker(token);
  const gm = battle.stage && battle.stage.dataset.gm === 'true';
  if (!attacker && !gm) {
    battleError('Сначала выберите, кто атакует: нажмите свою фишку');
    return;
  }
  const menu = document.querySelector('[data-battle-menu]');
  if (!menu) return;
  menu.replaceChildren();
  if (attacker) await attackSection(menu, attacker, token);
  else {
    const head = document.createElement('div');
    head.className = 'battle-menu-head';
    head.textContent = token.name;
    menu.appendChild(head);
  }
  if (gm) gmSection(menu, token);
  const select = document.createElement('button');
  select.type = 'button';
  select.className = 'btn btn-sm btn-ghost';
  select.textContent = 'Выбрать ' + token.name;
  select.addEventListener('click', () => {
    closeBattleMenu();
    selectToken(token.id);
  });
  if (!lockedToken(token)) menu.appendChild(select);
  const holder = menu.parentElement.getBoundingClientRect();
  const x = event ? event.clientX - holder.left : holder.width / 2;
  const y = event ? event.clientY - holder.top : holder.height / 2;
  menu.style.left = Math.min(Math.max(8, x), holder.width - 260) + 'px';
  menu.style.top = Math.min(Math.max(8, y), Math.max(8, holder.height - menu.offsetHeight - 8)) + 'px';
  menu.hidden = false;
  menu.style.top = Math.min(Math.max(8, y), Math.max(8, holder.height - menu.offsetHeight - 8)) + 'px';
}

// R-15a: правка мастера в меню фишки — те же формы, что в боевом блоке участника: урон вручную, инициатива, убрать из боя
function gmSection(menu, token) {
  const row = document.getElementById('combatant-' + token.character);
  if (!row) return;
  const forms = [...row.querySelectorAll('.encounter-damage, .encounter-minor form')]
    .filter(form => !/\/wait$/.test(form.getAttribute('action') || ''));
  if (!forms.length) return;
  const box = document.createElement('div');
  box.className = 'battle-menu-gm';
  const title = document.createElement('div');
  title.className = 'battle-menu-note';
  title.textContent = 'Мастер: ' + token.name;
  box.appendChild(title);
  forms.forEach(form => {
    const copy = form.cloneNode(true);
    copy.removeAttribute('id');
    if (copy.dataset.confirm) copy.addEventListener('submit', e => { if (!confirm(copy.dataset.confirm)) e.preventDefault(); });
    box.appendChild(copy);
  });
  menu.appendChild(box);
}

async function attackSection(menu, attacker, token) {
  let sight = { meters: battleMeters(attacker.x, attacker.y, token.x, token.y), blocked: false, cover: null };
  try {
    const response = await fetch(`${battle.url}/sight?from=${attacker.id}&to=${token.id}`, { cache: 'no-store' });
    if (response.ok) sight = await response.json();
  } catch (e) { /* расстояние посчитано и здесь */ }
  const row = document.getElementById('combatant-' + attacker.character);
  const dialogs = row ? [...row.querySelectorAll('dialog[data-attack]')] : [];
  let last = null;
  try { last = localStorage.getItem('charka.battle.weapon.' + attacker.character); } catch (e) { /* только удобство */ }
  dialogs.sort((a, b) => (b.dataset.attack === last) - (a.dataset.attack === last));

  const head = document.createElement('div');
  head.className = 'battle-menu-head';
  head.textContent = `${attacker.name} → ${token.name} · ${sight.meters} м`;
  menu.appendChild(head);
  const line = (text, className) => {
    const el = document.createElement('div');
    el.className = className;
    el.textContent = text;
    menu.appendChild(el);
  };
  if (sight.blocked && sight.throughLabel) line('Не видна — только вслепую сквозь: ' + sight.throughLabel, 'battle-menu-warn');
  else if (sight.blocked) line('Нет линии видимости', 'battle-menu-warn');
  else if (sight.visibility === 'partial') line('Видна частично' + (sight.coverLabel ? ' · укрытие: ' + sight.coverLabel : ''), 'battle-menu-note');
  else if (sight.coverLabel) line('Укрытие: ' + sight.coverLabel, 'battle-menu-note');
  // M-17: промах одиночным выстрелом или очередью может попасть в первого из них (хоумбрю)
  if (sight.inTheWay && sight.inTheWay.length) line('На линии огня: ' + sight.inTheWay.join(', ') + ' — промах может попасть', 'battle-menu-warn');
  const adjacent = Math.max(Math.abs(attacker.x - token.x), Math.abs(attacker.y - token.y)) <= 1;
  dialogs.forEach(dialog => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm' + (dialog.dataset.attack === last || dialogs.length === 1 ? ' btn-primary' : '');
    const title = dialog.querySelector('h2');
    const why = attackVerdict(dialog, sight, adjacent);
    button.textContent = (title ? title.textContent.replace(/^[^:]*:\s*/, '') : 'Атака') + (why.note ? ' · ' + why.note : '');
    button.disabled = !why.ok;
    if (why.title) button.title = why.title;
    button.addEventListener('click', () => {
      try { localStorage.setItem('charka.battle.weapon.' + attacker.character, dialog.dataset.attack); } catch (e) { /* только удобство */ }
      closeBattleMenu();
      openAttackFromMap(dialog, token, sight);
    });
    menu.appendChild(button);
  });
  if (!dialogs.length) {
    const none = document.createElement('div');
    none.className = 'battle-menu-note';
    none.textContent = 'Атаковать нечем';
    menu.appendChild(none);
  }
}

// Можно ли этим оружием по цели: видна или вслепую сквозь одну преграду (если у оружия есть такой модификатор),
// ближний бой — только с соседней клетки, дальний — не дальше последней полосы дальности (двойная дальность, с. 99)
function attackVerdict(dialog, sight, adjacent) {
  // KF-01: вплотную ли — говорит сервер (data-attack-melee); стрельба Космозэков расстояния не спрашивает, но она не ближний бой
  const melee = 'attackMelee' in dialog.dataset ? dialog.dataset.attackMelee === 'true' : !dialog.querySelector('[data-attack-distance]');
  const blind = sight.blindOption && dialog.querySelector(`input[type="checkbox"][value="${sight.blindOption}"]`);
  if (sight.blocked && !(sight.throughLabel && blind && !melee)) return { ok: false, note: 'не видна' };
  if (melee) return adjacent ? { ok: true, note: '' } : { ok: false, note: 'не рядом' };
  const bands = [...dialog.querySelectorAll('[data-attack-band]')]
    .map(band => ({ meters: Number(band.dataset.attackBand), label: band.textContent.trim() }))
    .sort((a, b) => a.meters - b.meters);
  const band = bands.find(candidate => sight.meters <= candidate.meters);
  if (bands.length && !band) return { ok: false, note: 'вне дальности', title: 'Предел оружия ' + bands[bands.length - 1].meters + ' м' };
  const difficulty = band ? band.label.split('·').pop().trim() : '';
  const notes = [sight.blocked ? 'вслепую' : '', difficulty ? 'сложн. ' + difficulty : ''].filter(Boolean);
  return { ok: true, note: notes.join(', '), title: band ? band.label : '' };
}

// M-10: кого задевает. Взрыв — радиус зоны × единиц заряда от цели, зона огня — половина ширины от цели.
// HOMEBREW: за стеной от точки взрыва не отмечается (книга о стенах и взрыве молчит), мастер может отметить сам
async function pickArea(dialog, target) {
  const mode = dialog.querySelector('[data-attack-mode]');
  const option = mode && mode.selectedOptions[0];
  if (!option || option.dataset.multi !== 'true' || !battle.data) return;
  const rounds = dialog.querySelector('input[name="rounds"]');
  const width = dialog.querySelector('input[name="zoneWidth"]');
  let area = 0;
  if (option.dataset.width === 'true') area = Number(width && width.value || 0) / 2;
  else if (Number(dialog.dataset.radius || 0) > 0) {
    const units = option.dataset.rounds === 'true' && rounds ? Math.max(1, Number(rounds.value || 1)) : 1;
    area = Number(dialog.dataset.radius) * units;
  }
  if (!area) return;
  const inputs = [...dialog.querySelectorAll('input[name="target"]')];
  const candidates = battle.data.tokens.filter(other => other.x !== null && other.id !== target.id && !other.out
    && battleMeters(target.x, target.y, other.x, other.y) <= area);
  inputs.forEach(input => {
    if (input.value !== String(target.character) && !('attackObject' in input.dataset)) input.checked = false;
  });
  for (const other of candidates) {
    const input = inputs.find(candidate => candidate.value === String(other.character));
    if (!input) continue;
    let shielded = false;
    try {
      const response = await fetch(`${battle.url}/sight?from=${target.id}&to=${other.id}`, { cache: 'no-store' });
      if (response.ok) shielded = (await response.json()).blocked;
    } catch (e) { /* без проверки стены — отмечаем */ }
    input.checked = !shielded;
  }
}

// Тот же диалог, что в строке трекера: цель, расстояние и укрытие подставлены, всё можно поправить
function openAttackFromMap(dialog, target, sight) {
  const inputs = [...dialog.querySelectorAll('input[name="target"]')];
  const chosen = inputs.find(input => input.value === String(target.character));
  if (chosen) {
    inputs.forEach(input => { input.checked = input === chosen; });
    chosen.dispatchEvent(new Event('change', { bubbles: true }));
  }
  dialog.mapTarget = target;
  if (!dialog.dataset.mapBound) {
    dialog.dataset.mapBound = '1';
    ['[data-attack-mode]', 'input[name="rounds"]', 'input[name="zoneWidth"]'].forEach(selector => {
      const field = dialog.querySelector(selector);
      if (field) field.addEventListener('change', () => { if (dialog.mapTarget) pickArea(dialog, dialog.mapTarget); });
    });
  }
  pickArea(dialog, target);
  const distance = dialog.querySelector('input[name="distance"]');
  if (distance) {
    distance.value = sight.meters;
    distance.dispatchEvent(new Event('input', { bubbles: true }));
  }
  // A-05: бегал или уже стрелял в этом раунде — штраф за следующее действие (с. 98) по свежим данным карты
  const attacker = battleAttacker(target);
  const actions = attacker && dialog.querySelector('select[name="option.action"]');
  if (actions) actions.value = attacker.actionOption || '';
  const cover = dialog.querySelector('select[name="option.cover"]');
  if (cover) cover.value = sight.cover || '';
  if (cover && sight.cover) {
    const more = cover.closest('details');
    if (more) more.open = true;
  }
  // стрельба вслепую: флажок ставит и снимает карта
  if (dialog.mapBlind) {
    const before = dialog.querySelector(`input[type="checkbox"][value="${dialog.mapBlind}"]`);
    if (before) before.checked = false;
    dialog.mapBlind = null;
  }
  const blind = sight.blocked && sight.blindOption && dialog.querySelector(`input[type="checkbox"][value="${sight.blindOption}"]`);
  if (blind) {
    blind.checked = true;
    dialog.mapBlind = sight.blindOption;
    const more = blind.closest('details');
    if (more) more.open = true;
  }
  dialog.showModal();
}

// Редактор карты (M-06, M-07). Все инструменты — операции над словарями документа: грань стены — ключ «x,y,h|v»,
// поэтому общая стена двух комнат одна сама собой, а ластик удаляет ключ. Документ сохраняется целиком через 2 с после правки
const editor = { url: null, data: null, doc: null, root: null, level: 0, undo: [], redo: [], tool: 'room', size: 1, rot: 0, zone: 'players', gap: 1,
  density: 15, saveTimer: null, view: null, selection: null, clipboard: null, hover: null, space: false };

const EDITOR_TOOLS = { k: 'calibrate', r: 'room', w: 'wall', f: 'fill', b: 'brush', o: 'object', l: 'row', s: 'scatter', z: 'zone', t: 'label', v: 'select', x: 'eraser' };
// какой слой материалов у инструмента: цифры 1–9 выбирают из него
const TOOL_LAYERS = { room: 'WALL', wall: 'WALL', fill: 'TERRAIN', brush: 'TERRAIN' };

const editorMemory = (key, value) => {
  try {
    if (value === undefined) return localStorage.getItem('charka.editor.' + key);
    localStorage.setItem('charka.editor.' + key, value);
  } catch (e) { /* только удобство */ }
  return null;
};

function initEditor() {
  const holder = document.querySelector('[data-map-editor]');
  if (!holder) {
    editor.url = null;
    return;
  }
  if (editor.url !== holder.dataset.mapEditor) {
    Object.assign(editor, { url: holder.dataset.mapEditor, data: null, doc: null, root: null, level: 0, undo: [], redo: [], view: null, selection: null });
  }
  editor.tool = editorMemory('tool') || 'room';
  holder.querySelectorAll('[data-tool]').forEach(button => button.addEventListener('click', () => setTool(button.dataset.tool)));
  holder.querySelector('[data-editor-undo]').addEventListener('click', undoEdit);
  holder.querySelector('[data-editor-redo]').addEventListener('click', redoEdit);
  // правка ещё не сохранена — рисуем своё; иначе берём с сервера: «Ещё вариант» заменил карту там
  if (editor.data && editor.saveTimer) {
    renderEditorLevels();
    renderEditor();
    return;
  }
  fetch(editor.url, { cache: 'no-store' }).then(response => response.json()).then(data => {
    editor.data = data;
    editor.root = data.doc;
    setEditorLevel(0);
  }).catch(() => editorStatus('Карта не загрузилась', true));
}

// M-22: правится один уровень — его вид делит стены, клетки, предметы и подписи с корнем документа
function setEditorLevel(level, silent) {
  const root = editor.root;
  root.levels = root.levels || [];
  editor.level = Math.min(Math.max(0, level), root.levels.length);
  editor.doc = levelDoc(root, editor.level);
  editor.selection = null;
  renderEditorLevels();
  if (!silent) renderEditor();
}

function renderEditorLevels() {
  const holder = document.querySelector('[data-editor-levels]');
  if (!holder || !editor.root) return;
  holder.replaceChildren();
  const count = levelCount(editor.root);
  for (let level = count - 1; level >= 0; level--) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm' + (level === editor.level ? ' btn-primary' : ' btn-ghost');
    button.textContent = levelName(editor.root, level);
    button.title = 'Править этот уровень. Лестница — клетка «Лестница» на соседних уровнях в одном месте, «Пустота» — видно вниз';
    button.addEventListener('click', () => setEditorLevel(level));
    holder.appendChild(button);
  }
  const add = document.createElement('button');
  add.type = 'button';
  add.className = 'btn btn-sm btn-ghost';
  add.textContent = '＋ Этаж';
  add.title = 'Новый уровень над верхним: этаж, крыша, балкон';
  add.addEventListener('click', () => {
    beginEdit();
    const number = levelCount(editor.root);
    editor.root.levels.push({ name: (number + 1) + ' этаж', z: number * 3, walls: {}, cells: {}, stamps: [], labels: [] });
    setEditorLevel(number);
    editDone();
  });
  holder.appendChild(add);
  if (editor.level > 0 && editor.level === count - 1) {
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'btn btn-sm btn-ghost';
    remove.textContent = '✕ Уровень';
    remove.title = 'Убрать верхний уровень со всем, что на нём нарисовано';
    remove.addEventListener('click', () => {
      if (!confirm('Убрать уровень «' + levelName(editor.root, editor.level) + '» со всем, что на нём?')) return;
      beginEdit();
      editor.root.levels.pop();
      setEditorLevel(editor.level - 1);
      editDone();
    });
    holder.appendChild(remove);
  }
}

const editorStatus = (text, error) => {
  const status = document.querySelector('[data-editor-status]');
  if (!status) return;
  status.textContent = text;
  status.classList.toggle('is-error', Boolean(error));
};

function setTool(tool) {
  editor.tool = tool;
  editor.selection = tool === 'select' ? editor.selection : null;
  editorMemory('tool', tool);
  renderEditor();
}

// материал инструмента: последний выбранный, иначе по умолчанию системы
const toolMaterial = tool => {
  const stored = editorMemory('material.' + tool);
  const materials = editor.data.materials;
  if (stored && materials.some(material => material.code === stored)) return stored;
  const preset = { room: editor.data.defaults.room, wall: editor.data.defaults.wall, door: editor.data.defaults.door, window: editor.data.defaults.window }[tool];
  if (preset) return preset;
  const layer = TOOL_LAYERS[tool];
  const first = materials.find(material => material.layer === layer);
  return first ? first.code : null;
};

const toolObject = () => {
  const stored = editorMemory('object');
  const objects = editor.data.objects;
  return objects.some(object => object.code === stored) ? stored : objects.length ? objects[0].code : null;
};

// Снимок до правки: Ctrl+Z возвращает его
const beginEdit = () => {
  editor.undo.push(JSON.stringify(editor.root));
  if (editor.undo.length > 100) editor.undo.shift();
  editor.redo = [];
};

const editDone = () => {
  renderEditor();
  editorStatus('Изменено…');
  clearTimeout(editor.saveTimer);
  editor.saveTimer = setTimeout(saveEditor, 2000);
};

async function saveEditor() {
  clearTimeout(editor.saveTimer);
  editor.saveTimer = null;
  editorStatus('Сохраняю…');
  try {
    const response = await fetch(editor.url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(editor.root) });
    if (!response.ok) {
      editorStatus('Не сохранено: ' + await response.text(), true);
      return;
    }
    editorStatus('Сохранено');
  } catch (e) {
    editorStatus('Нет связи: не сохранено', true);
  }
}

function undoEdit() {
  if (!editor.undo.length) return;
  editor.redo.push(JSON.stringify(editor.root));
  editor.root = JSON.parse(editor.undo.pop());
  setEditorLevel(editor.level, true);
  editDone();
}

function redoEdit() {
  if (!editor.redo.length) return;
  editor.undo.push(JSON.stringify(editor.root));
  editor.root = JSON.parse(editor.redo.pop());
  setEditorLevel(editor.level, true);
  editDone();
}

// Уходим, пока не сохранено: отправить сразу
window.addEventListener('pagehide', () => {
  if (editor.url && editor.saveTimer && editor.doc) {
    // keepalive вместо sendBeacon: маяку не задать заголовок CSRF
    fetch(editor.url, { method: 'POST', keepalive: true, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(editor.root) });
  }
});

const edgeTop = (x, y) => x + ',' + y + ',h';
const edgeLeft = (x, y) => x + ',' + y + ',v';
const cellKey = (x, y) => x + ',' + y;
const insideDoc = (x, y) => x >= 0 && y >= 0 && x < editor.doc.w && y < editor.doc.h;

// Прямоугольник клеток → стены по краю; Alt — слить: стены внутри уходят
function drawRoom(a, b, merge) {
  const x1 = Math.max(0, Math.min(a.x, b.x));
  const y1 = Math.max(0, Math.min(a.y, b.y));
  const x2 = Math.min(editor.doc.w - 1, Math.max(a.x, b.x));
  const y2 = Math.min(editor.doc.h - 1, Math.max(a.y, b.y));
  const walls = editor.doc.walls;
  if (merge) {
    for (let x = x1; x <= x2; x++) for (let y = y1 + 1; y <= y2; y++) delete walls[edgeTop(x, y)];
    for (let y = y1; y <= y2; y++) for (let x = x1 + 1; x <= x2; x++) delete walls[edgeLeft(x, y)];
    return;
  }
  const material = toolMaterial('room');
  // дверь или окно на краю остаются: комнату пристроили к готовой
  const put = key => { if (!walls[key] || walls[key] === 'gap') walls[key] = material; };
  for (let x = x1; x <= x2; x++) {
    put(edgeTop(x, y1));
    put(edgeTop(x, y2 + 1));
  }
  for (let y = y1; y <= y2; y++) {
    put(edgeLeft(x1, y));
    put(edgeLeft(x2 + 1, y));
  }
}

// Стена от узла к узлу сетки: всегда по прямой, по большей оси
function drawWall(a, b) {
  const material = toolMaterial('wall');
  if (Math.abs(b.x - a.x) >= Math.abs(b.y - a.y)) {
    for (let x = Math.min(a.x, b.x); x < Math.max(a.x, b.x); x++) {
      if (x >= 0 && x < editor.doc.w && a.y >= 0 && a.y <= editor.doc.h) editor.doc.walls[edgeTop(x, a.y)] = material;
    }
  } else {
    for (let y = Math.min(a.y, b.y); y < Math.max(a.y, b.y); y++) {
      if (y >= 0 && y < editor.doc.h && a.x >= 0 && a.x <= editor.doc.w) editor.doc.walls[edgeLeft(a.x, y)] = material;
    }
  }
}

// Нажатие на грань: стена → дверь → окно → проём → пусто
function cycleEdge(key) {
  const walls = editor.doc.walls;
  const value = walls[key];
  if (!value) walls[key] = toolMaterial('wall');
  else if (value.startsWith('door:')) walls[key] = 'window:' + toolMaterial('window');
  else if (value.startsWith('window:')) walls[key] = 'gap';
  else if (value === 'gap') delete walls[key];
  else walls[key] = 'door:' + toolMaterial('door');
}

// Заливка местности в замкнутой области: не переходит через стены, окна и закрытые двери
function fillArea(start, material) {
  const walls = editor.doc.walls;
  const blocks = key => {
    const value = walls[key];
    return value && value !== 'gap' && !(value.startsWith('door:') && value.endsWith(':open'));
  };
  const seen = new Set([cellKey(start.x, start.y)]);
  const queue = [start];
  while (queue.length && seen.size < 4000) {
    const { x, y } = queue.shift();
    editor.doc.cells[cellKey(x, y)] = material;
    [[1, 0, edgeLeft(x + 1, y)], [-1, 0, edgeLeft(x, y)], [0, 1, edgeTop(x, y + 1)], [0, -1, edgeTop(x, y)]].forEach(([dx, dy, edge]) => {
      const next = { x: x + dx, y: y + dy };
      const key = cellKey(next.x, next.y);
      if (!insideDoc(next.x, next.y) || seen.has(key) || blocks(edge)) return;
      seen.add(key);
      queue.push(next);
    });
  }
}

function brushAt(cell, material) {
  const half = Math.floor(editor.size / 2);
  for (let dy = -half; dy <= half; dy++) {
    for (let dx = -half; dx <= half; dx++) {
      if (insideDoc(cell.x + dx, cell.y + dy)) editor.doc.cells[cellKey(cell.x + dx, cell.y + dy)] = material;
    }
  }
}

const objectOf = code => editor.data.objects.find(object => object.code === code);
const stampSize = stamp => {
  const object = objectOf(stamp.code);
  if (!object) return { w: 1, h: 1 };
  return stamp.rot % 2 === 0 ? { w: object.w, h: object.h } : { w: object.h, h: object.w };
};
const stampAt = (x, y) => editor.doc.stamps.findIndex(stamp => {
  const size = stampSize(stamp);
  return x >= stamp.x && y >= stamp.y && x < stamp.x + size.w && y < stamp.y + size.h;
});
const stampFits = stamp => {
  const size = stampSize(stamp);
  for (let dy = 0; dy < size.h; dy++) {
    for (let dx = 0; dx < size.w; dx++) {
      if (!insideDoc(stamp.x + dx, stamp.y + dy) || stampAt(stamp.x + dx, stamp.y + dy) >= 0) return false;
    }
  }
  return true;
};

function placeObject(cell, code, rot) {
  const stamp = { code, x: cell.x, y: cell.y, rot: ((rot % 4) + 4) % 4 };
  if (!stampFits(stamp)) return false;
  editor.doc.stamps.push(stamp);
  return true;
}

// Ряд: машины вдоль линии через N клеток, развёрнуты по линии
function placeRow(a, b) {
  const code = toolObject();
  const object = objectOf(code);
  if (!object) return;
  const horizontal = Math.abs(b.x - a.x) >= Math.abs(b.y - a.y);
  // длинная сторона предмета — вдоль линии
  const rot = horizontal === (object.w >= object.h) ? 0 : 1;
  const length = rot % 2 === 0 ? (horizontal ? object.w : object.h) : (horizontal ? object.h : object.w);
  const step = length + editor.gap;
  const span = horizontal ? Math.abs(b.x - a.x) : Math.abs(b.y - a.y);
  const dir = horizontal ? Math.sign(b.x - a.x) || 1 : Math.sign(b.y - a.y) || 1;
  for (let offset = 0; offset + length <= span + 1; offset += step) {
    const along = (horizontal ? a.x : a.y) + (dir > 0 ? offset : -offset - length + 1);
    placeObject(horizontal ? { x: along, y: a.y } : { x: a.x, y: along }, code, rot);
  }
}

// Россыпь: предметы в прямоугольнике по плотности, без наложения. Повтор даёт другой узор
function placeScatter(a, b) {
  const code = toolObject();
  if (!objectOf(code)) return;
  const x1 = Math.min(a.x, b.x);
  const y1 = Math.min(a.y, b.y);
  const x2 = Math.max(a.x, b.x);
  const y2 = Math.max(a.y, b.y);
  for (let y = y1; y <= y2; y++) {
    for (let x = x1; x <= x2; x++) {
      if (Math.random() * 100 < editor.density) placeObject({ x, y }, code, Math.random() < 0.5 ? 0 : 1);
    }
  }
}

// Ластик: ближняя грань, иначе всё в клетке
function eraseAt(hit) {
  if (hit.edge && hit.edgeDistance < 0.22 && editor.doc.walls[hit.edge]) {
    delete editor.doc.walls[hit.edge];
    return;
  }
  delete editor.doc.cells[cellKey(hit.x, hit.y)];
  const index = stampAt(hit.x, hit.y);
  if (index >= 0) editor.doc.stamps.splice(index, 1);
  // на месте: у верхнего уровня список общий с документом (M-22)
  const keptLabels = editor.doc.labels.filter(label => label.x !== hit.x || label.y !== hit.y);
  editor.doc.labels.splice(0, editor.doc.labels.length, ...keptLabels);
  editor.doc.zones = editor.doc.zones.filter(zone => !(hit.x >= zone.x && hit.y >= zone.y && hit.x < zone.x + zone.w && hit.y < zone.y + zone.h));
}

// Содержимое выделения в координатах от его угла: перенести, скопировать, повернуть, отразить
function takeSelection(sel, remove) {
  const part = { w: sel.w, h: sel.h, walls: {}, cells: {}, stamps: [], labels: [] };
  const doc = editor.doc;
  Object.entries(doc.walls).forEach(([key, value]) => {
    const [x, y, dir] = key.split(',');
    const ex = Number(x) - sel.x;
    const ey = Number(y) - sel.y;
    const inside = dir === 'h' ? ex >= 0 && ex < sel.w && ey >= 0 && ey <= sel.h : ex >= 0 && ex <= sel.w && ey >= 0 && ey < sel.h;
    if (!inside) return;
    part.walls[ex + ',' + ey + ',' + dir] = value;
    if (remove) delete doc.walls[key];
  });
  Object.entries(doc.cells).forEach(([key, value]) => {
    const [x, y] = key.split(',').map(Number);
    if (x < sel.x || y < sel.y || x >= sel.x + sel.w || y >= sel.y + sel.h) return;
    part.cells[(x - sel.x) + ',' + (y - sel.y)] = value;
    if (remove) delete doc.cells[key];
  });
  doc.stamps = doc.stamps.filter(stamp => {
    const inside = stamp.x >= sel.x && stamp.y >= sel.y && stamp.x < sel.x + sel.w && stamp.y < sel.y + sel.h;
    if (inside) part.stamps.push({ ...stamp, x: stamp.x - sel.x, y: stamp.y - sel.y });
    return !(inside && remove);
  });
  doc.labels = doc.labels.filter(label => {
    const inside = label.x >= sel.x && label.y >= sel.y && label.x < sel.x + sel.w && label.y < sel.y + sel.h;
    if (inside) part.labels.push({ ...label, x: label.x - sel.x, y: label.y - sel.y });
    return !(inside && remove);
  });
  return part;
}

function putPart(part, at) {
  const doc = editor.doc;
  Object.entries(part.walls).forEach(([key, value]) => {
    const [x, y, dir] = key.split(',');
    const nx = Number(x) + at.x;
    const ny = Number(y) + at.y;
    const fits = dir === 'h' ? nx >= 0 && nx < doc.w && ny >= 0 && ny <= doc.h : nx >= 0 && nx <= doc.w && ny >= 0 && ny < doc.h;
    if (fits) doc.walls[nx + ',' + ny + ',' + dir] = value;
  });
  Object.entries(part.cells).forEach(([key, value]) => {
    const [x, y] = key.split(',').map(Number);
    if (insideDoc(x + at.x, y + at.y)) doc.cells[cellKey(x + at.x, y + at.y)] = value;
  });
  part.stamps.forEach(stamp => placeObject({ x: stamp.x + at.x, y: stamp.y + at.y }, stamp.code, stamp.rot));
  part.labels.forEach(label => {
    if (insideDoc(label.x + at.x, label.y + at.y)) doc.labels.push({ ...label, x: label.x + at.x, y: label.y + at.y });
  });
}

// Поворот на четверть по часовой: точка (px, py) прямоугольника высотой h уходит в (h − py, px)
function rotatePart(part) {
  const h = part.h;
  const rotated = { w: part.h, h: part.w, walls: {}, cells: {}, stamps: [], labels: [] };
  Object.entries(part.walls).forEach(([key, value]) => {
    const [x, y, dir] = key.split(',');
    const cx = Number(x);
    const cy = Number(y);
    if (dir === 'h') rotated.walls[(h - cy) + ',' + cx + ',v'] = value;
    else rotated.walls[(h - cy - 1) + ',' + cx + ',h'] = value;
  });
  Object.entries(part.cells).forEach(([key, value]) => {
    const [x, y] = key.split(',').map(Number);
    rotated.cells[(h - 1 - y) + ',' + x] = value;
  });
  part.stamps.forEach(stamp => {
    const size = stampSize(stamp);
    rotated.stamps.push({ ...stamp, x: h - stamp.y - size.h, y: stamp.x, rot: (stamp.rot + 1) % 4 });
  });
  part.labels.forEach(label => rotated.labels.push({ ...label, x: h - 1 - label.y, y: label.x }));
  return rotated;
}

function mirrorPart(part) {
  const w = part.w;
  const mirrored = { w: part.w, h: part.h, walls: {}, cells: {}, stamps: [], labels: [] };
  Object.entries(part.walls).forEach(([key, value]) => {
    const [x, y, dir] = key.split(',');
    const cx = Number(x);
    mirrored.walls[(dir === 'h' ? w - 1 - cx : w - cx) + ',' + y + ',' + dir] = value;
  });
  Object.entries(part.cells).forEach(([key, value]) => {
    const [x, y] = key.split(',').map(Number);
    mirrored.cells[(w - 1 - x) + ',' + y] = value;
  });
  part.stamps.forEach(stamp => {
    const size = stampSize(stamp);
    mirrored.stamps.push({ ...stamp, x: w - stamp.x - size.w, rot: stamp.rot % 2 === 1 ? (stamp.rot + 2) % 4 : stamp.rot });
  });
  part.labels.forEach(label => mirrored.labels.push({ ...label, x: w - 1 - label.x }));
  return mirrored;
}

function transformSelection(how) {
  const sel = editor.selection;
  if (!sel) return;
  beginEdit();
  const part = how(takeSelection(sel, true));
  putPart(part, sel);
  editor.selection = { x: sel.x, y: sel.y, w: part.w, h: part.h };
  editDone();
}

function renderOptions() {
  const box = document.querySelector('[data-editor-options]');
  if (!box) return;
  box.replaceChildren();
  const tool = editor.tool;
  const select = (label, options, value, change) => {
    const field = document.createElement('label');
    field.className = 'field editor-field';
    const span = document.createElement('span');
    span.className = 'label';
    span.textContent = label;
    const control = document.createElement('select');
    options.forEach(([code, text]) => {
      const option = document.createElement('option');
      option.value = code;
      option.textContent = text;
      control.appendChild(option);
    });
    control.value = value;
    control.addEventListener('change', () => change(control.value));
    field.append(span, control);
    box.appendChild(field);
    return control;
  };
  const number = (label, value, min, max, change) => {
    const field = document.createElement('label');
    field.className = 'field editor-field';
    const span = document.createElement('span');
    span.className = 'label';
    span.textContent = label;
    const input = document.createElement('input');
    input.type = 'number';
    input.min = min;
    input.max = max;
    input.value = value;
    input.className = 'narrow';
    input.addEventListener('change', () => change(Number(input.value)));
    field.append(span, input);
    box.appendChild(field);
  };
  const materialsOf = layer => editor.data.materials.filter(material => material.layer === layer)
    .map((material, index) => [material.code, (index < 9 ? (index + 1) + ' · ' : '') + material.label + (material.sp ? ' SP ' + material.sp : '')]);
  const remember = key => value => { editorMemory('material.' + key, value); };
  // панель материалов: свёрнута за выбором, SP вводить не нужно
  if (tool === 'room' || tool === 'wall') {
    select('Стена', materialsOf('WALL'), toolMaterial(tool), remember(tool));
    if (tool === 'wall') {
      select('Дверь', materialsOf('DOOR'), toolMaterial('door'), remember('door'));
      select('Окно', materialsOf('WINDOW'), toolMaterial('window'), remember('window'));
    }
  }
  if (tool === 'fill' || tool === 'brush') select('Местность', materialsOf('TERRAIN'), toolMaterial(tool), remember(tool));
  if (tool === 'brush') select('Кисть', [['1', '1 клетка'], ['3', '3 × 3'], ['5', '5 × 5']], String(editor.size), value => { editor.size = Number(value); });
  if (tool === 'object' || tool === 'row' || tool === 'scatter') {
    select('Предмет', editor.data.objects.map(object => [object.code, object.label + ' ' + object.w + '×' + object.h]), toolObject(),
      value => { editorMemory('object', value); renderEditor(); });
  }
  if (tool === 'object') select('Поворот', [['0', '0°'], ['1', '90°'], ['2', '180°'], ['3', '270°']], String(editor.rot), value => { editor.rot = Number(value); });
  if (tool === 'row') number('Промежуток, клеток', editor.gap, 0, 10, value => { editor.gap = value; });
  if (tool === 'scatter') number('Плотность, %', editor.density, 1, 100, value => { editor.density = value; });
  if (tool === 'zone') select('Чья', [['players', 'Старт игроков'], ['npcs', 'Старт NPC']], editor.zone, value => { editor.zone = value; });
  if (tool === 'select') {
    const hint = document.createElement('span');
    hint.className = 'editor-note';
    hint.textContent = editor.selection
      ? `Выделено ${editor.selection.w} × ${editor.selection.h}: тяните, Ctrl+C/V, Q/E — поворот, M — отразить, Delete`
      : 'Протяните рамку';
    box.appendChild(hint);
  }
  if (tool === 'select' && editor.selection) {
    [['⟳ Повернуть', () => transformSelection(rotatePart)], ['⇋ Отразить', () => transformSelection(mirrorPart)],
      ['✕ Удалить', () => { beginEdit(); takeSelection(editor.selection, true); editDone(); }]].forEach(([text, action]) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'btn btn-sm btn-ghost';
      button.textContent = text;
      button.addEventListener('click', action);
      box.appendChild(button);
    });
  }
}

function renderEditor() {
  const stage = document.querySelector('[data-editor-stage]');
  if (!stage || !editor.doc) return;
  document.querySelectorAll('[data-tool]').forEach(button => button.classList.toggle('is-active', button.dataset.tool === editor.tool));
  renderOptions();
  const doc = editor.doc;
  if (!editor.view) editor.view = { x: -CELL / 2, y: -CELL / 2, w: doc.w * CELL + CELL, h: doc.h * CELL + CELL };
  if (stage.clientWidth) {
    stage.style.height = Math.round(Math.min(innerHeight * 0.75, Math.max(300, stage.clientWidth * (doc.h + 1) / (doc.w + 1)))) + 'px';
  }
  const materials = new Map(editor.data.materials.map(material => [material.code, material]));
  const objects = new Map(editor.data.objects.map(object => [object.code, object]));
  const svg = svgEl('svg', { viewBox: [editor.view.x, editor.view.y, editor.view.w, editor.view.h].join(' '), class: 'battle-svg editor-svg' });
  drawMapLayers(svg, doc, materials, objects, null, true, editor.data.backgroundUrl);
  if (editor.selection) {
    const sel = editor.selection;
    svgEl('rect', { x: sel.x * CELL, y: sel.y * CELL, width: sel.w * CELL, height: sel.h * CELL, class: 'editor-selection' }, svg);
  }
  editor.preview = svgEl('g', { class: 'editor-preview' }, svg);
  stage.replaceChildren(svg);
  wireEditorSvg(svg);
}

// Калибровка подложки: рамка по одной клетке картинки становится клеткой сетки, её угол — узлом сетки
function calibrate(a, b) {
  const bg = editor.doc.bg;
  const rw = Math.abs(b.x - a.x) / CELL;
  const rh = Math.abs(b.y - a.y) / CELL;
  if (!bg || rw < 0.05 || rh < 0.05) {
    editorStatus('Протяните рамку по одной клетке картинки', true);
    return false;
  }
  beginEdit();
  const left = Math.min(a.x, b.x) / CELL;
  const top = Math.min(a.y, b.y) / CELL;
  const sx = 1 / rw;
  const sy = 1 / rh;
  // точка рамки после растяжения картинки от её угла должна встать на ближний узел
  const scaledLeft = bg.x + (left - bg.x) * sx;
  const scaledTop = bg.y + (top - bg.y) * sy;
  editor.doc.bg = { ...bg, w: bg.w * sx, h: bg.h * sy, x: bg.x + Math.round(scaledLeft) - scaledLeft, y: bg.y + Math.round(scaledTop) - scaledTop };
  editorStatus('Сетка подогнана под картинку');
  return true;
}

// Где указатель: клетка, ближний узел сетки и ближняя грань
function editorHit(svg, event) {
  const point = toSvgPoint(svg, event.clientX, event.clientY);
  const fx = point.x / CELL;
  const fy = point.y / CELL;
  const x = Math.floor(fx);
  const y = Math.floor(fy);
  const dx = fx - x;
  const dy = fy - y;
  const distances = [[dy, edgeTop(x, y)], [1 - dy, edgeTop(x, y + 1)], [dx, edgeLeft(x, y)], [1 - dx, edgeLeft(x + 1, y)]];
  distances.sort((a, b) => a[0] - b[0]);
  return { x, y, node: { x: Math.round(fx), y: Math.round(fy) }, edge: distances[0][1], edgeDistance: distances[0][0], point };
}

function editorPreview(start, hit) {
  const group = editor.preview;
  if (!group) return;
  group.replaceChildren();
  const tool = editor.tool;
  const rect = (a, b, cls) => svgEl('rect', {
    x: Math.min(a.x, b.x) * CELL, y: Math.min(a.y, b.y) * CELL,
    width: (Math.abs(a.x - b.x) + 1) * CELL, height: (Math.abs(a.y - b.y) + 1) * CELL, class: cls
  }, group);
  if (start && ['room', 'zone', 'scatter', 'select'].includes(tool)) rect(start, hit, 'editor-ghost');
  if (start && tool === 'calibrate') {
    svgEl('rect', { x: Math.min(start.point.x, hit.point.x), y: Math.min(start.point.y, hit.point.y),
      width: Math.abs(hit.point.x - start.point.x), height: Math.abs(hit.point.y - start.point.y), class: 'editor-ghost' }, group);
  }
  if (start && tool === 'wall') {
    const b = Math.abs(hit.node.x - start.node.x) >= Math.abs(hit.node.y - start.node.y) ? { x: hit.node.x, y: start.node.y } : { x: start.node.x, y: hit.node.y };
    svgEl('line', { x1: start.node.x * CELL, y1: start.node.y * CELL, x2: b.x * CELL, y2: b.y * CELL, class: 'editor-ghost-line' }, group);
  }
  if (start && tool === 'row') {
    const horizontal = Math.abs(hit.x - start.x) >= Math.abs(hit.y - start.y);
    rect(start, horizontal ? { x: hit.x, y: start.y } : { x: start.x, y: hit.y }, 'editor-ghost');
  }
  if (!start && tool === 'object') {
    const object = objectOf(toolObject());
    if (object) {
      const w = editor.rot % 2 === 0 ? object.w : object.h;
      const h = editor.rot % 2 === 0 ? object.h : object.w;
      svgEl('rect', { x: hit.x * CELL, y: hit.y * CELL, width: w * CELL, height: h * CELL, class: 'editor-ghost' }, group);
    }
  }
  if (!start && (tool === 'wall' || tool === 'eraser') && hit.edgeDistance < 0.22) {
    const [x, y, dir] = hit.edge.split(',');
    const cx = Number(x) * CELL;
    const cy = Number(y) * CELL;
    svgEl('line', dir === 'h' ? { x1: cx, y1: cy, x2: cx + CELL, y2: cy } : { x1: cx, y1: cy, x2: cx, y2: cy + CELL }, group)
      .setAttribute('class', 'editor-ghost-line');
  }
}

function wireEditorSvg(svg) {
  let press = null;
  svg.addEventListener('contextmenu', event => event.preventDefault());
  svg.addEventListener('pointerdown', event => {
    if (event.button !== 0 && event.button !== 2) return;
    svg.setPointerCapture(event.pointerId);
    const hit = editorHit(svg, event);
    const erase = event.button === 2 || editor.tool === 'eraser';
    press = { start: hit, erase, alt: event.altKey, pan: editor.space || event.button === 1, x: event.clientX, y: event.clientY, painted: false };
    if (press.pan) return;
    if (editor.tool === 'select' && !erase && editor.selection && hit.x >= editor.selection.x && hit.y >= editor.selection.y
      && hit.x < editor.selection.x + editor.selection.w && hit.y < editor.selection.y + editor.selection.h) {
      press.moveSelection = true;
      return;
    }
    if (erase || editor.tool === 'brush') {
      beginEdit();
      press.painted = true;
      paintAt(hit, erase);
    }
  });
  // кисть и ластик красят документ сразу, а рисуют метку; перерисовка — когда отпустили, чтобы не потерять захват указателя
  const paintAt = (hit, erase) => {
    if (erase) eraseAt(hit);
    else brushAt(hit, toolMaterial('brush'));
    const half = erase ? 0 : Math.floor(editor.size / 2);
    svgEl('rect', { x: (hit.x - half) * CELL, y: (hit.y - half) * CELL, width: (half * 2 + 1) * CELL, height: (half * 2 + 1) * CELL,
      class: erase ? 'editor-erase' : 'editor-paint' }, editor.preview);
  };
  svg.addEventListener('pointermove', event => {
    const hit = editorHit(svg, event);
    editor.hover = hit;
    if (!press) {
      editorPreview(null, hit);
      return;
    }
    if (press.pan) {
      const scale = editor.view.w / svg.getBoundingClientRect().width;
      editor.view.x -= (event.clientX - press.x) * scale;
      editor.view.y -= (event.clientY - press.y) * scale;
      press.x = event.clientX;
      press.y = event.clientY;
      svg.setAttribute('viewBox', [editor.view.x, editor.view.y, editor.view.w, editor.view.h].join(' '));
      return;
    }
    if (press.painted) {
      paintAt(hit, press.erase);
      return;
    }
    if (press.moveSelection) {
      editorPreview(null, hit);
      const sel = editor.selection;
      const dx = hit.x - press.start.x;
      const dy = hit.y - press.start.y;
      svgEl('rect', { x: (sel.x + dx) * CELL, y: (sel.y + dy) * CELL, width: sel.w * CELL, height: sel.h * CELL, class: 'editor-ghost' }, editor.preview);
      return;
    }
    editorPreview(press.start, hit);
  });
  const finish = event => {
    if (!press) return;
    const current = press;
    press = null;
    if (current.pan || current.painted) {
      if (current.painted) editDone();
      return;
    }
    const hit = editorHit(svg, event);
    const start = current.start;
    const moved = hit.x !== start.x || hit.y !== start.y;
    const tool = editor.tool;
    if (current.moveSelection) {
      if (!moved) return;
      beginEdit();
      const sel = editor.selection;
      const part = takeSelection(sel, true);
      const at = { x: sel.x + hit.x - start.x, y: sel.y + hit.y - start.y };
      putPart(part, at);
      editor.selection = { ...sel, ...at };
      editDone();
      return;
    }
    if (tool === 'select') {
      editor.selection = moved || !editor.selection
        ? { x: Math.min(start.x, hit.x), y: Math.min(start.y, hit.y), w: Math.abs(hit.x - start.x) + 1, h: Math.abs(hit.y - start.y) + 1 }
        : null;
      renderEditor();
      return;
    }
    if (tool === 'calibrate') {
      if (!calibrate(start.point, hit.point)) return;
      editDone();
      return;
    }
    beginEdit();
    if (tool === 'room') drawRoom(start, hit, current.alt);
    else if (tool === 'wall') {
      const nodeMoved = hit.node.x !== start.node.x || hit.node.y !== start.node.y;
      if (nodeMoved) drawWall(start.node, hit.node);
      else cycleEdge(start.edge);
    } else if (tool === 'fill' && insideDoc(hit.x, hit.y)) fillArea(hit, toolMaterial('fill'));
    else if (tool === 'object' && !placeObject(hit, toolObject(), editor.rot)) {
      editor.undo.pop();
      editorStatus('Не помещается: занято или за краем', true);
      return;
    } else if (tool === 'row') placeRow(start, Math.abs(hit.x - start.x) >= Math.abs(hit.y - start.y) ? { x: hit.x, y: start.y } : { x: start.x, y: hit.y });
    else if (tool === 'scatter') placeScatter(start, hit);
    else if (tool === 'zone') {
      const x = Math.max(0, Math.min(start.x, hit.x));
      const y = Math.max(0, Math.min(start.y, hit.y));
      editor.doc.zones.push({ kind: editor.zone, x, y, w: Math.min(editor.doc.w, Math.max(start.x, hit.x) + 1) - x, h: Math.min(editor.doc.h, Math.max(start.y, hit.y) + 1) - y });
    } else if (tool === 'label' && insideDoc(hit.x, hit.y)) {
      const text = prompt('Подпись');
      if (!text) {
        editor.undo.pop();
        return;
      }
      editor.doc.labels.push({ x: hit.x, y: hit.y, text: text.slice(0, 40) });
    }
    editDone();
  };
  svg.addEventListener('pointerup', finish);
  svg.addEventListener('pointercancel', () => { press = null; });
  svg.addEventListener('wheel', event => {
    event.preventDefault();
    const point = toSvgPoint(svg, event.clientX, event.clientY);
    const view = editor.view;
    const factor = event.deltaY < 0 ? 1.15 : 1 / 1.15;
    const w = Math.min(Math.max(view.w / factor, CELL * 4), CELL * 400);
    const h = w * view.h / view.w;
    editor.view = { x: point.x - (point.x - view.x) * w / view.w, y: point.y - (point.y - view.y) * h / view.h, w, h };
    svg.setAttribute('viewBox', [editor.view.x, editor.view.y, editor.view.w, editor.view.h].join(' '));
  }, { passive: false });
}

// Клавиши редактора: только на его странице и не в поле ввода
document.addEventListener('keydown', event => {
  if (!editor.url || !editor.doc) return;
  if (event.target.closest('input, select, textarea')) return;
  const key = event.key.toLowerCase();
  if (key === ' ') {
    editor.space = true;
    event.preventDefault();
    return;
  }
  if ((event.ctrlKey || event.metaKey) && key === 'z') {
    event.preventDefault();
    if (event.shiftKey) redoEdit(); else undoEdit();
    return;
  }
  if ((event.ctrlKey || event.metaKey) && key === 'y') {
    event.preventDefault();
    redoEdit();
    return;
  }
  if ((event.ctrlKey || event.metaKey) && key === 'c' && editor.selection) {
    editor.clipboard = takeSelection(editor.selection, false);
    editorStatus('Скопировано');
    return;
  }
  if ((event.ctrlKey || event.metaKey) && key === 'v' && editor.clipboard && editor.hover) {
    beginEdit();
    putPart(editor.clipboard, editor.hover);
    editor.selection = { x: editor.hover.x, y: editor.hover.y, w: editor.clipboard.w, h: editor.clipboard.h };
    editor.tool = 'select';
    editDone();
    return;
  }
  if (event.ctrlKey || event.metaKey || event.altKey) return;
  if ((key === 'delete' || key === 'backspace') && editor.selection) {
    beginEdit();
    takeSelection(editor.selection, true);
    editDone();
    return;
  }
  if (key === 'q' || key === 'e') {
    if (editor.tool === 'select' && editor.selection) {
      transformSelection(key === 'e' ? rotatePart : part => rotatePart(rotatePart(rotatePart(part))));
    } else {
      editor.rot = (editor.rot + (key === 'e' ? 1 : 3)) % 4;
      renderEditor();
      if (editor.hover) editorPreview(null, editor.hover);
    }
    return;
  }
  if (key === 'm' && editor.selection) {
    transformSelection(mirrorPart);
    return;
  }
  if (/^[1-9]$/.test(key)) {
    const layer = TOOL_LAYERS[editor.tool];
    const list = layer ? editor.data.materials.filter(material => material.layer === layer) : editor.data.objects;
    const chosen = list[Number(key) - 1];
    if (!chosen) return;
    if (layer) editorMemory('material.' + editor.tool, chosen.code);
    else editorMemory('object', chosen.code);
    renderEditor();
    return;
  }
  if (EDITOR_TOOLS[key]) setTool(EDITOR_TOOLS[key]);
});
document.addEventListener('keyup', event => { if (event.key === ' ') editor.space = false; });
