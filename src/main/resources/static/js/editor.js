// Charka, часть фронта «editor» — редактор карты.
// ARC-02: обычный скрипт (не модуль) — части делят одну глобальную область и грузятся по порядку
// (fragments/layout.html, defer): объявления видны всем частям, вызовы идут из обработчиков после загрузки всех.

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
