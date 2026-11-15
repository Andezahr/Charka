// Charka, часть фронта «map-draw» — отрисовка карты (общая для боя и редактора): слои, уровни, линейка, предметы.
// ARC-02: обычный скрипт (не модуль) — части делят одну глобальную область и грузятся по порядку
// (fragments/layout.html, defer): объявления видны всем частям, вызовы идут из обработчиков после загрузки всех.

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
const TERRAIN_COLORS = { water: '#1e40af', asphalt: '#2a303b', grass: '#1f4d2b', smoke: '#9ca3af', void: '#020305', stairs: '#6b5b3e', steps: '#5b4d34' };
// M-23: взрывоопасное — своим цветом, чтобы его было видно издалека
const EXPLOSIVE_COLOR = '#c2410c';

// Карта без фишек: пол, сетка, зоны, местность, предметы, стены, подписи. Общая для поля боя и редактора.
// under(svg) рисует то, что ложится под стены (досягаемость); editing — показать проёмы, двери не нажимаются
// (editing === 'ghost' — нижний этаж под проёмом: без нажатий и подписей).
// ML-01: levels.below — уровень ниже: виден притушенным в клетках пустоты; levels.above — уровень выше: край его
// перекрытия (балкон, мостик) — пунктиром над головой
let voidClipCounter = 0;

// ML-03: фишка на площадке лестницы — в той же клетке лестница и на показанном соседнем уровне
function onLanding(root, token, level) {
  const from = token.level || 0;
  if (token.x === null || Math.abs(from - level) !== 1 || level >= levelCount(root)) return false;
  const key = token.x + ',' + token.y;
  return levelDoc(root, from).cells[key] === 'stairs' && levelDoc(root, level).cells[key] === 'stairs';
}

// ML-01: соседние уровни показанного — для вида сквозь проём и края перекрытия сверху
function neighbourLevels(root, level) {
  const named = l => ({ ...levelDoc(root, l), levelName: levelName(root, l) });
  return {
    below: level > 0 ? named(level - 1) : null,
    above: level + 1 < levelCount(root) ? named(level + 1) : null
  };
}

// Грани между пустотой и полом: края проёмов, балконов и мостков
function voidEdges(doc) {
  const isVoid = (x, y) => doc.cells[x + ',' + y] === 'void';
  const edges = [];
  Object.entries(doc.cells).forEach(([key, code]) => {
    if (code !== 'void') return;
    const [x, y] = key.split(',').map(Number);
    if (y > 0 && !isVoid(x, y - 1)) edges.push([x, y, x + 1, y]);
    if (y + 1 < doc.h && !isVoid(x, y + 1)) edges.push([x, y + 1, x + 1, y + 1]);
    if (x > 0 && !isVoid(x - 1, y)) edges.push([x, y, x, y + 1]);
    if (x + 1 < doc.w && !isVoid(x + 1, y)) edges.push([x + 1, y, x + 1, y + 1]);
  });
  return edges;
}

function drawMapLayers(svg, doc, materials, objects, under, editing, backgroundUrl, levels) {
  const ghost = editing === 'ghost';
  editing = editing === true;
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
  const below = levels && levels.below;
  if (below) {
    const voids = Object.entries(doc.cells).filter(([, code]) => code === 'void');
    if (voids.length) {
      const id = 'void-clip-' + (++voidClipCounter);
      const clip = svgEl('clipPath', { id }, defs);
      voids.forEach(([key]) => {
        const [x, y] = key.split(',').map(Number);
        svgEl('rect', { x: x * CELL, y: y * CELL, width: CELL, height: CELL }, clip);
      });
      const group = svgEl('g', { class: 'battle-below', 'clip-path': 'url(#' + id + ')' }, svg);
      drawMapLayers(group, below, materials, objects, null, 'ghost', null);
    }
  }
  Object.entries(doc.cells).forEach(([key, code]) => {
    const [x, y] = key.split(',').map(Number);
    const material = materials.get(code);
    if (code === 'void' && below) {
      // проём: виден нижний этаж, клетка только с подсказкой
      svgEl('rect', { x: x * CELL, y: y * CELL, width: CELL, height: CELL, class: 'battle-void' }, svg)
        .appendChild(svgEl('title', {})).textContent = (material ? material.label : 'Пустота') + ' · внизу — ' + (below.levelName || 'нижний этаж');
      return;
    }
    const kind = material && material.smoke ? ' battle-smoke' : material && material.difficult ? ' battle-difficult' : '';
    svgEl('rect', { x: x * CELL, y: y * CELL, width: CELL, height: CELL, class: 'battle-terrain' + kind, fill: TERRAIN_COLORS[code] || MATERIAL_COLORS.TERRAIN },
      svg).appendChild(svgEl('title', {})).textContent = (material ? material.label : code)
      + (material && material.difficult ? ' · трудная местность: бег вдвое дороже' : '')
      + (material && material.smoke ? ' · закрывает взгляд, стрельба вслепую' : '');
    if (code === 'steps') {
      // ML-03: ступени марша — поперечные полосы
      for (let i = 1; i <= 3; i++) {
        svgEl('line', { x1: x * CELL + 3, y1: y * CELL + i * CELL / 4, x2: (x + 1) * CELL - 3, y2: y * CELL + i * CELL / 4, class: 'battle-step' }, svg);
      }
    }
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
    if (!editing && !ghost) {
      // M-23: правая кнопка или долгое нажатие по грани — выбить, разбить; дверь ещё и открывается нажатием
      line.dataset.edge = key;
      line.dataset.edgeKind = type;
    }
  });
  if (ghost) return;
  // ML-01: перила по краю проёма своего уровня; край перекрытия уровня выше — пунктиром
  voidEdges(doc).forEach(([x1, y1, x2, y2]) => svgEl('line', {
    x1: x1 * CELL, y1: y1 * CELL, x2: x2 * CELL, y2: y2 * CELL, class: 'battle-rail'
  }, svg));
  if (levels && levels.above) {
    const overhead = svgEl('g', { class: 'battle-overhead' }, svg);
    svgEl('title', {}, overhead).textContent = 'Край перекрытия сверху: ' + (levels.above.levelName || 'этаж выше');
    voidEdges(levels.above).forEach(([x1, y1, x2, y2]) => svgEl('line', {
      x1: x1 * CELL, y1: y1 * CELL, x2: x2 * CELL, y2: y2 * CELL
    }, overhead));
  }
  doc.labels.forEach(label => {
    svgEl('text', { x: (label.x + 0.5) * CELL, y: (label.y + 0.5) * CELL, class: 'battle-label' }, svg).textContent = label.text;
  });
}

function renderBattle() {
  const stage = battle.stage;
  const data = battle.data;
  if (!stage || !data || !stage.isConnected) return;
  const level = battleLevel();
  const doc = levelDoc(data.doc, level);
  const width = doc.w * CELL;
  const height = doc.h * CELL;
  const freshView = !battle.view;
  if (!battle.view) battle.view = { x: -CELL / 2, y: -CELL / 2, w: width + CELL, h: height + CELL };
  // высота поля — по пропорциям карты: широкая улица на телефоне не оставляет пустоты сверху и снизу
  // R-14: на экране боя поле занимает всю середину, высоту задаёт раскладка
  if (stage.clientWidth && !('battleFill' in stage.dataset)) {
    stage.style.height = Math.round(Math.min(innerHeight * 0.7, Math.max(240, stage.clientWidth * (doc.h + 1) / (doc.w + 1)))) + 'px';
  }
  const materials = new Map(data.materials.map(material => [material.code, material]));
  const objects = new Map(data.objects.map(object => [object.code, object]));
  const me = battleMe();
  // UX-15, UX-17: на сенсорном экране вся карта — это клетки по 12 px: фишку пальцем не взять. Клетка — не меньше 44 px,
  // в центре — своя фишка (или чей ход), рамка — по пропорциям поля, без пустых полос; «⤢» по-прежнему показывает всю карту
  if (freshView && !battle.coarseFitted && stage.clientWidth && matchMedia('(pointer: coarse)').matches) {
    battle.coarseFitted = true;
    const minCell = 44;
    if (stage.clientWidth / (battle.view.w / CELL) < minCell) {
      const focus = data.tokens.find(token => String(token.character) === me && token.x !== null)
        || data.tokens.find(token => token.current && token.x !== null);
      const w = stage.clientWidth / minCell * CELL;
      const h = w * (stage.clientHeight || stage.clientWidth) / stage.clientWidth;
      const cx = focus ? (focus.x + 0.5) * CELL : width / 2;
      const cy = focus ? (focus.y + 0.5) * CELL : height / 2;
      battle.view = { x: cx - w / 2, y: cy - h / 2, w, h };
    }
  }
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
  // ML-03: фишка на площадке лестницы стоит на обоих уровнях — видна и тянется с любого из них
  const onLevel = token => (token.level || 0) === level || onLanding(data.doc, token, level);

  const svg = svgEl('svg', {
    viewBox: [battle.view.x, battle.view.y, battle.view.w, battle.view.h].join(' '),
    class: 'battle-svg', role: 'img', 'aria-label': 'Карта боя', 'data-metric': data.metric
  });
  drawMapLayers(svg, doc, materials, objects, svg => {
    if (battle.reach && battle.selected && selectedToken && onLevel(selectedToken)) {
      const group = svgEl('g', { class: 'battle-reach' }, svg);
      battle.reach.cells.forEach((cost, key) => {
        const [cellLevel, xy] = key.split(':');
        if (Number(cellLevel) !== level) return;
        const [x, y] = xy.split(',').map(Number);
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
  }, false, level === 0 ? data.backgroundUrl : null, neighbourLevels(data.doc, level));

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

// ML-04: фишку отпустили в проём на верхнем уровне — прыжок вниз после подтверждения; урон решает мастер
async function jumpToken(token, x, y, free) {
  const level = battleLevel();
  const root = battle.data.doc;
  let below = level - 1;
  while (below > 0 && levelDoc(root, below).cells[x + ',' + y] === 'void') below--;
  const height = ((root.levels[level - 1] || {}).z || 0) - (below > 0 ? root.levels[below - 1].z : 0);
  if (!confirm('Прыгнуть вниз с ' + height + ' м на «' + levelName(root, below) + '»?\nУрон от падения — по решению мастера: в правилах его нет.')) return;
  try {
    const response = await fetch(battle.url + '/participants/' + token.id + '/jump', {
      method: 'POST', body: new URLSearchParams({ x, y, free: Boolean(free) })
    });
    const text = await response.text();
    if (!response.ok) {
      battleError(text);
      return;
    }
    battle.level = below;
    battleHint(text);
    await visit(location.href);
  } catch (e) {
    battleError('Нет связи с сервером: фишка не сдвинулась');
  }
}

async function moveToken(token, x, y, free) {
  const level = battleLevel();
  if (level > 0 && (token.level || 0) === level && levelDoc(battle.data.doc, level).cells[x + ',' + y] === 'void') {
    return jumpToken(token, x, y, free);
  }
  const before = { x: token.x, y: token.y, level: token.level };
  // M-22, ML-03: на другой уровень — из лотка, мастер без ограничения или с площадки лестницы (путь сверит сервер)
  if (token.x === null || free || onLanding(battle.data.doc, token, battleLevel())) token.level = battleLevel();
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
