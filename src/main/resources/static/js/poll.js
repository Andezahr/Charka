// Charka, часть фронта «poll» — опросы: «мой ход», запросы бросков, чат.
// ARC-02: обычный скрипт (не модуль) — части делят одну глобальную область и грузятся по порядку
// (fragments/layout.html, defer): объявления видны всем частям, вызовы идут из обработчиков после загрузки всех.

// Запоминаемый выбор одного списка: и на странице, и в копии формы (меню фишки мастера, GM-01)
function rememberSelect(select) {
  const key = 'charka.remember.' + select.dataset.remember;
  try {
    const stored = localStorage.getItem(key);
    if (stored !== null && [...select.options].some(option => option.value === stored)) select.value = stored;
  } catch (e) { /* только удобство */ }
  select.addEventListener('change', () => {
    try { localStorage.setItem(key, select.value); } catch (e) { /* только удобство */ }
  });
}

// A-04: «мой ход» на телефоне игрока. Опрос /turn раз в 2 с; ход сменился — если подошла очередь игрока,
// вибрация, звук (галочка «Звук», запоминается) и заголовок вкладки; экран обновляется сам, если ничего не открыто
const turnWatch = { url: null, turn: null, mine: [], timer: null };
const TURN_SOUND_KEY = 'charka.turnSound';

function initTurnWatch() {
  clearTimeout(turnWatch.timer);
  const holder = document.querySelector('[data-turn-watch]');
  if (!holder) {
    turnWatch.url = null;
    return;
  }
  Object.assign(turnWatch, { url: holder.dataset.turnWatch, turn: holder.dataset.turn, mine: holder.dataset.mine.split(',') });
  if (holder.querySelector('.turn-panel.is-mine') && !document.title.startsWith('▶')) document.title = '▶ Ваш ход · ' + document.title;
  let sound = false;
  try { sound = localStorage.getItem(TURN_SOUND_KEY) === '1'; } catch (e) { /* без памяти — без звука */ }
  holder.querySelectorAll('[data-turn-sound]').forEach(box => {
    box.checked = sound;
    box.addEventListener('change', () => {
      try { localStorage.setItem(TURN_SOUND_KEY, box.checked ? '1' : ''); } catch (e) { /* только удобство */ }
      holder.querySelectorAll('[data-turn-sound]').forEach(other => { other.checked = box.checked; });
      if (box.checked) beep();
    });
  });
  turnWatch.timer = setTimeout(pollTurn, 2000);
}

async function pollTurn() {
  if (!turnWatch.url || !document.querySelector('[data-turn-watch]')) return;
  try {
    const response = await fetch(turnWatch.url, { cache: 'no-store' });
    if (response.ok) {
      const state = await response.json();
      if (state.turn !== turnWatch.turn) {
        turnWatch.turn = state.turn;
        turnChanged(turnWatch.mine.includes(String(state.current)));
        return;
      }
    }
  } catch (e) { /* сеть пропала: попробуем позже */ }
  turnWatch.timer = setTimeout(pollTurn, document.hidden ? 5000 : 2000);
}

function turnChanged(mine) {
  if (mine) {
    if (navigator.vibrate) navigator.vibrate([250, 120, 250]);
    let sound = false;
    try { sound = localStorage.getItem(TURN_SOUND_KEY) === '1'; } catch (e) { /* без звука */ }
    if (sound) beep();
  }
  // открытый диалог, раскрытое меню или поле с вводом не сбрасываем — полоса «Обновить»
  const busy = document.querySelector('dialog[open], details[data-menu][open]')
    || (document.activeElement && document.activeElement.matches('input:not([type=checkbox]), textarea, select') && document.activeElement.value);
  if (busy || submitting) {
    const bar = document.querySelector('[data-turn-changed]');
    if (bar) bar.hidden = false;
    if (mine) document.title = '▶ Ваш ход · ' + document.title.replace(/^▶ Ваш ход · /, '');
    turnWatch.timer = setTimeout(pollTurn, 2000);
    return;
  }
  visit(location.href);
}

// Короткий сигнал без файла: WebAudio, два тона
function beep(tones = [660, 990]) {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    const audio = beep.context || (beep.context = new AudioCtx());
    tones.map((frequency, i) => [i * 0.18, frequency]).forEach(([start, frequency]) => {
      const tone = audio.createOscillator();
      const gain = audio.createGain();
      tone.frequency.value = frequency;
      gain.gain.setValueAtTime(0.18, audio.currentTime + start);
      gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + start + 0.16);
      tone.connect(gain).connect(audio.destination);
      tone.start(audio.currentTime + start);
      tone.stop(audio.currentTime + start + 0.17);
    });
  } catch (e) { /* звук не поддерживается */ }
}

// A-07: запросы бросков от мастера — карточки опрашиваются раз в 3 с (10 с в фоне), подмена только при изменении
const requestsPoll = { timer: null };

function initRequests() {
  clearTimeout(requestsPoll.timer);
  if (!document.querySelector('[data-requests]')) return;
  pollRequests();
}

async function pollRequests() {
  const slots = [...document.querySelectorAll('[data-requests]')];
  if (!slots.length) return;
  for (const slot of slots) {
    try {
      const response = await fetch(slot.dataset.requests, { cache: 'no-store' });
      if (response.ok) {
        const html = (await response.text()).trim();
        if (slot.dataset.html !== html) {
          const had = slot.dataset.html !== undefined && slot.dataset.html !== '';
          slot.dataset.html = html;
          slot.innerHTML = html;
          // новый запрос у игрока — как подошедший ход: короткая вибрация
          if (html && !had && slot.querySelector('.request-card:not(.is-gm)') && navigator.vibrate) navigator.vibrate(150);
        }
      }
    } catch (e) { /* сеть пропала: попробуем позже */ }
  }
  requestsPoll.timer = setTimeout(pollRequests, document.hidden ? 10000 : 3000);
}

// Чат кампании (U-06): лента опрашивается раз в 3 с (раз в 10 с после минуты тишины) с ETag и подменяется целиком,
// форма отправляется как обычно; после отправки курсор снова в поле
const chat = { url: null, etag: null, timer: null, quietSince: 0, refocus: false };

// KF-09: «💬 Только сообщения» — броски, проверки и журнал боя прячутся из ленты у этого игрока, на этом устройстве
const ONLY_MESSAGES_KEY = 'charka.chat.onlyMessages';
function syncOnlyMessages() {
  let on = false;
  try { on = localStorage.getItem(ONLY_MESSAGES_KEY) === '1'; } catch (e) { /* только удобство */ }
  document.querySelectorAll('[data-chat]').forEach(card => card.classList.toggle('only-messages', on));
  document.querySelectorAll('[data-chat-only-messages]').forEach(box => { box.checked = on; });
}
document.addEventListener('change', e => {
  if (!e.target.matches('[data-chat-only-messages]')) return;
  try { localStorage.setItem(ONLY_MESSAGES_KEY, e.target.checked ? '1' : '0'); } catch (err) { /* только удобство */ }
  syncOnlyMessages();
});

function initChat() {
  syncOnlyMessages();
  const card = document.querySelector('[data-chat]');
  clearTimeout(chat.timer);
  if (!card) {
    chat.url = null;
    return;
  }
  if (chat.url !== card.dataset.chat) Object.assign(chat, { url: card.dataset.chat, etag: null, quietSince: Date.now() });
  const me = card.querySelector('[data-me]');
  const key = meKey(chat.url);
  if (me) {
    try {
      const stored = localStorage.getItem(key);
      if (stored !== null && [...me.options].some(option => option.value === stored)) me.value = stored;
    } catch (e) { /* только удобство */ }
    me.addEventListener('change', () => {
      try { localStorage.setItem(key, me.value); } catch (e) { /* только удобство */ }
    });
  }
  const form = card.querySelector('form');
  form.addEventListener('submit', () => { chat.refocus = true; });
  if (chat.refocus) {
    chat.refocus = false;
    const input = form.querySelector('input[name="text"]');
    if (input) input.focus({ preventScroll: true });
  }
  chat.timer = setTimeout(pollChat, 3000);
}

async function pollChat() {
  const card = document.querySelector('[data-chat]');
  if (!card || card.dataset.chat !== chat.url) return;
  if (!document.hidden) {
    try {
      const response = await fetch(chat.url, { cache: 'no-store', headers: chat.etag ? { 'If-None-Match': chat.etag } : {} });
      if (response.status === 200) {
        // первый ответ после загрузки тоже подставляем: между отрисовкой страницы и опросом могли написать
        const changed = chat.etag !== null;
        chat.etag = response.headers.get('ETag');
        const feed = card.querySelector('[data-chat-feed]');
        if (feed) {
          feed.innerHTML = await response.text();
          revealRolls();
        }
        if (changed) chat.quietSince = Date.now();
      }
    } catch (e) { /* сеть пропала: попробуем позже */ }
  }
  chat.timer = setTimeout(pollChat, Date.now() - chat.quietSince > 60000 ? 10000 : 3000);
}

// PH-12: экран не гаснет, пока идёт бой и вкладка видна. Где есть Wake Lock API (localhost, HTTPS) — он; по http в домашней
// сети его нет, тогда — приём NoSleep.js своими руками: беззвучное зацикленное видео в 1 пиксель, запущенное по касанию.
// Видео не лежит файлом: секунду пустого холста записывает MediaRecorder. Выключатель «☀» — запоминается (по умолчанию включён)
const WAKE_KEY = 'charka.wake';
const wake = { lock: null, video: null, armed: false, denied: false };

function wakeWanted() {
  let on = true;
  try { on = localStorage.getItem(WAKE_KEY) !== '0'; } catch (e) { /* без памяти — включено */ }
  return on && !document.hidden && !!document.querySelector('.battle-screen, [data-fight-on]');
}

async function wakeVideo() {
  if (wake.video) return wake.video;
  const video = document.createElement('video');
  Object.assign(video, { muted: true, loop: true, playsInline: true });
  video.setAttribute('playsinline', '');
  video.setAttribute('aria-hidden', 'true');
  video.style.cssText = 'position:fixed;left:0;bottom:0;width:1px;height:1px;opacity:0.01;pointer-events:none;';
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 2;
  const paint = canvas.getContext('2d');
  const stream = canvas.captureStream ? canvas.captureStream(5) : null;
  if (stream && window.MediaRecorder) {
    const chunks = [];
    const recorder = new MediaRecorder(stream);
    recorder.ondataavailable = event => chunks.push(event.data);
    const done = new Promise(resolve => { recorder.onstop = resolve; });
    let tick = 0;
    const draw = setInterval(() => { paint.fillStyle = tick++ % 2 ? '#000' : '#010101'; paint.fillRect(0, 0, 2, 2); }, 100);
    recorder.start();
    await new Promise(resolve => setTimeout(resolve, 1000));
    recorder.stop();
    await done;
    clearInterval(draw);
    stream.getTracks().forEach(track => track.stop());
    video.src = URL.createObjectURL(new Blob(chunks, { type: recorder.mimeType || 'video/webm' }));
  } else if (stream) {
    video.srcObject = stream;
  }
  // вне body: страница обновляется на месте заменой body, видео должно пережить это
  document.documentElement.appendChild(video);
  wake.video = video;
  return video;
}

async function updateWake() {
  const want = wakeWanted();
  if (!want) {
    if (wake.lock) wake.lock.release().catch(() => {});
    wake.lock = null;
    if (wake.video) wake.video.pause();
    return;
  }
  if ('wakeLock' in navigator && !wake.denied) {
    if (wake.lock) return;
    try {
      wake.lock = await navigator.wakeLock.request('screen');
      wake.lock.addEventListener('release', () => { wake.lock = null; });
      return;
    } catch (e) {
      // браузер отказал (нет разрешения, встроенное окно) — запасной способ
      wake.denied = true;
    }
  }
  // видео без касания браузер не запустит: ждём первого касания
  if (!wake.armed) return;
  try {
    const video = await wakeVideo();
    if (wakeWanted() && video.paused) await video.play();
  } catch (e) { /* не вышло — экран гаснет как обычно */ }
}

function initWake() {
  let on = true;
  try { on = localStorage.getItem(WAKE_KEY) !== '0'; } catch (e) { /* включено */ }
  document.querySelectorAll('[data-wake-toggle]').forEach(box => {
    box.checked = on;
    box.addEventListener('change', () => {
      try { localStorage.setItem(WAKE_KEY, box.checked ? '1' : '0'); } catch (e) { /* только удобство */ }
      document.querySelectorAll('[data-wake-toggle]').forEach(other => { other.checked = box.checked; });
      updateWake();
    });
  });
  updateWake();
}

document.addEventListener('visibilitychange', () => updateWake());
document.addEventListener('pointerdown', () => {
  if (wake.armed) return;
  wake.armed = true;
  updateWake();
}, { capture: true });

// PH-22: громкие броски — крит и критический провал (решает система: фишки jchip-crit и jchip-fumble в журнале) всем за столом
// крупной плашкой на 2,5 с, со звуком и вибрацией. Новую запись ленты отдаёт revealRolls (один раз, после перезагрузки — нет);
// скрытые броски мастера (A-09) — нет. Звук — выключатель в меню аккаунта (по умолчанию включён)
const LOUD_SOUND_KEY = 'charka.loudSound';

function loudRoll(row) {
  if (row.classList.contains('is-hidden')) return;
  const chip = row.querySelector('.jchip-crit, .jchip-fumble');
  if (!chip) return;
  const fumble = chip.classList.contains('jchip-fumble');
  const who = row.querySelector('.chat-who');
  const name = who ? [...who.childNodes].filter(node => node.nodeType === Node.TEXT_NODE).map(node => node.textContent).join('').trim() : '';
  const title = row.querySelector('.journal-title');
  const roll = row.querySelector('[data-final]');

  document.querySelectorAll('.loud-roll').forEach(old => old.remove());
  const card = document.createElement('div');
  card.className = 'loud-roll ' + (fumble ? 'is-fumble' : 'is-crit');
  card.setAttribute('role', 'status');
  const face = who && who.querySelector('.avatar');
  if (face) {
    const copy = face.cloneNode(true);
    copy.classList.remove('avatar-xs');
    copy.classList.add('avatar-lg');
    card.appendChild(copy);
  }
  const text = document.createElement('div');
  text.className = 'loud-roll-text';
  [['loud-roll-verdict', fumble ? '💀 ' + chip.textContent : '✨ ' + chip.textContent], ['loud-roll-who', name],
    ['loud-roll-what', title ? title.textContent.trim() : ''], ['loud-roll-total', roll ? roll.dataset.final : '']]
    .filter(([, value]) => value)
    .forEach(([cls, value]) => {
      const line = document.createElement('div');
      line.className = cls;
      line.textContent = value;
      text.appendChild(line);
    });
  card.appendChild(text);
  card.addEventListener('click', () => card.remove());
  document.body.appendChild(card);
  setTimeout(() => card.remove(), 2600);

  if (navigator.vibrate) navigator.vibrate(fumble ? [400] : [80, 60, 80, 60, 160]);
  let sound = true;
  try { sound = localStorage.getItem(LOUD_SOUND_KEY) !== '0'; } catch (e) { /* по умолчанию со звуком */ }
  if (sound) beep(fumble ? [440, 330, 220] : [660, 880, 1320]);
}

function initLoudSound() {
  let on = true;
  try { on = localStorage.getItem(LOUD_SOUND_KEY) !== '0'; } catch (e) { /* включено */ }
  document.querySelectorAll('[data-loud-sound]').forEach(box => {
    box.checked = on;
    box.addEventListener('change', () => {
      try { localStorage.setItem(LOUD_SOUND_KEY, box.checked ? '1' : '0'); } catch (e) { /* только удобство */ }
      if (box.checked) beep([660, 880, 1320]);
    });
  });
}

// браузер даёт звук только после касания: первое касание будит звук, чтобы чужой крит потом прозвучал
document.addEventListener('pointerdown', () => {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    const audio = beep.context || (beep.context = new AudioCtx());
    if (audio.state === 'suspended') audio.resume();
  } catch (e) { /* звука нет */ }
}, { capture: true, once: true });
