// Charka, часть фронта «battle» — поле боя: состояние, выбор фишки, уровни, опрос.
// ARC-02: обычный скрипт (не модуль) — части делят одну глобальную область и грузятся по порядку
// (fragments/layout.html, defer): объявления видны всем частям, вызовы идут из обработчиков после загрузки всех.

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
// UX-33: игрок за кого играет, не выбирает — вошёл и играет своими: тот, кто сейчас ходит, иначе выбранный в чате,
// иначе первый свой. Мастер — как выбрал («мастер» — пусто)
const battleMe = () => {
  let stored = '';
  try { stored = localStorage.getItem(meKey(battle.url)) || ''; } catch (e) { /* только удобство */ }
  if (battleGm()) return stored;
  const screen = document.querySelector('.battle-screen');
  const mine = (screen && screen.dataset.mine || '').split(',').filter(Boolean);
  const current = battle.data && battle.data.tokens.find(token => token.current);
  if (current && mine.includes(String(current.character))) return String(current.character);
  return mine.includes(stored) ? stored : (mine[0] || '');
};

// A-15: режим мастера на этом устройстве — cookie, её же проверяет сервер (GmMode). «За NPC» — только свои NPC и по бегу
const GM_MODE_COOKIE = 'charka.gmMode';
const gmModeNpc = () => document.cookie.split('; ').includes(GM_MODE_COOKIE + '=npc');
const battleGm = () => Boolean(battle.stage && battle.stage.dataset.gm === 'true');
// мастер в «За NPC»: фишки игроков не выбираются и не двигаются
const npcOnly = () => battleGm() && gmModeNpc();
// BF-03: игрок управляет только своими персонажами — чужая фишка (враг, NPC) только цель: не выбирается и не тянется
const myCharacters = () => {
  const screen = document.querySelector('.battle-screen');
  return (screen && screen.dataset.mine || '').split(',').filter(Boolean);
};
const lockedToken = token => Boolean(token && ((npcOnly() && !token.npc)
  || (!battleGm() && !myCharacters().includes(String(token.character)))));
function applyGmMode() {
  const npc = gmModeNpc();
  document.querySelectorAll('[data-gm-mode]').forEach(button => {
    // UX-12: режимы словами, понятными без подсказки
    button.textContent = npc ? '👹 Играю за NPC' : '👁 Вижу всех';
    button.classList.toggle('is-active', npc);
    button.title = npc
      ? 'Играю за NPC: ходите только своими NPC и по бегу. Нажмите — «Вижу всех»'
      : 'Вижу всех: любые фишки, Shift — без лимита бега, ＋ Фишка, правка карты. Нажмите — «Играю за NPC»';
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
  // окно кубиков: бросает тот, за кого играем
  document.querySelectorAll('[data-dice-me]').forEach(select => {
    if ([...select.options].some(option => option.value === battleMe())) select.value = battleMe();
  });
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
      battleHint(npc ? 'Играю за NPC: двигаются только NPC, по бегу' : 'Вижу всех: любые фишки, Shift — без лимита бега');
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
    // «уровень:x,y» → метры пути (ML-03: через лестницу — и на соседнем уровне): подсказка при переносе показывает бег
    battle.reach = { meters: reach.meters, run: reach.run, cells: new Map(reach.cells.map(cell => [(cell[3] || 0) + ':' + cell[0] + ',' + cell[1], cell[2]])) };
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
  const chosen = tokenById(id);
  // BF-03: чужую фишку (карусель очереди, «Выбрать») игрок не берёт — только показываем, где она
  if (chosen && lockedToken(chosen)) {
    if (chosen.level !== undefined) battle.level = chosen.level || 0;
    renderBattle();
    return;
  }
  battle.selected = id;
  if (chosen) battle.level = chosen.level || 0;
  battle.reach = null;
  battle.reachFor = null;
  battle.vision = null;
  if (id) loadReach(id);
  renderBattle();
}
