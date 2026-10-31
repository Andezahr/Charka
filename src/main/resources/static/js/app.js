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
      syncName();
      if (location) {
        const canChoose = option.dataset.location === 'true';
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
    targets.forEach(input => input.addEventListener('change', syncName));
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
    show(stored.get());
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
  const data = new URLSearchParams(new FormData(form, e.submitter));
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
