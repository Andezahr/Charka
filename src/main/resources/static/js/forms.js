// Charka, часть фронта «forms» — догружаемое, форма предмета, окно «Добавить», меню, уведомления.
// ARC-02: обычный скрипт (не модуль) — части делят одну глобальную область и грузятся по порядку
// (fragments/layout.html, defer): объявления видны всем частям, вызовы идут из обработчиков после загрузки всех.

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

// UX-16: в бою плашка «↶ Отменить» не должна закрывать полосу хода — она сверху и через 8 секунд уходит сама
// (наведение или фокус её держат)
const hideBattleUndo = () => {
  const toast = document.querySelector('.battle-screen') && document.querySelector('.undo-toast');
  if (!toast || toast.dataset.autoHide) return;
  toast.dataset.autoHide = '1';
  const timer = setTimeout(() => { if (!toast.matches(':hover, :focus-within')) toast.remove(); }, 8000);
  toast.addEventListener('mouseleave', () => setTimeout(() => toast.isConnected && !toast.matches(':hover, :focus-within') && toast.remove(), 2000), { once: true });
  return timer;
};

// PH-20: портрет — браузер обрезает картинку в квадрат по центру и сжимает до 512 px (WebP, где нет — JPEG) не больше 300 КБ,
// подменяет файл в поле и отправляет форму. Сервер только проверяет тип и размер
const PORTRAIT_SIZE = 512;
const PORTRAIT_MAX_BYTES = 300 * 1024;

async function shrinkPortrait(file) {
  const bitmap = await createImageBitmap(file);
  const side = Math.min(bitmap.width, bitmap.height);
  const size = Math.min(PORTRAIT_SIZE, side);
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  canvas.getContext('2d').drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, size, size);
  bitmap.close && bitmap.close();
  const encode = (type, quality) => new Promise(resolve => canvas.toBlob(resolve, type, quality));
  for (const quality of [0.85, 0.7, 0.55, 0.4]) {
    let blob = await encode('image/webp', quality);
    // Safari старых версий WebP не пишет и отдаёт PNG — тогда JPEG
    if (!blob || blob.type !== 'image/webp') blob = await encode('image/jpeg', quality);
    if (blob && blob.size <= PORTRAIT_MAX_BYTES) return new File([blob], 'portrait.' + blob.type.split('/')[1], { type: blob.type });
  }
  throw new Error('Картинка не сжимается до 300 КБ');
}

document.addEventListener('change', async e => {
  const input = e.target.closest && e.target.closest('input[data-portrait]');
  if (!input || !input.files || !input.files[0]) return;
  const form = input.form;
  form.classList.add('is-busy');
  try {
    const transfer = new DataTransfer();
    transfer.items.add(await shrinkPortrait(input.files[0]));
    input.files = transfer.files;
    form.requestSubmit();
  } catch (error) {
    form.classList.remove('is-busy');
    input.value = '';
    alert('Не удалось прочитать картинку: ' + error.message);
  }
});

// Портрет не отдан (NPC в тумане, удалён) — остаётся буква под ним
document.addEventListener('error', e => {
  if (e.target instanceof HTMLImageElement && e.target.classList.contains('portrait-img')) e.target.remove();
}, true);
