# Charka — менеджер персонажей для ролевых игр

Веб-приложение для ведущего/игроков настольных РПГ: кампании, персонажи, их раны, инвентари, деньги, заметки и счётчики, плюс игровой календарь кампании («время летит…» → хроника).
Изначально ориентировано на Cyberpunk 2020 (дата старта сида — 2037 г., уровни смертельности ран), в планах — поддержка D&D (ячейки заклинаний и т.п.).

Язык интерфейса и комментариев — русский. Сообщения исключений — смесь русского и английского.

## Рабочий план — `TODO.md`

**Начинай любую работу с [`TODO.md`](TODO.md)**: там приоритизированный план (задачи с ID, «Готово, когда»), целевая архитектура и журнал решений. Взял задачу — отметь `[~]`, закончил — `[x]` с датой и строкой итога; новые задачи и изменения плана вноси туда же.

**Мультисистемность — обязательный принцип.** Приложение должно поддерживать разные НРИ (CP2020 сейчас, D&D 5e далее). Для каждой новой вещи реши: это ядро `[CORE]` (кампании, механизм счётчиков, кубики, инвентарь, деньги, время, трекер боя) или правила системы `[CP2020]`/`[DND5E]` (ХП и броня CP2020, статы, навыки, формулы проверок, типы оружия). Правила системы живут в её пакете и доступны только кампаниям этой системы; ядро обращается к ним через интерфейсы/реестры (`GameSystem`, см. раздел 2 `TODO.md`).

## Стек

- Java 24 (в `pom.xml`; локально стоит JDK 25), Spring Boot 3.5.3
- Spring Web MVC + **Thymeleaf** (серверный рендеринг, формы с POST + redirect)
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
./mvnw test                 # 195 тестов, на 2026-10-30 все зелёные (считать после `clean`: старые surefire-отчёты завышают число) (живой PostgreSQL не нужен)
./mvnw spring-boot:build-image   # образ charka/app:latest
```

При старте `DataInitializer` (CommandLineRunner), если кампаний в БД нет, создаёт демо-кампанию «Последний шанс» с персонажами «Колясик» и «Олежа».

## Структура (`src/main/java/app/charka`)

```
model/         JPA-сущности (используются напрямую и как модель для шаблонов, и для JSON-экспорта)
  counters/    Counter + strategy/ (стратегии изменения значения счётчика)
  notes/       Note, NoteCategory
repository/    Spring Data репозитории (по одному на сущность)
service/       бизнес-логика; campaign/, counter/, notes/ — подпакеты
controller/    MVC-контроллеры, возвращают имена шаблонов или redirect
api/           REST: CampaignExportController (единственный JSON-эндпоинт)
system/        GameSystem, GameSystemRegistry, GenericSystem ("GENERIC"), Currency; cp2020/Cp2020System ("CP2020")
dto/counter/   DTO только для счётчиков (единственный модуль, переведённый на DTO)
exception/     NotFoundException.forId("Entity", id) (NoSuchElementException → 404), наследники CharacterNotFoundException, InventoryNotFoundException;
               GlobalExceptionHandler (страницы → error.html: 404/400/500), ApiExceptionHandler (app.charka.api → text/plain)
TODO.txt       список планов автора (см. ниже)
```

Шаблоны: `src/main/resources/templates/` — `error.html`, `home.html`, `campaign.html`, `character.html`, `notes.html`, `counters/list.html`; общие куски — `fragments/layout.html` (`head(title)`, `topbar(characterId, active)` с вкладками Лист/Счётчики/Заметки).
Фронт единый: один CSS `static/css/app.css` (тёмная тема, токены в `:root`, шрифты Inter + Russo One с Google Fonts) и один JS `static/js/app.js` — без Bootstrap. JS-соглашения: `[data-edit-toggle]` открывает `.edit-panel` внутри ближайшего `[data-editable]`; `form[data-confirm]` спрашивает подтверждение; `[data-count-to]` — счётчик символов; `.alert-close` закрывает flash-сообщение.

## Доменная модель

```
Campaign (name, system, startDate, currentDate)   — system: код GameSystem (колонка game_system)
 ├─ Chronicle* (name, startDate, endDate)     — запись о прошедшем периоде
 └─ Character* (name, birthDate)
     ├─ Wound*     (name, severity, date)
     ├─ Inventory* (name) └─ Item* (name, quantity, cost, description)
     ├─ Money*     (name, amount, operationDate) — журнал операций, баланс = сумма amount
     ├─ Note*      (name ≤255, text ≤1000, createdAt) ←M:N→ NoteCategory (глобальные, не привязаны к персонажу)
     └─ Counter*   (name, value:Integer, type:String)
```

Все связи `@OneToMany(cascade = ALL, orphanRemoval = true)` от родителя. Для JSON-экспорта циклы разрываются `@JsonManagedReference/@JsonBackReference`.
`Campaign` использует `@GeneratedValue` по умолчанию (sequence), остальные — `IDENTITY`.

### Счётчики (самая «архитектурная» часть)

`CounterStrategy` (тип, `getNewValue(String current, Object input)`, `getResetValue()`) → `CounterStrategyFactory` собирает все бины-стратегии в Map по `getCounterType()`. Значения гоняются через `String`, хотя хранятся как `Integer`. Тип счётчика — строка без enum.
Default-методы стратегии: `getSystems()` (пусто = универсальная; форма и `createCounter` показывают/принимают только типы системы кампании), `getDisplayName()`/`getDefaultName()`, `getViewTemplate()` (шаблон с фрагментом `card` — каждый тип рисует свою карточку, `counters/list.html` не трогаем), `getCreateFieldsTemplate()` (фрагмент `create-fields` — доп. поля формы создания, уходят в `attributes[...]`), `usesInitialValue()`, `getRequiredCounterTypes()` (обязательные счётчики: создаются автоматически, удалить их, пока есть зависимый, нельзя), `isUniquePerCharacter()`, `getDisplayOrder()`, `initialize(counter, params)` (незнакомые ключи пропускать — params общие для всех создаваемых вместе), `reset(counter)`, `describe(CounterContext)` → данные карточки в `CounterResponseDto.view`, `updateAttributes(counter, map)`, `performAction(ctx, action)`.
`Counter.attributes` (`Map<String,Integer>`, таблица `counter_attribute`) — доп. состояние стратегии сверх одного числа. `CounterContext` — счётчик + все счётчики персонажа, через него зависимые стратегии читают друг друга.

Стратегии: `SimpleIntegerCounterStrategy` (`"SIMPLE_INTEGER"`, прибавляет input) и связка CP2020 (`system/cp2020/`, доступна только кампаниям CP2020):
- `strategy/health/HealthCounterStrategy` — точка расширения для систем здоровья (урон, лечение, `healOverTime`, спасброски по коду, стабилизация, `describe` → `HealthView`). `DiceRoller` — кубики (в тестах подменяются).
- `Cp2020StatsStrategy` (`"CP2020_STATS"`): все 9 статов `int/ref/tech/cool/attr/luck/ma/body/emp` + `humanityLoss`. Текущие значения: REF/INT/COOL со штрафами от ран (уровень из счётчика здоровья), EMP = человечность/10 (человечность = EMP×10 − потеря). Производные (`Cp2020Rules`): бег MA×3 м, прыжок бег/4, переноска BODY×10 кг, подъём BODY×40 кг, BTM, спасбросок = BODY.
- `Cp2020ArmorStrategy` (`"CP2020_ARMOR"`): `sp_<ЗОНА>` — текущий SP, `max_<ЗОНА>` — полный; `wear()` при пробитии, действие `REPAIR` возвращает полный SP.
- `Cp2020HealthStrategy` (`"CP2020_HEALTH"`, требует stats + armor): value = полученный урон 0..40; attributes: `lost_<ЗОНА>`, флаги `stunned/stabilized/dead/deathSavePending`. Порядок урона: зона → −SP (износ −1 при пробитии; AP — половина SP и урона; BYPASS — без брони) → ×2 в голову → −BTM (мин. 1) → >8 в голову = смерть, >8 в конечность = потеря + спасбросок от смерти → >40 = смерть → авто-спасбросок от оглушения. Таблицы — `Cp2020Rules`.
- Карточки — `counters/types/cp2020-*.html`, стили — блок `.cp-*` в конце `app.css` (Russo One только у главного числа: в нём нет символов − ≤ →).
- `HealthCounterService` — операции, не знающие о системе; `healOverTime` вызывается из `CampaignTimelineService.proceedDate` (1 очко/день, умирающие без стабилизации не лечатся).

### Время кампании

`CampaignTimelineService.proceedDate(campaignId, days, description)` создаёт `Chronicle` [старая дата → новая], сдвигает `currentDate` и вызывает `GameSystem.onTimePassed` (CP2020 — естественное лечение). Место под периодические траты/лечение ран оставлено комментарием.

## HTTP-маршруты

| Метод | Путь | Что делает |
|---|---|---|
| GET | `/` | список персонажей и кампаний |
| POST | `/campaigns` | `name`, `system`, `startDate?` → новая кампания |
| GET | `/campaign/{id}` | страница кампании |
| POST | `/campaign/{id}/characters` | добавить персонажа (инвентарь «Backpack» + счётчики по умолчанию системы) |
| POST | `/campaign/{id}/chronicles` | `name`, `duration` → промотать время |
| GET | `/api/campaigns/{id}/download` | экспорт кампании в JSON-файл |
| GET | `/character/{id}` | лист персонажа (раны, инвентари, деньги) |
| POST | `/character/{id}/wounds`, `.../wounds/{wid}/delete` | раны |
| POST | `/character/{id}/inventories`, `.../{invId}/edit`, `.../{invId}/delete` | инвентари |
| POST | `/character/{id}/inventories/{invId}/items` (+ `/{itemId}/edit|delete|move`) | предметы |
| POST | `/character/{id}/money`, `.../money/{mid}/delete` | деньги |
| GET | `/character/{id}/notes/` | заметки (фильтр `?categoryId=`) |
| POST | `/character/{id}/notes`, `.../{noteId}/edit|delete`; GET `.../{noteId}/edit` | заметки |
| POST | `/character/{id}/categories`, `.../{catId}/edit|delete` | категории заметок |
| GET | `/character/{id}/counters` | счётчики |
| POST | `/character/{id}/counters/create`, `/{cid}/update|set|reset`; DELETE `/{cid}` | счётчики |
| POST | `/character/{id}/counters/{cid}/attributes`, `.../{cid}/action/{action}` | правка attributes и особые действия типа (ремонт брони) |
| POST | `/character/{id}/counters/{cid}/health/damage|heal|stabilize`, `.../health/save/{STUN|DEATH}` | счётчик здоровья (итог + пошаговый расчёт во flash `healthSteps`) |

## Соглашения в коде

- Сервисы: `@Service @RequiredArgsConstructor`, `@Transactional` на запись, `@Transactional(readOnly = true)` на чтение, JavaDoc на русском.
- «Не найдено» → `NotFoundException` (404), сообщение `"Item not found for id=%d"`; `IllegalArgumentException` — только некорректный ввод (400).
- Контроллеры (кроме счётчиков) принимают `@RequestParam` и собирают сущность вручную, после POST — `redirect:/...`.
- Контроллер счётчиков — образец «нового» стиля: DTO + `@Valid` + flash-сообщения `successMessage`/`errorMessage`.
- Тесты разбиты на `@Nested` классы по операциям.

## Известные проблемы и долг (кандидаты на модернизацию)

Исправлено 2026-10-29: edit-эндпоинты ран и денег, кнопка ✎ у предметов (открывала форму инвентаря), `GET /notes` без слеша (был 405), подсветка выбранной категории, рекурсия в JSON-экспорте при наличии счётчиков, битые сид-данные. Единый редизайн всех страниц; добавлены UI для перемещения предмета, переименования категории и удаления счётчика (включён `spring.mvc.hiddenmethod.filter`).

**Открытые вопросы (не баги в текущем UI, но стоит решить)**
- `NoteCategory` глобальны для всех персонажей, хотя маршруты вложены в `/character/{id}`.
- Тесты контроллеров — standalone MockMvc без ViewResolver: если имя вида совпадает с последним сегментом URL (`notes` для `.../notes`), получится «Circular view path» — нужен `setViewResolvers(...)` (см. `NoteControllerTest`).

**Архитектура**
- Сущности отдаются прямо в шаблоны и в JSON; DTO есть только у счётчиков.
- Сервисы ходят в чужие репозитории напрямую (Money/Wound/Inventory → `CharacterRepository`).
- Нет проверки принадлежности ресурса персонажу (кроме счётчиков и удаления ран): можно удалить/переместить чужой предмет, подставив id.
- Стратегии счётчиков работают через `String`/`Object`, тип — строка; стратегии лежат в `model/` и знают имена шаблонов.
- Нет авторизации/пользователей.

## Планы автора (`TODO.txt`)

1. Система счётчиков/статов:
   - **ХП** — min/max, восстановление по событиям, дебаффы по порогам; для CP2020 — уровни смертельности.
   - **Опыт** — без максимума, массовое начисление всем игрокам, подсветка при достижении уровня.
   - **Отложенный счётчик** — события копятся и применяются позже.
   - **Мана / ячейки заклинаний** (D&D), **вдохновение**, **броня** (CP2020 SP/абляция).
2. Периодические траты (привязать к `proceedDate`).
3. Броски кубиков с сохранением истории.
4. Возможно — чат кампании.

Сделано из TODO: заметки, хроники, раны как отдельная сущность, смена даты, окно трат, базовые счётчики.
