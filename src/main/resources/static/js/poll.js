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
function beep() {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    const audio = beep.context || (beep.context = new AudioCtx());
    [0, 0.18].forEach((start, i) => {
      const tone = audio.createOscillator();
      const gain = audio.createGain();
      tone.frequency.value = i ? 990 : 660;
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
