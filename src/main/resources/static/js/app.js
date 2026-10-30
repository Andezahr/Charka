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

  // Страница навыков: кнопка [data-skills-edit] включает правку уровней. На странице одна форма уровней,
  // поэтому «Отмена» просто возвращает поля к сохранённым значениям
  document.querySelectorAll('[data-skills]').forEach(page => {
    page.querySelectorAll('[data-skills-edit]').forEach(btn => {
      btn.addEventListener('click', () => {
        if (page.classList.toggle('is-editing')) return;
        page.querySelectorAll('.skill-input').forEach(input => { input.value = input.defaultValue; });
      });
    });

    // Проверка навыка: кнопка с итогом открывает <dialog> и подставляет в него навык
    const dialog = page.querySelector('#skill-check-dialog');
    if (!dialog) return;
    page.querySelectorAll('[data-skill-check]').forEach(btn => {
      btn.addEventListener('click', () => {
        dialog.querySelector('[name=code]').value = btn.dataset.code;
        dialog.querySelector('[name=skillId]').value = btn.dataset.skillId || '';
        dialog.querySelector('[data-check-title]').textContent = btn.dataset.name;
        dialog.querySelector('[data-check-sum]').textContent = btn.dataset.sum;
        dialog.showModal();
      });
    });
    dialog.querySelectorAll('[data-dc]').forEach(btn => {
      btn.addEventListener('click', () => {
        dialog.querySelector('[name=difficulty]').value = btn.dataset.dc;
      });
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
