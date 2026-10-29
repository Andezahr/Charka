# Charka — менеджер персонажей для ролевых игр

Веб-приложение для ведущего/игроков настольных РПГ: кампании, персонажи, их раны, инвентари, деньги, заметки и счётчики, плюс игровой календарь кампании («время летит…» → хроника).
Изначально ориентировано на Cyberpunk 2020 (дата старта сида — 2037 г., уровни смертельности ран), в планах — поддержка D&D (ячейки заклинаний и т.п.).

Язык интерфейса и комментариев — русский. Сообщения исключений — смесь русского и английского.

## Стек

- Java 24 (в `pom.xml`; локально стоит JDK 25), Spring Boot 3.5.3
- Spring Web MVC + **Thymeleaf** (серверный рендеринг, формы с POST + redirect)
- Spring Data JPA / Hibernate, **H2 in-memory** (настроек datasource нет → данные живут только до перезапуска, схема `ddl-auto` по умолчанию)
- Lombok (`@Data`, `@RequiredArgsConstructor`), Bean Validation
- Тесты: JUnit 5, Spring Boot Test (MockMvc, Mockito)
- Сборка: Maven Wrapper. Профили `dev`/`prod` объявлены в pom, но отдельных `application-*.yml` нет.

## Команды

```bash
./mvnw spring-boot:run      # http://localhost:8080 (слушает 0.0.0.0)
./mvnw test                 # ~101 тест, на 2026-10-29 все зелёные
./mvnw spring-boot:build-image   # образ charka/app:latest
```

При старте `DataInitializer` (CommandLineRunner) создаёт демо-кампанию «Последний шанс» с персонажами «Колясик» и «Олежа».

## Структура (`src/main/java/app/charka`)

```
model/         JPA-сущности (используются напрямую и как модель для шаблонов, и для JSON-экспорта)
  counters/    Counter + strategy/ (стратегии изменения значения счётчика)
  notes/       Note, NoteCategory
repository/    Spring Data репозитории (по одному на сущность)
service/       бизнес-логика; campaign/, counter/, notes/ — подпакеты
controller/    MVC-контроллеры, возвращают имена шаблонов или redirect
api/           REST: CampaignExportController (единственный JSON-эндпоинт)
dto/counter/   DTO только для счётчиков (единственный модуль, переведённый на DTO)
exception/     CharacterNotFoundException, InventoryNotFoundException (наследуют NoSuchElementException, фабрика forId())
TODO.txt       список планов автора (см. ниже)
```

Шаблоны: `src/main/resources/templates/` — `home.html`, `campaign.html`, `character.html`, `notes.html`, `counters/list.html`; общие куски — `fragments/layout.html` (`head(title)`, `topbar(characterId, active)` с вкладками Лист/Счётчики/Заметки).
Фронт единый: один CSS `static/css/app.css` (тёмная тема, токены в `:root`, шрифты Inter + Russo One с Google Fonts) и один JS `static/js/app.js` — без Bootstrap. JS-соглашения: `[data-edit-toggle]` открывает `.edit-panel` внутри ближайшего `[data-editable]`; `form[data-confirm]` спрашивает подтверждение; `[data-count-to]` — счётчик символов; `.alert-close` закрывает flash-сообщение.

## Доменная модель

```
Campaign (name, startDate, currentDate)
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

`CounterStrategy` (тип, `getNewValue(String current, Object input)`, `getResetValue()`) → `CounterStrategyFactory` собирает все бины-стратегии в Map по `getCounterType()`. Сейчас есть только `SimpleIntegerCounterStrategy` (`"SIMPLE_INTEGER"`, прибавляет input). Значения гоняются через `String`, хотя хранятся как `Integer`. Тип счётчика — строка без enum.

### Время кампании

`CampaignTimelineService.proceedDate(campaignId, days, description)` создаёт `Chronicle` [старая дата → новая] и сдвигает `currentDate`. Место под периодические траты/лечение ран оставлено комментарием.

## HTTP-маршруты

| Метод | Путь | Что делает |
|---|---|---|
| GET | `/` | список персонажей и кампаний |
| GET | `/campaign/{id}` | страница кампании |
| POST | `/campaign/{id}/characters` | добавить персонажа (создаёт инвентарь «Backpack») |
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

## Соглашения в коде

- Сервисы: `@Service @RequiredArgsConstructor`, `@Transactional` на запись, `@Transactional(readOnly = true)` на чтение, JavaDoc на русском.
- «Не найдено» → в основном `IllegalArgumentException` с форматной константой (`ITEM_NOT_FOUND = "Item not found for id=%d"`), в новом коде — свои исключения с `forId(id)`. В заметках — голый `RuntimeException`.
- Контроллеры (кроме счётчиков) принимают `@RequestParam` и собирают сущность вручную, после POST — `redirect:/...`.
- Контроллер счётчиков — образец «нового» стиля: DTO + `@Valid` + flash-сообщения `successMessage`/`errorMessage`.
- Тесты разбиты на `@Nested` классы по операциям.

## Известные проблемы и долг (кандидаты на модернизацию)

Исправлено 2026-10-29: edit-эндпоинты ран и денег, кнопка ✎ у предметов (открывала форму инвентаря), `GET /notes` без слеша (был 405), подсветка выбранной категории, рекурсия в JSON-экспорте при наличии счётчиков, битые сид-данные. Единый редизайн всех страниц; добавлены UI для перемещения предмета, переименования категории и удаления счётчика (включён `spring.mvc.hiddenmethod.filter`).

**Открытые вопросы (не баги в текущем UI, но стоит решить)**
- `MoneyController` ставит `operationDate = LocalDate.now()` (реальная дата), а не игровую `campaign.currentDate`.
- `NoteCategory` глобальны для всех персонажей, хотя маршруты вложены в `/character/{id}`.
- `GlobalExceptionHandler` лежит в `src/test`, т.е. в рантайме глобальной обработки ошибок нет.
- Тесты контроллеров — standalone MockMvc без ViewResolver: если имя вида совпадает с последним сегментом URL (`notes` для `.../notes`), получится «Circular view path» — нужен `setViewResolvers(...)` (см. `NoteControllerTest`).

**Архитектура**
- Сущности отдаются прямо в шаблоны и в JSON; DTO есть только у счётчиков.
- Сервисы ходят в чужие репозитории напрямую (Money/Wound/Inventory → `CharacterRepository`), разный стиль «не найдено».
- Нет проверки принадлежности ресурса персонажу (кроме счётчиков и удаления ран): можно удалить/переместить чужой предмет, подставив id.
- Стратегии счётчиков работают через `String`/`Object`, тип — строка.
- Нет постоянного хранилища (H2 in-memory), нет миграций (Flyway/Liquibase), нет авторизации/пользователей.

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
