// Charka, часть фронта «battle-actions» — действия на поле боя: ход фишкой, лут, меню фишки, атака с карты.
// ARC-02: обычный скрипт (не модуль) — части делят одну глобальную область и грузятся по порядку
// (fragments/layout.html, defer): объявления видны всем частям, вызовы идут из обработчиков после загрузки всех.

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
        // долгое нажатие на выбранную или свою фишку — не атака по себе; GM-01: мастер в «Боге» — меню правки и на ней
        const own = held.token && (held.token.id === battle.selected || String(held.token.character) === battleMe());
        if (held.locked) attackMenu(held.locked, event);
        else if (held.token && (!own || (battleGm() && !npcOnly()))) attackMenu(held.token, event);
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
      const path = known ? battle.reach.cells.get(battleLevel() + ':' + cell.x + ',' + cell.y) : undefined;
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
      battleHint('Играю за NPC: ' + current.locked.name + ' — фишка игрока. Атака по ней — правой кнопкой или долгим нажатием');
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
  // GM-01: на телефоне полоса хода прилипает снизу поверх карты — меню не уходит под неё, остальное прокручивается
  const bar = document.querySelector('.battle-bar');
  const barTop = bar && getComputedStyle(bar).position === 'sticky' ? bar.getBoundingClientRect().top - holder.top : holder.height;
  const room = Math.max(120, Math.min(holder.height, barTop) - 16);
  menu.style.maxHeight = room + 'px';
  menu.style.top = Math.min(Math.max(8, y), Math.max(8, room + 8 - menu.offsetHeight)) + 'px';
  menu.hidden = false;
  menu.style.top = Math.min(Math.max(8, y), Math.max(8, room + 8 - menu.offsetHeight)) + 'px';
}

// R-15a: правка мастера в меню фишки — те же формы, что в боевом блоке участника: урон вручную, инициатива, убрать из боя;
// GM-01: лечение и ＋/− действие раунда. Только в режиме «Бог» (A-15): «За NPC» мастер играет, а не правит
function gmSection(menu, token) {
  if (npcOnly()) return;
  const row = document.getElementById('combatant-' + token.character);
  if (!row) return;
  const forms = [...row.querySelectorAll('.encounter-damage, .encounter-heal, .encounter-actions-adjust, .encounter-minor form')]
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
    copy.querySelectorAll('select[data-remember]').forEach(rememberSelect);
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
  // предел действий системы (Космозэки — одно за ход): кнопки видны, но заняты до следующего раунда
  // BF-02: игрок действует только на своём ходу (мастер — когда угодно)
  const notTurn = row && row.querySelector('[data-not-turn]');
  const spent = notTurn || (row && row.querySelector('[data-no-actions]'));
  if (notTurn) line('Сейчас не ваш ход — действовать можно на своём ходу', 'battle-menu-warn');
  else if (spent) line('Действие в этом раунде сделано — осталось перемещение', 'battle-menu-warn');
  dialogs.forEach(dialog => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm' + (dialog.dataset.attack === last || dialogs.length === 1 ? ' btn-primary' : '');
    const title = dialog.querySelector('h2');
    const why = attackVerdict(dialog, sight, adjacent);
    button.textContent = (title ? title.textContent.replace(/^[^:]*:\s*/, '') : 'Атака') + (why.note ? ' · ' + why.note : '');
    button.disabled = !why.ok || !!spent;
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
