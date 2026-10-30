// Общий JS для всех страниц Charka
document.addEventListener('DOMContentLoaded', () => {

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
    // Строка из адреса могла быть скрыта, пока не отработал render
    const anchored = location.hash && document.getElementById(location.hash.slice(1));
    if (anchored) anchored.scrollIntoView();

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

  // Закрытие уведомлений
  document.querySelectorAll('.alert-close').forEach(btn => {
    btn.addEventListener('click', () => btn.closest('.alert').remove());
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
});
