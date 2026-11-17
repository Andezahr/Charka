// Charka, часть фронта «page» — initPage: обработчики элементов страницы, лист вне хода (TURN-02).
// ARC-02: обычный скрипт (не модуль) — части делят одну глобальную область и грузятся по порядку
// (fragments/layout.html, defer): объявления видны всем частям, вызовы идут из обработчиков после загрузки всех.

/*
 * TURN-02: лист в бою вне своего хода — кнопки, которые сервер всё равно отклонит (TurnGuard), неактивны;
 * урон, спасброски, заметки, контакты, профиль и ☆ навыка — можно. Ход пришёл — лист обновляется сам (опрос /encounter/turn).
 */
const TURN_FREE = [/\/health\/(damage|save\/[^/]+)$/, /\/(notes|categories|contacts)(\/|$)/, /\/profile$/, /\/skills\/pin$/, /\/portrait(\/delete)?$/,
  /\/chat$/, /\/requests\/\d+\/roll$/, /\/undo\/\d+$/];
let sheetTurnTimer = null;

function initSheetTurn() {
  clearInterval(sheetTurnTimer);
  const banner = document.querySelector('.alert[data-not-turn][data-character]');
  if (!banner) return;
  document.querySelectorAll('main form').forEach(form => {
    if ((form.getAttribute('method') || 'get').toLowerCase() !== 'post') return;
    const action = new URL(form.action, location.href).pathname;
    if (TURN_FREE.some(pattern => pattern.test(action))) return;
    form.querySelectorAll('button[type="submit"], button:not([type]), input[type="submit"]').forEach(button => {
      button.disabled = true;
      button.title = 'Сейчас ход не ваш';
    });
  });
  sheetTurnTimer = setInterval(async () => {
    if (document.hidden) return;
    try {
      const turn = await (await fetch(banner.dataset.notTurn, {headers: {Accept: 'application/json'}})).json();
      if (!turn.current || String(turn.current) === banner.dataset.character) location.reload();
    } catch (e) { /* сеть — попробуем в следующий раз */ }
  }, 4000);
}

function initPage() {
  revealRolls();
  hideBattleUndo();
  initSheetTurn();

  // R-14: экран боя — до низа окна под шапкой, какой бы высоты она ни была
  const battleScreen = document.querySelector('.battle-screen');
  if (battleScreen) {
    battleScreen.style.setProperty('--battle-offset', Math.round(battleScreen.getBoundingClientRect().top + scrollY + 12) + 'px');
  }

  // UX-26: Космозэки — нажатие на цифру к100 подсвечивает её зону в фигуре; повторное — снимает
  document.querySelectorAll('[data-kz-digits]').forEach(strip => {
    const body = strip.closest('.kz-compact');
    strip.querySelectorAll('[data-kz-digit]').forEach(button => button.addEventListener('click', () => {
      const again = button.classList.contains('is-picked');
      strip.querySelectorAll('.is-picked').forEach(other => other.classList.remove('is-picked'));
      body.querySelectorAll('.kz-fz.is-hit').forEach(zone => zone.classList.remove('is-hit'));
      if (again) return;
      button.classList.add('is-picked');
      const zone = button.dataset.kzZone && body.querySelector('.kz-fz[data-kz-zone="' + button.dataset.kzZone + '"]');
      if (zone) zone.classList.add('is-hit');
    }));
  });

  // UX-21: поиск по длинному выпадающему списку: input[data-select-filter="имя select"] прячет неподходящие варианты
  document.querySelectorAll('[data-select-filter]').forEach(input => {
    const select = input.form && input.form.querySelector('select[name="' + input.dataset.selectFilter + '"]');
    if (!select) return;
    input.addEventListener('input', () => {
      const query = input.value.trim().toLowerCase();
      let first = null;
      [...select.options].forEach(option => {
        const fits = !query || option.text.toLowerCase().includes(query);
        option.hidden = !fits;
        if (fits && !first) first = option;
      });
      if (first && select.selectedOptions[0] && select.selectedOptions[0].hidden) select.value = first.value;
    });
  });

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
    // UX-01: якорь ведёт в другую вкладку (итог действия у предмета в «Рюкзаке») — открыть её
    try {
      const anchored = location.hash && holder.querySelector('#' + CSS.escape(decodeURIComponent(location.hash.slice(1))));
      const anchoredPanel = anchored && anchored.closest('[data-tab-panel]');
      if (anchoredPanel && anchoredPanel.hidden) show(anchoredPanel.dataset.tabPanel);
    } catch (e) { /* якорь не id */ }
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
    // вне боя (лист) по умолчанию — цель без листа: стрельба по своим — редкость
    const fallback = dialog.dataset.attackUnlistedFirst === 'true' && unlisted ? [unlisted] : targets.slice(0, 1);
    (chosen.length ? chosen : fallback).forEach(input => { input.checked = true; });

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
  document.querySelectorAll('select[data-remember]').forEach(rememberSelect);
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
