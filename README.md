# Charka

**Charka** is a Java-based character management application for narrative role-playing games (RPGs). Built with Spring Boot, it provides a RESTful API and a web UI to create, read, update, and delete (CRUD) characters and their attributes, inventory, and background details.

## Ключевые возможности

- Управление персонажами: создание, редактирование, удаление, просмотр
- Учёт инвентаря: добавление и удаление предметов персонажа
- Хранение заметок
- Кастомные счетчики
- Система отслеживания внутриигровой даты кампании и летописи
- Веб-интерфейс на Spring MVC + Thymeleaf для удобного взаимодействия


## Технологии

```
- Java 24
- Spring Boot 3.x
    - Spring Web
    - Spring Data JPA
    - Spring MVC + Thymeleaf
- База данных H2 (в памяти)
- Maven для сборки и управления зависимостями
```

## Быстрый старт

1. Клонировать репозиторий

```bash
git clone https://github.com/Andezahr/Charka.git
cd Charka
```

2. Собрать и запустить приложение

```bash
./mvnw spring-boot:run
```

3. Перейти в браузере по адресу
http://localhost:8080

## Структура проекта

```
└── src
   ├── main
   │  ├── java/com/andezahr/charka
   │    ├── api/ — REST контроллеры (пока что он один)
   │    ├── controller/ — MVC контроллеры
   │    ├── model/ — сущности JPA
   │    └── repository/ — репозитории Spring Data JPA
   │    └── service/ — бизнес-логика
   └── resources
      ├── templates/ — Thymeleaf-шаблоны
      └── application.yml
```

## Веб-интерфейс

- Главная страница: список персонажей
- Форма создания/редактирования с динамическим управлением атрибутами и инвентарём
- Просмотр деталей и удаление через кнопки управления


## Тестирование

```bash
./mvnw test
```

В проекте есть небольшое количество web-slice тестов контроллеров и unit-тестов сервисов 


---
**Charka** открывает простые и расширяемые возможности управления персонажами для любых настольных RPG и интерактивных историй.
