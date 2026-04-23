# Charka

**Charka** is a Java-based character management application for narrative role-playing games (RPGs). Built with Spring Boot, it provides a RESTful API and a web UI to manage characters and their attributes, inventory, and background details.

## Key Features

- Character management with inventory tracking: characters can have multiple stashes where different items are stored
- Notes with editing capabilities and category filtering
- Custom counters
- System for tracking in-game campaign dates and chronicles
- Web interface built with Spring MVC + Thymeleaf for convenient interaction


## Technologies

```
- Java 24
- Spring Boot 3.x
    - Spring Web
    - Spring Data JPA
    - Spring MVC + Thymeleaf
- H2 Database (in-memory)
- Maven for build management and dependency management
```


## Quick Start

1. Clone the repository
```bash
git clone https://github.com/Andezahr/Charka.git
cd Charka
```

2. Build and run the application
```bash
./mvnw spring-boot:run
```

3. Navigate to http://localhost:8080 in your browser

## Project Structure

```
└── src
   ├── main
   │  ├── java/com/andezahr/charka
   │    ├── api/ — REST controllers (currently just one)
   │    ├── controller/ — MVC controllers
   │    ├── model/ — JPA entities
   │    └── repository/ — Spring Data JPA repositories
   │    └── service/ — business logic
   └── resources
      ├── templates/ — Thymeleaf templates
      └── application.yml
```


## Web Interface

- Home page: list of characters and campaigns
- Campaign page: characters in the campaign and its chronicle
- Character page: money and wounds, inventories and items
- Character counter and notes pages
- Campaign page features a JSON download button for campaign information (so you can escape from the application!)


## Testing

```bash
./mvnw test
```

The project includes a small number of web-slice controller tests and unit tests for services.

## TODO

In the future, I plan to rewrite all service and controller interactions using DTOs, then write comprehensive service tests. Add more counter implementations, specifically need a health counter from Cyberpunk 2020. Need custom dice linked to items. Implement character attributes and skills functionality, connect it with dice. Create a unified frontend style.

---
**Charka** provides simple and extensible character management capabilities for any tabletop RPGs and interactive stories.
