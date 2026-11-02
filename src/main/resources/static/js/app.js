// Общий JS для всех страниц Charka

// Обработчики элементов страницы: вызывается при загрузке и после каждого обновления на месте.
// Обработчики на document сюда не класть, они накопятся: они в конце файла
function initPage() {

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
        const first = panel.querySelector('input:not([type=hidden]), select, textarea');
        if (first) first.focus();
      }
    });
  });

  // Подтверждение для опасных действий: <form data-confirm="Текст?">
  document.querySelectorAll('form[data-confirm]').forEach(form => {
    form.addEventListener('submit', e => {
      if (!confirm(form.dataset.confirm)) e.preventDefault();
    });
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

  // Форма предмета: показываем поля выбранного типа; поля других типов выключены и не отправляются
  document.querySelectorAll('[data-item-form]').forEach(form => {
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
  document.querySelectorAll('select[data-suggest]').forEach(select => {
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

  // Окно «Добавить»: одно на страницу, инвентарь подставляет кнопка. Запоминает последнюю вкладку и возвращает к инвентарю
  document.querySelectorAll('[data-add-dialog]').forEach(dialog => {
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
    document.querySelectorAll('[data-add-item]').forEach(button => button.addEventListener('click', () => {
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
    }));

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

  initBattle();
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
  initPage();
};

const fetchPage = async (url, init) => {
  const response = await fetch(url, { cache: 'no-cache', ...init, headers: { [IN_PLACE_HEADER]: '1' } });
  const redirect = response.headers.get(LOCATION_HEADER);
  if (redirect) return fetchPage(new URL(redirect, location.href).href);
  return { response, url };
};

// Сервер недоступен: действие могло не выполниться, страница и введённое остаются
const showConnectionError = () => {
  const alert = document.createElement('div');
  alert.className = 'alert alert-error';
  alert.setAttribute('role', 'alert');
  const text = document.createElement('span');
  text.className = 'alert-text';
  text.textContent = 'Нет связи с сервером. Проверьте, прошло ли действие, и повторите.';
  const close = document.createElement('button');
  close.type = 'button';
  close.className = 'alert-close';
  close.setAttribute('aria-label', 'Закрыть');
  close.textContent = '×';
  alert.append(text, close);
  (document.querySelector('main') || document.body).prepend(alert);
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
    if (ticket === lastVisit) showConnectionError();
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
  await visit(new URL(action, location.href).href, { method: 'POST', body: data }, undefined, e.submitter || form);
  submitting = false;
  document.documentElement.classList.remove('is-busy');
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
  url: null, data: null, etag: null, timer: null, view: null, selected: undefined, reach: null, reachFor: null,
  lastChange: 0, dragging: false, pointerMode: false, placing: null, seen: new Map(), turn: null, stage: null
};

const svgEl = (name, attrs, parent) => {
  const el = document.createElementNS(SVG_NS, name);
  Object.entries(attrs || {}).forEach(([key, value]) => el.setAttribute(key, value));
  if (parent) parent.appendChild(el);
  return el;
};

const battleMe = () => {
  try { return localStorage.getItem('charka.battle.me.' + battle.url) || ''; } catch (e) { return ''; }
};

// Мера «прямая»: та же формула, что MapRules.distance на сервере
const battleMeters = (x1, y1, x2, y2) => Math.round(Math.hypot(x2 - x1, y2 - y1) * battle.data.doc.cell);

const tokenById = id => battle.data && battle.data.tokens.find(token => token.id === id);

function initBattle() {
  const stage = document.querySelector('[data-battle]');
  if (!stage) {
    clearTimeout(battle.timer);
    battle.stage = null;
    return;
  }
  if (battle.url !== stage.dataset.battle) {
    Object.assign(battle, { url: stage.dataset.battle, data: null, etag: null, view: null, selected: undefined, reach: null, reachFor: null });
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
      try { localStorage.setItem('charka.battle.me.' + battle.url, me.value); } catch (e) { /* только удобство */ }
      battle.selected = undefined;
      renderBattle();
    });
  }
  const pointerButton = document.querySelector('[data-battle-pointer]');
  if (pointerButton) {
    pointerButton.classList.toggle('is-active', battle.pointerMode);
    pointerButton.addEventListener('click', () => {
      battle.pointerMode = !battle.pointerMode;
      pointerButton.classList.toggle('is-active', battle.pointerMode);
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
    battle.reach = { meters: reach.meters, cells: new Set(reach.cells.map(cell => cell[0] + ',' + cell[1])) };
    renderBattle();
  } catch (e) { /* без области: только подсказка */ }
}

function selectToken(id) {
  battle.selected = id;
  battle.reach = null;
  battle.reachFor = null;
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

function renderBattle() {
  const stage = battle.stage;
  const data = battle.data;
  if (!stage || !data || !stage.isConnected) return;
  const doc = data.doc;
  const width = doc.w * CELL;
  const height = doc.h * CELL;
  if (!battle.view) battle.view = { x: -CELL / 2, y: -CELL / 2, w: width + CELL, h: height + CELL };
  // высота поля — по пропорциям карты: широкая улица на телефоне не оставляет пустоты сверху и снизу
  if (stage.clientWidth) {
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
    const current = data.tokens.find(token => token.current && token.x !== null);
    const chosen = mine || (me === '' ? current : null);
    if (chosen) {
      battle.selected = chosen.id;
      loadReach(chosen.id);
    }
  }

  const svg = svgEl('svg', {
    viewBox: [battle.view.x, battle.view.y, battle.view.w, battle.view.h].join(' '),
    class: 'battle-svg', role: 'img', 'aria-label': 'Карта боя', 'data-metric': data.metric
  });
  const defs = svgEl('defs', {}, svg);
  const pattern = svgEl('pattern', { id: 'battle-grid', width: CELL, height: CELL, patternUnits: 'userSpaceOnUse' }, defs);
  svgEl('path', { d: `M ${CELL} 0 L 0 0 0 ${CELL}`, class: 'battle-gridline' }, pattern);
  svgEl('rect', { x: 0, y: 0, width, height, class: 'battle-floor' }, svg);
  svgEl('rect', { x: 0, y: 0, width, height, fill: 'url(#battle-grid)' }, svg);

  doc.zones.forEach(zone => svgEl('rect', {
    x: zone.x * CELL, y: zone.y * CELL, width: zone.w * CELL, height: zone.h * CELL, class: 'battle-zone ' + zone.kind
  }, svg));
  Object.entries(doc.cells).forEach(([key, code]) => {
    const [x, y] = key.split(',').map(Number);
    const material = materials.get(code);
    svgEl('rect', { x: x * CELL, y: y * CELL, width: CELL, height: CELL, class: 'battle-terrain', fill: MATERIAL_COLORS.TERRAIN },
      svg).appendChild(svgEl('title', {})).textContent = material ? material.label : code;
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
          fill: MATERIAL_COLORS.OBJECT, 'fill-opacity': material && material.sp >= 30 ? 0.95 : 0.7
        }, group);
      }
    }
    svgEl('rect', { x: stamp.x * CELL, y: stamp.y * CELL, width: w * CELL, height: h * CELL, class: 'battle-stamp-outline' }, group);
  });

  if (battle.reach && battle.selected) {
    const group = svgEl('g', { class: 'battle-reach' }, svg);
    battle.reach.cells.forEach(key => {
      const [x, y] = key.split(',').map(Number);
      svgEl('rect', { x: x * CELL, y: y * CELL, width: CELL, height: CELL }, group);
    });
  }

  Object.entries(doc.walls).forEach(([key, value]) => {
    const [x, y, dir] = key.split(',');
    const cx = Number(x) * CELL;
    const cy = Number(y) * CELL;
    const [kind, , state] = value.split(':');
    if (value === 'gap') return;
    const line = svgEl('line', dir === 'h'
      ? { x1: cx, y1: cy, x2: cx + CELL, y2: cy }
      : { x1: cx, y1: cy, x2: cx, y2: cy + CELL }, svg);
    const type = kind === 'door' ? 'door' : kind === 'window' ? 'window' : 'wall';
    line.setAttribute('class', 'battle-edge ' + type + (state === 'open' ? ' open' : ''));
    const material = materials.get(type === 'wall' ? kind : value.split(':')[1]);
    const title = svgEl('title', {}, line);
    title.textContent = (material ? material.label + (material.sp ? ' SP ' + material.sp : '') : value)
      + (type === 'door' ? (state === 'open' ? ' · открыта, нажмите — закрыть' : ' · закрыта, нажмите — открыть') : '');
    if (type === 'door') line.dataset.edge = key;
  });
  doc.labels.forEach(label => {
    svgEl('text', { x: (label.x + 0.5) * CELL, y: (label.y + 0.5) * CELL, class: 'battle-label' }, svg).textContent = label.text;
  });

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

  data.tokens.filter(token => token.x !== null).forEach(token => {
    const group = svgEl('g', {
      class: 'battle-token' + (token.npc ? ' npc' : ' player') + (token.current ? ' is-current' : '') + (token.out ? ' is-out' : '')
        + (token.done ? ' is-done' : '') + (token.alarming ? ' is-alarming' : '') + (token.id === battle.selected ? ' is-selected' : '')
        + (String(token.character) === me ? ' is-me' : ''),
      transform: `translate(${(token.x + 0.5) * CELL} ${(token.y + 0.5) * CELL})`,
      tabindex: 0, 'data-token': token.id, role: 'button',
      'aria-label': token.name + (token.status ? ', ' + token.status : '')
    }, svg);
    svgEl('title', {}, group).textContent = token.name + (token.status ? ' · ' + token.status : '');
    svgEl('circle', { r: CELL * 0.46, class: 'battle-token-ring' }, group);
    svgEl('circle', { r: CELL * 0.38, class: 'battle-token-body' }, group);
    svgEl('text', { class: 'battle-token-text', dy: '0.35em' }, group).textContent = token.initials;
  });

  stage.replaceChildren(svg);
  wireBattleSvg(svg);
  renderTray();
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

async function moveToken(token, x, y) {
  const before = { x: token.x, y: token.y };
  token.x = x;
  token.y = y;
  renderBattle();
  try {
    const response = await fetch(battle.url + '/participants/' + token.id + '/move', {
      method: 'POST', body: new URLSearchParams({ x, y })
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
    const tokenEl = event.target.closest('[data-token]');
    const start = cellAt(event);
    // после захвата указателя pointerup приходит на сам svg: дверь запоминаем здесь
    const door = event.target.closest('[data-edge]');
    press = { tokenEl, token: tokenEl ? tokenById(Number(tokenEl.dataset.token)) : null, door: door && door.dataset.edge,
      x: event.clientX, y: event.clientY, start, moved: false };
    svg.setPointerCapture(event.pointerId);
    if (!tokenEl && !battle.pointerMode) {
      // долгое нажатие на пустой клетке — указатель для всех
      longPress = setTimeout(() => {
        if (press && !press.moved && inside(start)) {
          sendPointer(start.x, start.y);
          press = null;
        }
      }, 600);
    }
  });

  svg.addEventListener('pointermove', event => {
    if (!press) return;
    const dx = event.clientX - press.x;
    const dy = event.clientY - press.y;
    if (!press.moved && Math.hypot(dx, dy) < 5) return;
    press.moved = true;
    clearTimeout(longPress);
    if (press.token && !battle.pointerMode) {
      battle.dragging = true;
      const cell = cellAt(event);
      press.tokenEl.setAttribute('transform', `translate(${cell.point.x} ${cell.point.y})`);
      const meters = battleMeters(press.token.x, press.token.y, cell.x, cell.y);
      const far = battle.reach && battle.reachFor === press.token.id && battle.reach.meters
        && !battle.reach.cells.has(cell.x + ',' + cell.y);
      press.tokenEl.classList.toggle('is-far', Boolean(far));
      // HOMEBREW-подсказка: дальше бега — второе действие, −3 (с. 98); учёта действий на сервере нет
      battleHint(meters + ' м' + (far ? ' — дальше бега: второе действие, −3' : ''));
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
    if (battle.pointerMode) {
      if (!inside(cell)) return;
      const from = current.token && current.moved && current.token.x !== null ? { x: current.token.x, y: current.token.y } : null;
      sendPointer(cell.x, cell.y, from);
      return;
    }
    if (current.token && current.moved) {
      if (inside(cell) && (cell.x !== current.token.x || cell.y !== current.token.y)) moveToken(current.token, cell.x, cell.y);
      else renderBattle();
      return;
    }
    if (current.moved) return;
    if (event.altKey && inside(cell)) {
      sendPointer(cell.x, cell.y);
      return;
    }
    if (current.door && !current.token) {
      toggleDoor(current.door);
      return;
    }
    if (current.token) {
      tokenClick(current.token, event);
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
        tokenClick(token, null);
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
    const response = await fetch(battle.url + '/door', { method: 'POST', body: new URLSearchParams({ edge }) });
    if (!response.ok) battleError(await response.text());
  } catch (e) {
    battleError('Нет связи с сервером');
  }
  battle.lastChange = Date.now();
  pollBattle();
}

const closeBattleMenu = () => {
  const menu = document.querySelector('[data-battle-menu]');
  if (menu) menu.hidden = true;
};

// Своя фишка выбирается, чужая при выбранной своей — атака: оружие, цель, расстояние и укрытие уже в диалоге
async function tokenClick(token, event) {
  if (!token) return;
  const attacker = tokenById(battle.selected);
  if (!attacker || attacker.id === token.id || attacker.x === null || token.x === null) {
    closeBattleMenu();
    selectToken(attacker && attacker.id === token.id ? null : token.id);
    return;
  }
  const menu = document.querySelector('[data-battle-menu]');
  if (!menu) return;
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

  menu.replaceChildren();
  const head = document.createElement('div');
  head.className = 'battle-menu-head';
  head.textContent = `${attacker.name} → ${token.name} · ${sight.meters} м`;
  menu.appendChild(head);
  if (sight.blocked) {
    const warn = document.createElement('div');
    warn.className = 'battle-menu-warn';
    warn.textContent = 'Нет линии видимости';
    menu.appendChild(warn);
  } else if (sight.coverLabel) {
    const cover = document.createElement('div');
    cover.className = 'battle-menu-note';
    cover.textContent = 'Укрытие: ' + sight.coverLabel;
    menu.appendChild(cover);
  }
  dialogs.forEach(dialog => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm' + (dialog.dataset.attack === last || dialogs.length === 1 ? ' btn-primary' : '');
    const title = dialog.querySelector('h2');
    button.textContent = title ? title.textContent.replace(/^[^:]*:\s*/, '') : 'Атака';
    button.disabled = sight.blocked;
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
  const select = document.createElement('button');
  select.type = 'button';
  select.className = 'btn btn-sm btn-ghost';
  select.textContent = 'Выбрать ' + token.name;
  select.addEventListener('click', () => {
    closeBattleMenu();
    selectToken(token.id);
  });
  menu.appendChild(select);
  const holder = menu.parentElement.getBoundingClientRect();
  const x = event ? event.clientX - holder.left : holder.width / 2;
  const y = event ? event.clientY - holder.top : holder.height / 2;
  menu.style.left = Math.min(Math.max(8, x), holder.width - 220) + 'px';
  menu.style.top = Math.max(8, y) + 'px';
  menu.hidden = false;
}

// Тот же диалог, что в строке трекера: цель, расстояние и укрытие подставлены, всё можно поправить
function openAttackFromMap(dialog, target, sight) {
  const inputs = [...dialog.querySelectorAll('input[name="target"]')];
  const chosen = inputs.find(input => input.value === String(target.character));
  if (chosen) {
    inputs.forEach(input => { input.checked = input === chosen; });
    chosen.dispatchEvent(new Event('change', { bubbles: true }));
  }
  // зона взрыва: отмечены все, кто в радиусе от цели
  const radius = Number(dialog.dataset.radius || 0);
  const mode = dialog.querySelector('[data-attack-mode]');
  const multi = mode && mode.selectedOptions[0] && mode.selectedOptions[0].dataset.multi === 'true';
  if (radius > 0 && multi && battle.data) {
    battle.data.tokens.forEach(other => {
      if (other.x === null || other.id === target.id) return;
      const input = inputs.find(candidate => candidate.value === String(other.character));
      if (input && battleMeters(target.x, target.y, other.x, other.y) <= radius) input.checked = true;
    });
  }
  const distance = dialog.querySelector('input[name="distance"]');
  if (distance) {
    distance.value = sight.meters;
    distance.dispatchEvent(new Event('input', { bubbles: true }));
  }
  const cover = dialog.querySelector('select[name="option.cover"]');
  if (cover) cover.value = sight.cover || '';
  if (cover && sight.cover) {
    const more = cover.closest('details');
    if (more) more.open = true;
  }
  dialog.showModal();
}
