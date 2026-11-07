# Charka — менеджер персонажей для ролевых игр

Веб-приложение для ведущего/игроков настольных РПГ: кампании, персонажи, их раны, инвентари, деньги, заметки и счётчики, плюс игровой календарь кампании («время летит…» → хроника).
Изначально ориентировано на Cyberpunk 2020 (дата старта сида — 2037 г., уровни смертельности ран), в планах — поддержка D&D (ячейки заклинаний и т.п.).

Язык интерфейса и комментариев — русский. Сообщения исключений — смесь русского и английского.

## Рабочий план — `TODO.md`

**Начинай любую работу с [`TODO.md`](TODO.md)**: там текущий план (задачи с ID, «Готово, когда»), целевая архитектура, домашние правила и журнал решений. Сделанное — [`DONE.md`](DONE.md) (закрытую задачу переносить туда), отложенное до решения автора — [`BACKLOG.md`](BACKLOG.md) (оттуда без просьбы автора не брать). Взял задачу — отметь `[~]`, закончил — `[x]` с датой и строкой итога; новые задачи и изменения плана вноси туда же.

**Интерфейс — [`UI_PLAN.md`](UI_PLAN.md)**: разбор экранов на 2026-11-03 и план перегруппировки (задачи R-xx; отложен в `BACKLOG.md`, пока автор рисует эскизы). До эскизов интерфейс не перестраиваем, новые кнопки ставим рядом с похожими.

**Правила CP2020 — сначала [`RULES.md`](RULES.md)**: выжимка для незакрытых задач, с номерами страниц. Новое правило или расхождение с кодом дописывай туда. **Книга — `cyberpunk 2020 rulebook_text.pdf` в корне проекта** (в `.gitignore`, не коммитить): нужна там, где `RULES.md` отсылает к таблице. Значения правил не бери по памяти. Скан с распознанным текстом, удобнее `pdftotext -layout` в scratchpad; страница PDF = страница книги + 3. Текст распознан с ошибками, а в таблицах строки и номера теряются: таблицы читай с изображения страницы. Его показывает браузерная панель: открой файл и задай `location.hash = '#page=64&navpanes=0&toolbar=0&zoom=95,20,40'` с перезагрузкой (x, y — сдвиг), рендер занимает около 9 секунд.

**Функция должна уменьшать действия за столом.** Считай нажатия и ввод на настоящем сценарии и проверяй это в браузере: хорошие значения по умолчанию, запоминание последнего выбора, одно нажатие вместо числа, только то, что применимо к этому оружию или персонажу.

**Домашние правила помечай.** Всё, чего нет в книгах и что решено нами, — комментарий `HOMEBREW` в коде и строка в таблице «Домашние правила» в `TODO.md`: автор хочет потом менять их сам.

**`README.md` — витрина репозитория на GitHub.** Обновляй его при каждом крупном изменении: закрыт этап `TODO.md`, новая игровая система или крупная функция, изменились стек, требования, команды запуска/сборки, параметры БД, структура пакетов или число тестов (бейдж). Мелкие правки и рефакторинг README не трогают.

**Мультисистемность — обязательный принцип.** Приложение должно поддерживать разные НРИ (CP2020 сейчас, D&D 5e далее). Для каждой новой вещи реши: это ядро `[CORE]` (кампании, механизм счётчиков, кубики, инвентарь, деньги, время, трекер боя) или правила системы `[CP2020]`/`[DND5E]` (ХП и броня CP2020, статы, навыки, формулы проверок, типы оружия). Правила системы живут в её пакете и доступны только кампаниям этой системы; ядро обращается к ним через интерфейсы/реестры (`GameSystem`, см. раздел 2 `TODO.md`).

## Стек

- Java 24 (в `pom.xml`; локально стоит JDK 25), Spring Boot 3.5.3
- Spring Web MVC + **Thymeleaf** (серверный рендеринг, формы с POST + redirect; `app.js` выполняет их без перезагрузки). Кэш шаблонов включён: правка шаблона видна после перезапуска или с `THYMELEAF_CACHE=false`
- Spring Security (U-03): вход по логину и паролю, права по кампаниям — `AccessInterceptor`; `charka.security.enabled=false` (профиль тестов) — без входа. CSRF-токен в cookie `XSRF-TOKEN`, `app.js` шлёт его заголовком
- Spring Data JPA / Hibernate, **PostgreSQL 17 в Docker** (`compose.yaml`: контейнер `charka-postgres`, `localhost:5433/charka`, `charka`/`charka_dev_pw`; переопределяется `DB_URL`/`DB_USER`/`DB_PASSWORD`). `spring-boot-docker-compose` поднимает контейнер сам при `spring-boot:run` — нужен запущенный Docker Desktop. Установленный в системе PostgreSQL (5432) не используется
- **Flyway**: схема только миграциями `src/main/resources/db/migration/V<n>__*.sql`, Hibernate `ddl-auto=validate` → любое изменение сущности = новая миграция
- Тесты: H2 в режиме PostgreSQL на тех же миграциях (`application-test.yml`, `@ActiveProfiles("test")` у `@SpringBootTest`) — SQL миграций держать переносимым
- Lombok (`@Data`, `@RequiredArgsConstructor`), Bean Validation
- Тесты: JUnit 5, Spring Boot Test (MockMvc, Mockito)
- Сборка: Maven Wrapper. Профили `dev`/`prod` объявлены в pom, но отдельных `application-*.yml` нет.

## Команды

```bash
./mvnw spring-boot:run      # http://localhost:8080 (слушает 0.0.0.0); сам запускает БД из compose.yaml
docker compose up -d        # БД вручную (данные в volume charka-pgdata)
./mvnw test                 # 1078 тестов, на 2026-11-07 все зелёные (считать после `clean`: старые surefire-отчёты завышают число) (живой PostgreSQL не нужен)
./mvnw spring-boot:build-image   # образ charka/app:latest
```

При старте `DataInitializer` (CommandLineRunner), если кампаний в БД нет, создаёт демо-кампанию «Последний шанс» с персонажами «Колясик» и «Олежа».

## Структура (`src/main/java/app/charka`)

```
model/         JPA-сущности (используются напрямую и как модель для шаблонов, и для JSON-экспорта)
  counters/    Counter + strategy/ (стратегии изменения значения счётчика)
  notes/       Note, NoteCategory
repository/    Spring Data репозитории (по одному на сущность)
service/       бизнес-логика; campaign/, counter/, notes/ — подпакеты. Character, Chronicle, Inventory, Item, Money, Wound — интерфейс здесь (с @Transactional), реализация в impl/ (с @Override и JavaDoc)
controller/    MVC-контроллеры, возвращают имена шаблонов или redirect
api/           REST: CampaignExportController, SkillCheckController (проверка навыка)
dice/         DiceExpression (разбор NdM±K), DiceResult, DiceService (броски + лог RollLog)
attack/       AttackRules (контракт системы: form, plan, spend, outcome, effectHit, isObjectTarget/damageObject — цель-предмет, strayHit — промах в стоящего на линии огня, M-17), LineOfFire и MapBlast (что знает карта, реализации в map/), AttackSource (предмет или способность), ObjectTarget (машина в списке целей), AttackContext, AttackPlan, AttackOutcome, AttackForm, AttackRequest, AttackHit, AreaDamage (урон по площади), LingeringEffect (что остаётся на цели на следующие раунды), AttackResult, AttackService (один ход для всех атак, урон цели, лог, заряды PendingBlast)
creation/     CharacterCreation (мастер создания персонажа: форма, бросок очков, создание; реализует система)
npc/          NpcTemplate, NpcSheet (что выбросила система), NpcService (создание по шаблону, копии, удаление)
security/     SecurityConfig, UserService (регистрация, вход), AccessService (кто что видит: мастер кампании, игрок персонажа), AccessInterceptor (чужое — 404), AuthController, InviteService + InviteController (приглашение по ссылке и QR, A-02), GmMode (A-15: режим мастера на карте «Бог»/«За NPC» — cookie `charka.gmMode`)
chat/         ChatService (чат кампании: сообщения, броски и записи журнала одной лентой; `feedFor` — с «?» вместо врагов в тумане)
journal/      JournalService (J-01: журнал боя — `capture` собирает действие целиком: броски, проверки, события здоровья; одна запись `JournalEntry` с итогом, фишками и расчётом)
request/      RollRequestService (запросы бросков мастера игрокам, групповая проверка — A-07, A-08)
undo/         CampaignSnapshots (снимок игрового состояния кампании строками таблиц с прежними id, восстановление), UndoService (запись, отмена последнего действия, кто может), UndoInterceptor (снимок до и после любого изменяющего запроса), UndoLabels (подпись по адресу)
map/          MapDoc (документ карты: стены-грани, клетки, предметы, зоны, подписи, подложка), MapRules (правила карты системы, модификатор стрельбы вслепую, трудная местность, взрывоопасное), MapObject, Material (M-23: `difficult`, `smoke`, `explosive`), MapLineOfFire (M-17: кто на линии огня — `attack.LineOfFire`), MapBlastService (M-23: взрыв ломает слабое и подрывает бочки — `attack.MapBlast`), MapGrid (что в клетке, видимость цели многими лучами, поле видимости, досягаемость), TokenPlacer, BattleMapService (карты кампании, подложка), BattleFieldService (поле идущего боя), PointerBoard (указатели в памяти), MapGenerator (шаблоны и нарисованные карты из `resources/maps/*.map.txt`), MapText (текстовая карта: чтение и запись, формат — `docs/map-format.md`; новую карту рисуй в нём), MapAudit (проверка играбельности: пути, укрытия, прострел, дистанция, окружение — правила в `docs/map-design.md`), EditableMap. **Карту создаёшь, правишь или переносишь с картинки — сначала навык `.claude/skills/battle-map/SKILL.md`** (инструменты `MapFileCheck`, `MapImageGrid` в тестах)
encounter/    EncounterService (бой кампании: очередь, раунды, ожидание, урон, противостояния), RoundActions (действия участника в раунде, A-05), SceneService (заготовки боя, A-06), CombatContest (противостояние от системы), EncounterView (страница боя), Initiative, InitiativeDeclaration и CombatCard (что отдаёт система)
check/        CheckRequest/CheckResult/CheckResolver (GenericCheckResolver: d20), OpposedCheckResult, CheckService (правила системы + лог)
item/         ItemTypeDefinition, ItemField (поля типа предмета, проверка ввода, строка предмета), CatalogItem (строка справочника), ItemAction (кнопка действия на листе), InstalledBlock (вживлённое: блок фишек над инвентарями), ItemSection (секции инвентаря), ItemSummary (краткая строка), ItemAttachments + ItemChip (что показано под предметом: магазины и патроны фишками); всё это отдаёт GameSystem
skill/        SkillDefinition, CharacterRole, StatValue, SkillOverride (уровень от чипа), SkillSheet, SkillCheck, SkillOpponent, SkillProgression (контракт прокачки), SkillImprovement, SkillService (навыки, роли, проверка, прокачка)
system/        GameSystem, GameSystemRegistry, GenericSystem ("GENERIC"), Currency, ExpenseTemplate (типичная регулярная трата); cp2020/ — Cp2020System ("CP2020"),
               стратегии счётчиков, Cp2020Rules, Cp2020Dice (d10: на 10 один дополнительный кубик), Cp2020CheckResolver, Cp2020Skills (каталог, роли), Cp2020Fumbles (таблица книги), Cp2020SkillRules (языки по семьям, бонусы от других навыков, требования), Cp2020ItemTypes (оружие, магазины, боеприпасы), Cp2020Weapons (справочник оружия книги), Cp2020Ammo (таблица боеприпасов книги), Cp2020Magazines (справочник магазинов), Cp2020MagazineRules (цены, размеры, штрафы магазинов; хоумбрю помечен), Cp2020WeaponActions (выстрел, очередь, смена и снаряжение магазина), Cp2020ItemSheet (краткая строка, фишки под оружием, что предложить в окне «Добавить»), Cp2020Armors (справочник брони книги), Cp2020ArmorRules (пропорциональная броня, слои), Cp2020WornArmor (надетая броня), Cp2020ArmorActions (надеть, снять, починить), Cp2020AttackRules (дальняя атака и метание: план, сложность по дальности, зона, урон), Cp2020AreaAttacks (урон по площади и отклонение), Cp2020Effects (особые заряды: горение, оглушение, слепота, газ), Cp2020Cover (укрытие цели), Cp2020Cyberware (справочник киберware), Cp2020Cyber (установка, хирургия, эффекты, блок на листе), Cp2020Cyberlimbs (киберконечности: сторона, опции, прочность, удар), Cp2020Chips (чипы навыков: справочник, лимит INT, обкатка), Cp2020RepStrategy + Cp2020Facedown (репутация, противостояние в трекере), Cp2020Lifestyle (шаблоны трат: еда, жильё по зонам, услуги), Cp2020Creation (мастер создания: очки, пакеты ролей, зарплаты с. 58), Cp2020Lifepath (лайфпас с. 34–39), Cp2020Melee (ближний бой: встречный бросок, урон, рукопашная), Cp2020MartialArts (ключевые приёмы, с. 100), Cp2020AttackModifiers (таблица модификаторов FNFF), Cp2020SkillProgression + Cp2020IpStrategy (очки улучшения), Cp2020Npcs (быстрые NPC, с. 30), Cp2020MapRules (карта: укрытия как материалы, клетка 2 м)
dto/counter/   DTO только для счётчиков (единственный модуль, переведённый на DTO)
exception/     NotFoundException.forId("Entity", id) (NoSuchElementException → 404), наследники CharacterNotFoundException, InventoryNotFoundException;
               GlobalExceptionHandler (страницы → error.html: 404/400/500), ApiExceptionHandler (app.charka.api → text/plain)
TODO.txt       список планов автора (см. ниже)
```

Шаблоны: `src/main/resources/templates/` — `error.html`, `home.html`, `campaign.html`, `encounter.html`, `character.html`, `notes.html`, `counters/list.html`, `skills/list.html`; общие куски — `fragments/layout.html` (`head(title)`, `topbar(characterId, active)` с вкладками Лист/Навыки/Счётчики/Заметки; «Навыки» — только если у системы есть каталог), `fragments/dice.html` (`card(action, showAuthor)` — карточка «Кубики», `check-result` — итог проверки), `fragments/items.html` (`row(item, inv, owner)` — строка предмета, `chip` — фишка, `add-dialog` — окно «Добавить», `type-fields(types, item)` — выбор типа и поля предмета, редкие за «Дополнительно»; `action-form` — кнопка действия), `fragments/gear.html` (`inventory(inv, gear)` — инвентарь, `money(brief)` — деньги), `fragments/encounter.html` (`actions(row, base)` — действия участника боя, `portrait(row, hidden)` — портрет в карусели очереди); страница боя (R-14) — сетка `.battle-screen`: Char Info и карусель сверху, инструменты слева, карта, вкладки «Стол / Очередь / Мастер» справа, полоса хода снизу, `fragments/attack.html` (`dialog(...)` — диалог атаки, `pending(...)` — упавший заряд, `result(...)` — итог, `target-checks`, `target-options`).
Фронт единый (в адреса подставляется хэш содержимого, `spring.web.resources.chain`): один CSS `static/css/app.css` (тёмная тема, токены в `:root`, шрифты Inter + Russo One с Google Fonts) и один JS `static/js/app.js` — без Bootstrap. Страницы обновляются на месте: формы и ссылки идут через fetch, ответ заменяет `body`, затем снова вызывается `initPage()`, поэтому обработчики элементов живут в ней, а обработчики на `document` — вне её; редирект с якорем отдаёт `InPlaceRedirectFilter`. JS-соглашения: `[data-edit-toggle]` открывает `.edit-panel` внутри ближайшего `[data-editable]`; `form[data-confirm]` спрашивает подтверждение; `[data-count-to]` — счётчик символов; `.alert-close` закрывает flash-сообщение; `[data-item-form]` — поля выбранного типа предмета; `select[data-suggest]` — список типичных значений для текстового поля рядом («Другой…» открывает его), `data-name-prefix` подставляет название предмета; `[data-add-item]` + `[data-add-dialog]` — окно «Добавить» (одно на страницу, вкладка запоминается; `data-add-tab-open` и `data-add-then` — одна вкладка и сразу действие); `details[data-menu]` — выбор у фишки, закрывается нажатием мимо; `[data-dialog-open]` — диалог по кнопке; `[data-attack]` — диалог атаки (поля по режиму, цели нажатием, цель и расстояние запоминаются в localStorage для оружия); `[data-skills]` — страница навыков (фильтр, поиск, правка уровней, диалог проверки); `[data-creation]` — мастер создания персонажа; `[data-requests]` — карточки запросов бросков (опрос); `[data-turn-watch]` — панель игрока в бою: опрос хода, вибрация и звук (`turnWatch` вне `initPage`); `[data-battle]` — поле боя (SVG по JSON карты, опрос с ETag, состояние в `battle` вне `initPage`); `[data-map-editor]` — редактор карты (`editor`); общая отрисовка — `drawMapLayers`; `select[data-remember]` — выбор запоминается; `[data-tabs="ключ"]` с `[data-tab]` и `[data-tab-panel]` — вкладки (выбор запоминается), `[data-tab-go]` открывает вкладку; `[data-select-token]` — выбрать фишку на карте; `[data-battle-ruler]` — линейка (`battle.ruler`); `[data-gm-mode]` — режим мастера «Бог»/«За NPC» (cookie, `applyGmMode`, `npcOnly()`), `[data-god-only]` прячется в «За NPC»; `[data-roll-anim]` (`data-final`, внутри `[data-roll-value]`) — бросок катится (`rollAnimation`): новая запись ленты `[data-feed-key]` один раз, итог действия `[data-roll-now]` всегда, `[data-after-roll]` проявляется после; меню фишки у мастера (`data-gm` на поле) копирует формы правки из боевого блока (`gmSection`). В SpEL `map[var]` берёт ключ строкой «var» — пишите `map.get(var)`. У каждого `fetch` срок 20 с (обёртка в начале app.js, A-14): опрос и формы не должны ждать вечно; ошибки скрипта — `POST /client-error` в журнал сервера. Тяжёлое и редкое (справочник, формы правки) не рендерить в каждой строке: `[data-lazy="url"]` внутри `.edit-panel` грузится по ✎, ответы сжимаются (`server.compression`).

**Данные сначала (R-23).** Список — первым; частое добавление (деньги, предмет, готовый эффект) — строкой или кнопкой рядом с данными; редкое (инвентарь, свой счётчик, своя трата, свой эффект, категория, ручная запись журнала) — кнопка «＋ Что» с диалогом (`[data-dialog-open]`) или раскрытием (`[data-edit-toggle]`, `details.add-own`). Кнопка добавления называет объект: «＋ Предмет», а не «＋ Добавить»/«＋ Создать»; голый «＋» — только у строки, где данные уже введены. Точки у заголовков карточек: `red` — здоровье, бой, эффекты; `green` — деньги; `yellow` — снаряжение и карты; `pink` — время и сессия; без цвета (голубой) — остальное. Значки: эмодзи — предметы и разделы, знаки — управление (＋ ✎ ✕ ⋯ ▾ ↶), ⬆/⬇ — загрузка и выгрузка. Выбор файла — `label.file-pick[data-file-pick]` с `[data-file-name]`.

**Убирая что-то с экрана ради разгрузки, записывай в `BACKLOG.md`, где это вернуть** (пример — таблица «Скрыто с экрана навыков» и пометки в задачах G-05, P-01, I-06, C-03, C-07).

## Доменная модель

```
AppUser (username, passwordHash) — пользователь; мастер своих кампаний, игрок своих персонажей
Campaign (name, system, startDate, currentDate, gm?, inviteToken?, initiativeEachRound, fogOfWar)   — system: код GameSystem (колонка game_system); gm — мастер (U-03); inviteToken — приглашение (A-02); fogOfWar — туман для игроков на карте (M-16)
 ├─ RollLog* (character?, expression, details, total, label, gameDate, createdAt, hidden) — без коллекции в Campaign, не экспортируется
 ├─ ChatMessage* (character?, author, text, gameDate, createdAt) — чат, как лог: без коллекции, не экспортируется
 ├─ JournalEntry* (character?, kind, title, outcome, success?, total?, difficulty?, chips, steps, hidden) — журнал боя (J-01), как лог; RollLog.journalEntryId — бросок внутри записи
 ├─ RollRequest* (character, kind SKILL/SAVE, code, label, difficulty?, groupKey?, doneAt?, success?, total?, result?) — запрос броска от мастера
 ├─ UndoEntry* (userId?, characterId?, label, createdAt, snapshot JSON) — отмена (A-01), последние 20, без коллекции, не экспортируется
 ├─ BattleMap* (name, doc JSON, background) — карта боя, заготовка
 ├─ Scene* (name, battleMapId?, withPlayers, groupPlayers, groupNpcs) └─ SceneNpc* (templateCode?, character?, name?, count, advanced, ambush) — заготовка боя (A-06), без коллекции в Campaign
 ├─ Encounter (round, groupPlayers, groupNpcs, mapDoc — копия карты, battleMapId) └─ EncounterParticipant* (character, initiative, initiativeDetails, turnOrder, done, waiting, declaration, posX, posY, movedMeters, actions) └─ CombatEffect* (label, roundsLeft, damage, attackOption, data) — идущий бой, один на кампанию; закончился — удаляется
 ├─ Chronicle* (name, startDate, endDate)     — запись о прошедшем периоде
 └─ Character* (name, birthDate, npc, roles, player?, mainWeaponId?, pinnedSkills)  — npc: персонаж мастера; roles: коды из GameSystem.getRoles(), таблица character_role; уровень роли = уровень спецспособности
     ├─ CharacterSkill* (skillCode, level, customName?) — код из каталога системы; уровень 0 у обычного навыка не хранится
     ├─ Wound*     (name, severity, date) — журнал здоровья: ручные записи и автозаписи `HealthCounterService` (урон, смерть, медпомощь)
     ├─ Inventory* (name, carried) └─ Item* (name, quantity, cost, description, type, properties) — type: код из GameSystem.getItemTypes()
     ├─ Money*     (name, amount, operationDate) — журнал операций, баланс = сумма amount
     ├─ RecurringExpense* (name, amount, periodDays, nextDate) — регулярная трата, `proceedDate` пишет её в Money
     ├─ Note*      (name ≤255, text ≤1000, createdAt) ←M:N→ NoteCategory (глобальные, не привязаны к персонажу)
     └─ Counter*   (name, value:Integer, type:String)
```

Все связи `@OneToMany(cascade = ALL, orphanRemoval = true)` от родителя. Для JSON-экспорта циклы разрываются `@JsonManagedReference/@JsonBackReference`.
`Campaign` использует `@GeneratedValue` по умолчанию (sequence), остальные — `IDENTITY`.

### Счётчики (самая «архитектурная» часть)

`CounterStrategy` (тип, `getNewValue(String current, Object input)`, `getResetValue()`) → `CounterStrategyFactory` собирает все бины-стратегии в Map по `getCounterType()`. Значения гоняются через `String`, хотя хранятся как `Integer`. Тип счётчика — строка без enum.
Default-методы стратегии: `getSystems()` (пусто = универсальная; форма и `createCounter` показывают/принимают только типы системы кампании), `getDisplayName()`/`getDefaultName()`, `getViewTemplate()` (шаблон с фрагментом `card` — каждый тип рисует свою карточку, `counters/list.html` не трогаем), `getCreateFieldsTemplate()` (фрагмент `create-fields` — доп. поля формы создания, уходят в `attributes[...]`), `usesInitialValue()`, `getRequiredCounterTypes()` (обязательные счётчики: создаются автоматически, удалить их, пока есть зависимый, нельзя), `isUniquePerCharacter()`, `getDisplayOrder()`, `initialize(counter, params)` (незнакомые ключи пропускать — params общие для всех создаваемых вместе), `reset(counter)`, `describe(CounterContext)` → данные карточки в `CounterResponseDto.view`, `isAwardable()` (начислять всем со страницы кампании), `updateAttributes(counter, map)`, `performAction(ctx, action)`.
`Counter.attributes` (`Map<String,Integer>`, таблица `counter_attribute`) — доп. состояние стратегии сверх одного числа. `CounterContext` — счётчик + все счётчики персонажа, через него зависимые стратегии читают друг друга.

Стратегии: `SimpleIntegerCounterStrategy` (`"SIMPLE_INTEGER"`, прибавляет input) и связка CP2020 (`system/cp2020/`, доступна только кампаниям CP2020):
- `strategy/health/HealthCounterStrategy` — точка расширения для систем здоровья (урон, лечение, `healOverTime`, спасброски по коду, стабилизация, `describe` → `HealthView`). `DiceRoller` — кубики (в тестах подменяются).
- `Cp2020StatsStrategy` (`"CP2020_STATS"`): все 9 статов `int/ref/tech/cool/attr/luck/ma/body/emp` + `humanityLoss`. Текущие значения: REF/INT/COOL со штрафами от ран (уровень из счётчика здоровья), REF ещё со штрафом брони, EMP = человечность/10 (человечность = EMP×10 − потеря). Производные (`Cp2020Rules`): бег MA×3 м, прыжок бег/4, переноска BODY×10 кг, подъём BODY×40 кг, BTM, спасбросок = BODY.
- `Cp2020ArmorStrategy` (`"CP2020_ARMOR"`): SP зоны считается из надетых предметов типа «Броня» (`Cp2020WornArmor`, таблица `Cp2020ArmorRules`) и своего слоя счётчика: `sp_<ЗОНА>` — его текущий SP, `max_<ЗОНА>` — полный; `wear()` при пробитии, действие `REPAIR` возвращает полный SP; `encumbrance` — штраф брони к REF.
- `Cp2020HealthStrategy` (`"CP2020_HEALTH"`, требует stats + armor): value = полученный урон 0..40; attributes: `lost_<ЗОНА>`, флаги `stunned/stabilized/dead/deathSavePending`. Порядок урона: зона → −SP (износ −1 при пробитии; AP — половина SP и урона; BYPASS — без брони) → ×2 в голову → −BTM (мин. 1) → >8 в голову = смерть, >8 в конечность = потеря + спасбросок от смерти → >40 = смерть → авто-спасбросок от оглушения. Таблицы — `Cp2020Rules`.
- Карточки — `counters/types/cp2020-*.html`, стили — блок `.cp-*` в конце `app.css` (Russo One только у главного числа: в нём нет символов − ≤ →).
- `HealthCounterService` — операции, не знающие о системе; `healOverTime` вызывается из `CampaignTimelineService.proceedDate` (CP2020 по с. 116: без проверки медика +2 урона в день, иначе 0.5/1/2/3 в день). Медпомощь — `care`: `HealthCounterStrategy.getCareOptions` + бросок навыка медика.

### Время кампании

`CampaignTimelineService.proceedDate(campaignId, days, description)` создаёт `Chronicle` [старая дата → новая], сдвигает `currentDate`, вызывает `GameSystem.onTimePassed` (CP2020 — естественное лечение, обкатка чипов) и списывает наступившие регулярные траты (`RecurringExpenseService.chargeDue`).

## HTTP-маршруты

| Метод | Путь | Что делает |
|---|---|---|
| GET, POST | `/login`, `/register`; POST `/logout` | вход, регистрация (сразу вход; без `repeat` — со страницы входа, «Я новый игрок»), выход |
| GET, POST | `/join/{token}`, POST `/join/{token}/claim/{cid}`; POST `/campaign/{id}/invite/reset` | приглашение (A-02): свободные персонажи «Это я», мастер создания; новая ссылка |
| GET | `/` | свои кампании (мастер или игрок) и свои персонажи |
| POST | `/campaign/{id}/characters/{cid}/player` | `username` → отдать персонажа игроку (пусто — мастеру) |
| POST | `/campaigns` | `name`, `system`, `startDate?` → новая кампания |
| GET | `/campaign/{id}` | страница кампании; `?rolls=N` — страница лога бросков (по 10, новые сверху) |
| POST | `/campaign/{id}/characters` | добавить персонажа (инвентари «На теле» и «В сумке» + счётчики по умолчанию системы) |
| GET, POST | `/campaign/{id}/characters/new`, POST `.../roll`, `.../background` (лайфпас) | мастер создания по правилам системы (`CharacterCreation`; CP2020: очки, статы, роль и 40 карьерных очков, хобби, стартовые деньги) |
| POST | `/campaign/{id}/session` | новая игровая сессия: потраченная Удача возвращается (`GameSystem.restoreLuck`) |
| POST | `/campaign/{id}/award` | `counter` (название), `amount`, `character` (несколько) → начислить всем выбранным (IP, опыт), итог во flash `awardResult` |
| POST | `/campaign/{id}/chronicles` | `name`, `duration` → промотать время |
| POST | `/campaign/{id}/npcs` | `template?`, `name?`, `count`, `advanced` → NPC по шаблону системы или пустой |
| GET | `/campaign/{id}/encounter` | боевой трекер; без боя — выбор участников |
| POST | `/campaign/{id}/encounter/start`, `.../next`, `.../end`, `.../initiative-mode` (`eachRound`, A-13), `.../fog` (`on`, M-16) | `character` (несколько), `groupPlayers?`, `groupNpcs?`, `map?` (пусто — пустая карта, R-14) → начать бой с инициативой; следующий ход или раунд (игрок — только на своём ходу, «Закончить ход»); закончить бой |
| GET | `/character/{id}/print`, `/campaign/{id}/print` (`npc?`) | A-12: лист на бумаге и в PDF (свои стили, A4), все персонажи кампании подряд |
| GET | `/campaign/{id}/reference` | A-11: справка правил системы (фрагмент окна «📖 Правила») |
| GET, POST | `/campaign/{id}/downtime` (`days` или `customDays`, `name?`, `worked` несколько, `healer.<id>`) | A-10: даунтайм одной формой, итог с «↶ Отменить и изменить» |
| GET, POST | `/campaign/{id}/scenes`, `.../{sid}/edit\|delete\|start`, `.../{sid}/npcs` (`who` = `t:код`/`c:id`, `count`, `name?`, `ambush?`), `.../npcs/{nid}/delete\|ambush` | сцены (A-06): заготовки боя, «Начать» — одно нажатие |
| POST | `/campaign/{id}/roll`, `.../check` с `hidden=true`; `/campaign/{id}/rolls/{rid}/reveal` | A-09: скрытый бросок мастера (`HiddenRolls`), показать всем |
| GET, POST | `/campaign/{id}/requests` (`character?` — фрагмент карточек, опрос; POST: `character` несколько, `what` = `skill:код`/`save:код`, `difficulty?`, `rollNow?`), `.../{rid}/roll\|cancel` | запросы бросков мастера (A-07, A-08); итог во flash `requestResults` |
| GET | `/campaign/{id}/encounter/turn` | A-04: чей ход (JSON `turn`, `current`, `name`) — опрос с телефона игрока (`initTurnWatch`) |
| POST | `/campaign/{id}/encounter/participants` | `character` → ввести в идущий бой |
| POST | `/campaign/{id}/encounter/participants/{pid}/contest/{code}` | `target` (персонаж) → противостояние системы (CP2020: `FACEDOWN`), итог в строке, проигравшему эффект |
| POST | `/campaign/{id}/encounter/participants/{pid}/wait\|join\|remove\|initiative\|damage\|stabilize`, `.../save/{code}`, `.../effects/{eid}/remove`, `.../declare/{code}`, `.../check` | ожидание, вступление, убрать, инициатива вручную, урон (`amount`, `location?`, `damageType?`), спасбросок, снять эффект, объявить (CP2020: `FAST_DRAW`), проверка навыка (`skill`, `difficulty?`); итог во flash в строке участника |
| POST | `/character/{id}/npc/copy`, `.../npc/delete` | `count` → копии NPC без ран; удаление только NPC |
| GET | `/api/campaigns/{id}/download` | экспорт кампании в JSON-файл |
| POST | `/campaigns/import` | `file` — JSON экспорта → новая кампания (id новые, ссылки предметов переводятся) |
| POST | `/campaign/{id}/roll`, `/character/{id}/roll` | `expression` или `quick`, `label?` → бросок в лог, результат во flash |
| POST | `/campaign/{id}/check`, `/character/{id}/check` | `modifier`, `difficulty?`, `label?` → проверка по правилам системы, `checkResult` во flash |
| GET | `/character/{id}` | лист персонажа: состояние (компактные счётчики), оружие (R-07), носимые инвентари, деньги кратко |
| GET | `/character/{id}/gear` | R-08: «Снаряжение» — все инвентари, вживлённое, журнал денег, регулярные траты; действия отсюда возвращают сюда (`GearReturnInterceptor`) |
| POST | `/character/{id}/main-weapon` | `item` (пусто — первое) → основное оружие на листе (R-07) |
| GET | `/character/{id}/add-dialog?v=`, `/character/{id}/items/{itemId}/edit-form` | A-03: содержимое окна «Добавить» и форма правки предмета по требованию (app.js `lazyHtml`, `[data-lazy]`) |
| POST | `/character/{id}/wounds`, `.../wounds/{wid}/delete` | раны |
| POST | `/character/{id}/inventories` (`name`, `carried`), `.../{invId}/edit`, `.../{invId}/delete`, `.../{invId}/carried` | инвентари; `carried` — носимый, на листе (R-08) |
| POST | `/character/{id}/inventories/{invId}/items` (+ `/{itemId}/edit|delete|move`) | предметы; `itemType`, `prop.<ключ>` — тип и его поля; `anchor` — куда вернуться; `/catalog` (`catalogKey`, `quantity`, `anchor`, `thenAction?`) — из справочника системы, `thenAction` — сразу действие над новым предметом; `buy=true` (и у своего предмета) — купить: цена × количество списывается записью в деньгах, не хватает — отказ, итог `Purchase` в строке предмета; `/{itemId}/quantity` (`delta`, `returnTo`) — кнопки − и ＋, ноль убирает предмет; `/{itemId}/action/{action}` (`source`, `amount`, `returnTo`) — действие системы (CP2020: `FIRE`, `BURST`, `RELOAD`, `EJECT`, `ADD_MAGAZINE`, `LOAD`, `LOAD_ALL`, `UNLOAD`, `UNJAM`, у брони `WEAR`, `TAKE_OFF`, `REPAIR`, у киберware `INSTALL`, `REMOVE`, `REPAIR`), итог во flash для строки предмета или `returnTo` |
| POST | `/character/{id}/items/{itemId}/attack` | `target` (в зоне огня несколько; `item-<id>` — машина, CP2020: урон в SDP через SP) или `targetName`, `distance`, `location?`, `skill?`, `modifier`, `mode`, `rounds`, `zoneWidth`, `option.<выбор>`, `returnCampaign?` (вернуться в трекер; то же у действий предмета и зарядов) → атака оружием, итог во flash `attackResult` в строке предмета (предмет израсходован — в начале блока инвентарей) |
| POST | `/character/{id}/attack/ability/{code}` | то же без предмета, кодом способности системы (CP2020: `BRAWL`, режимы `STRIKE`, `KICK`, `GRAPPLE`, `BODY_THROW`, `HOLD`, `CHOKE`, `SWEEP`, `DISARM`, `ESCAPE`); только из трекера, итог над строками боя |
| POST | `/character/{id}/attack/blast/{blastId}`, `.../dismiss` | `target` (несколько) → заряд упал мимо (`PendingBlast`): урон выбранным целям; `dismiss` — никого не задело |
| POST | `/character/{id}/money`, `.../money/{mid}/delete` | деньги |
| POST | `/character/{id}/expenses`, `.../expenses/{eid}/delete` | `name`, `amount`, `periodDays` → регулярная трата, списывается при промотке времени |
| POST | `/character/{id}/installed/{action}` | действие блока вживлённого (CP2020: `THERAPY`, `source`, `amount`), итог в блоке |
| GET, POST | `/campaign/{id}/maps`, `.../generate`, `.../{mapId}/edit\|copy\|rename\|delete\|regenerate\|background` | карты кампании, генератор, редактор, подложка; GET/POST `.../{mapId}/doc` — документ (JSON, автосохранение); POST `.../import` (`file` или `text`, `name?`) — карта из текста `MapText` или JSON, GET `.../{mapId}/text` — выгрузить текстом (M-20) |
| GET, POST | `/campaign/{id}/encounter/map` (A-15: мастер с cookie «За NPC» — двигает только NPC и по бегу, `tokens` и `doc` — отказ), `.../participants/{pid}/move\|lift\|reach\|view`, `.../tokens` (мастер: персонаж или NPC по шаблону на клетку), `.../sight`, `.../door`, `.../pointer`, `.../doc` | поле боя: JSON с ETag (опрос; игроку — без фишек в тумане и с `fog`), ход фишки (204/400 текстом), досягаемость, поле видимости (`view`: клетки и видна ли каждая фишка), видимость цели (`sight`: целиком/частично/нет, укрытие, вслепую сквозь, кто на линии огня), дверь, указатель, правка копии; POST `/encounter/attach`, `/save-map`, GET `/edit-map` |
| POST | `/campaign/{id}/encounter/map/break` (`edge`), `.../map/detonate` (`x`, `y`, `by?`) | M-23: выбить дверь, разбить окно (стену — мастер); подорвать бочку или баллон — урон всем в радиусе, цепочка, журнал |
| POST | `/campaign/{id}/undo/{entryId}` | отменить последнее действие кампании (автор или мастер), вернуться на страницу из Referer; плашку «↶ Отменить» после действия кладёт `UndoInterceptor` во flash `undoOffer` |
| GET, POST | `/campaign/{id}/chat` | лента чата и бросков (фрагмент, ETag, опрос); POST `character?`, `text`, `returnTo` → сообщение |
| GET, POST | `/campaign/{id}/settings`, POST `/campaign/{id}/rename` (`name`) | R-13: «Настройки» мастера — название, игроки, приглашение (QR), экспорт |
| GET | `/campaign/{id}/gm` | перенаправляет на обзор (R-10): строки экрана мастера — список персонажей кампании (`fragments/roster :: row`) |
| POST | `/character/{id}/effects`, `.../effects/{eid}/delete` | `template` (готовый эффект системы) или `name`, `stat`, `modifier`, `duration`, `unit` → временный эффект; раунды идут в трекере, дни — при промотке |
| POST | `/character/{id}/contacts`, `.../{cid}/edit\|delete` | контакты: `name`, `role`, `relation`, `owes`, `note` |
| GET | `/character/{id}/notes/` | заметки (фильтр `?categoryId=`) |
| POST | `/character/{id}/notes`, `.../{noteId}/edit|delete`; GET `.../{noteId}/edit` | заметки |
| POST | `/character/{id}/categories`, `.../{catId}/edit|delete` | категории заметок |
| GET, POST | `/character/{id}/skills` | страница навыков; POST `level.<код>`, `custom.<id>` → уровни разом |
| POST | `/character/{id}/skills/custom`, `.../custom/{sid}/delete` | свой навык (язык, эксперт, боевое искусство) |
| POST | `/character/{id}/skills/pin` | `key` (код или `custom:<id>`), `anchor?` → ☆ навык в «Частые навыки» на листе или обратно (R-09a) |
| POST | `/character/{id}/skills/setup` | создать недостающие счётчики системы |
| POST | `/api/characters/{id}/skills/check` | `code` или `skillId`, `modifier`, `difficulty?` → проверка навыка, JSON (диалог, без перезагрузки) |
| POST | `/api/characters/{id}/skills/check/opposed` | то же + `opponentId`, `opponentCode` или `opponentSkillId`, `opponentModifier` (без `opponentId` — `opponentName` и число) → противопоставленная проверка, JSON |
| GET | `/api/characters/{id}/skills` | навыки персонажа для выбора в форме, JSON |
| POST | `/character/{id}/roles` | `role` (несколько) → роли персонажа целиком; новая роль — по правилу системы (CP2020: 4 уровень спецспособности текущей) |
| POST | `/character/{id}/skills/improve` | `code` или `skillId` → следующий уровень за очки системы (CP2020: IP) |
| GET | `/character/{id}/counters` | счётчики |
| POST | `/character/{id}/counters/create`, `/{cid}/update|set|reset`; DELETE `/{cid}` | счётчики |
| POST | `/character/{id}/counters/{cid}/attributes`, `.../{cid}/action/{action}` | правка attributes и особые действия типа (ремонт брони) |
| POST | `/character/{id}/counters/{cid}/health/damage|heal|stabilize`, `.../health/save/{STUN|DEATH}` | счётчик здоровья (итог + пошаговый расчёт во flash `healthSteps`) |
| POST | `/character/{id}/counters/{cid}/health/care` | `healer`, `option`, `modifier` → медпомощь: медик бросает навык (CP2020: стабилизация, First Aid, Medical Tech) |

## Соглашения в коде

- Сервисы: `@Service @RequiredArgsConstructor`, `@Transactional` на запись, `@Transactional(readOnly = true)` на чтение, JavaDoc на русском.
- «Не найдено» → `NotFoundException` (404), сообщение `"Item not found for id=%d"`; `IllegalArgumentException` — только некорректный ввод (400).
- Контроллеры (кроме счётчиков) принимают `@RequestParam` и собирают сущность вручную, после POST — `redirect:/...`.
- Контроллер счётчиков — образец «нового» стиля: DTO + `@Valid` + flash-сообщения `successMessage`/`errorMessage`.
- Тесты разбиты на `@Nested` классы по операциям.
- Имя фрагмента не должно совпадать с тегом HTML (`form`, `slot`…): селектор `~{file :: form}` найдёт элементы `<form>`.
- Отмена (A-01) снимает таблицы списком `CampaignSnapshots.TABLES`: новая таблица игрового состояния персонажа или боя — добавить туда, иначе её изменения не откатываются. Новый изменяющий маршрут вне игры (как заметки) — в `UndoInterceptor.SKIPPED`.

## Известные проблемы и долг (кандидаты на модернизацию)

Исправлено 2026-10-29: edit-эндпоинты ран и денег, кнопка ✎ у предметов (открывала форму инвентаря), `GET /notes` без слеша (был 405), подсветка выбранной категории, рекурсия в JSON-экспорте при наличии счётчиков, битые сид-данные. Единый редизайн всех страниц; добавлены UI для перемещения предмета, переименования категории и удаления счётчика (включён `spring.mvc.hiddenmethod.filter`).

**Открытые вопросы (не баги в текущем UI, но стоит решить)**
- `NoteCategory` с 2026-11-02 принадлежат персонажу; старые общие (character_id IS NULL) видны всем.
- Тесты контроллеров — standalone MockMvc без ViewResolver: если имя вида совпадает с последним сегментом URL (`notes` для `.../notes`), получится «Circular view path» — нужен `setViewResolvers(...)` (см. `NoteControllerTest`).

**Архитектура**
- Сущности отдаются прямо в шаблоны и в JSON; DTO есть только у счётчиков.
- Сервисы ходят в чужие репозитории напрямую (Money/Wound/Inventory → `CharacterRepository`).
- Принадлежность ресурса персонажу проверяет `OwnershipInterceptor` (U-02) по переменным адреса.
- Стратегии счётчиков работают через `String`/`Object`, тип — строка; стратегии лежат в `model/` и знают имена шаблонов.
- Пользователи есть (U-03), но один мастер на кампанию и нет смены пароля.

## Планы автора (`TODO.txt`)

1. Система счётчиков/статов:
   - **ХП** — min/max, восстановление по событиям, дебаффы по порогам; для CP2020 — уровни смертельности.
   - **Опыт** — без максимума, массовое начисление всем игрокам, подсветка при достижении уровня.
   - **Отложенный счётчик** — события копятся и применяются позже.
   - **Мана / ячейки заклинаний** (D&D), **вдохновение**, **броня** (CP2020 SP/абляция).
2. Периодические траты (привязать к `proceedDate`).
3. ~~Броски кубиков с сохранением истории.~~ (D-01..D-03)
4. ~~Возможно — чат кампании.~~ (U-06)

Сделано из TODO: заметки, хроники, раны как отдельная сущность, смена даты, окно трат, базовые счётчики.
