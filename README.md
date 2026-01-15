# Auto Repair Shop

Sistema de gerenciamento para oficina mecânica desenvolvido em Kotlin com arquitetura inspirada em hexagonal/ports & adapters multi-módulo.

### Estrutura de Pastas
```
auto-repair-shop/
├── main/                  # Aplicação principal
├── domain/                # Lógica de negócio
├── api/                   # REST API
├── storage/               # Persistência
├── worker/                # Background jobs
├── jwt/                   # Autenticação
├── email/                 # Envio de emails
├── Dockerfile             # Multi-stage build (Gradle + JRE)
├── docker-compose.yaml    # Orquestração (app + PostgreSQL)
└── build.gradle.kts       # Build principal
```
---
### Stack

- **Linguagem**: Kotlin 2.2.10
- **JVM**: Java 21
- **Build**: Gradle (Kotlin DSL)
- **Web Framework**: Ktor 3.3.2
- **Dependency Injection**: Koin 4.1.1
- **Database**: PostgreSQL 42.7.8
- **ORM**: Exposed 0.61.0
- **Migrations**: Flyway 11.17.0
- **Testing**: JUnit 5, MockK, TestContainers
- **Quality**: JaCoCo, SonarQube
---
### Testes

O projeto possui dois tipos de testes:

```bash
# Testes unitários
./gradlew test

# Testes de integração
./gradlew integrationTest
```

**Nota**: Os testes de integração requerem Docker para executar containers do PostgreSQL via TestContainers.

---

### Cobertura de Código

#### Gerar Relatório de Cobertura

O projeto usa JaCoCo para medir a cobertura de código, incluindo testes unitários e de integração.

```bash
./gradlew jacocoAggregatedReport
```

Para visualizar, abra `build/reports/jacoco/jacocoAggregatedReport/html/index.html` no navegador

---

### Executar o Projeto

#### Opção 1: Com Docker (Recomendado)

#### Pré-requisitos
- Docker
- Docker Compose

#### Iniciar os serviços
```bash
# Build e start
docker-compose up --build -d
```

#### Acessar banco de dados
```
Host: localhost
Port: 5432
Database: postgres
Username: postgres
Password: test
```

#### Opção 2: Execução Local (sem Docker)

#### Pré-requisitos
- Java 21
- PostgreSQL rodando localmente

#### Build e executar
```bash
# Build
./gradlew build

# Executar
./gradlew :main:run
```
---
### Arquitetura Docker

**Dockerfile**:
- **Stage 1 (Build)**: `gradle:8.14-jdk21` - Build do fat JAR
- **Stage 2 (Run)**: `eclipse-temurin:21-jre` - Execução leve

**docker-compose.yaml**:
- **db**: PostgreSQL 18.1 com volume persistente
- **app**: Aplicação Kotlin/Ktor