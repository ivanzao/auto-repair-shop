# Auto Repair Shop

Sistema de gerenciamento para oficina mecânica desenvolvido em Kotlin com arquitetura multi-módulo.

## 📦 Módulos

O projeto é dividido em 7 módulos:

- **main** - Aplicação principal e configuração de dependências
- **domain** - Lógica de negócio e casos de uso
- **api** - Endpoints REST (Ktor)
- **storage** - Persistência de dados (PostgreSQL/Exposed)
- **worker** - Processamento de tarefas em background
- **jwt** - Autenticação e gerenciamento de tokens
- **email** - Serviço de envio de emails

## 🛠️ Stack Tecnológica

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

## 🧪 Testes

O projeto possui dois tipos de testes:

### Testes Unitários
```bash
./gradlew test
```

### Testes de Integração
```bash
./gradlew integrationTest
```
**Nota**: Os testes de integração requerem Docker para executar containers do PostgreSQL via TestContainers.

### Executar Todos os Testes
```bash
./gradlew test integrationTest
```

## 📊 Cobertura de Código e SonarQube

### Gerar Relatório de Cobertura

O projeto usa JaCoCo para medir a cobertura de código, incluindo **testes unitários E de integração**.

#### Relatório Agregado (todos os módulos)
```bash
./gradlew jacocoAggregatedReport
```

**Visualizar**: Abra `build/reports/jacoco/jacocoAggregatedReport/html/index.html` no navegador

#### Relatórios Individuais por Módulo
```bash
# Exemplo: módulo main
./gradlew :main:jacocoMergedReport
```

**Visualizar**: `main/build/reports/jacoco/jacocoMergedReport/html/index.html`

### Análise com SonarQube

#### Pré-requisitos
1. SonarQube Server rodando (local ou remoto)
2. Token de autenticação do SonarQube

#### Configurar Variáveis de Ambiente

**Windows (PowerShell)**:
```powershell
$env:SONAR_HOST_URL="http://localhost:9000"
$env:SONAR_TOKEN="seu_token_aqui"
```

**Linux/Mac**:
```bash
export SONAR_HOST_URL=http://localhost:9000
export SONAR_TOKEN=seu_token_aqui
```

#### Executar Análise
```bash
./gradlew sonar
```

A task `sonar` automaticamente:
1. Executa todos os testes (unitários + integração)
2. Gera o relatório de cobertura agregado
3. Envia os dados para o SonarQube

#### Visualizar Resultados

Acesse o SonarQube em `http://localhost:9000` (ou seu servidor configurado) e busque pelo projeto **"auto-repair-shop"**.

### Cobertura Atual

- **Cobertura Total**: 77%
- **Branches**: 45%
- **Linhas**: 86%
- **Métodos**: 91%
- **Classes**: 92%

#### Áreas Críticas sem Cobertura

🔴 **Urgente**:
- `br.com.soat.email` - 0% (serviço de envio de emails)
- `br.com.soat.security` - 45% (hash de senhas, 79% dos branches sem cobertura)

🟡 **Importante**:
- `br.com.soat.config` - 66%
- `br.com.soat.supply.model.event` - 47%
- `br.com.soat.shared.util` - 0%

## 🚀 Executar o Projeto

```bash
# Build
./gradlew build

# Executar
./gradlew :main:run
```

## 🔧 Desenvolvimento

### Estrutura de Pastas
```
auto-repair-shop/
├── main/           # Aplicação principal
├── domain/         # Lógica de negócio
├── api/            # REST API
├── storage/        # Persistência
├── worker/         # Background jobs
├── jwt/            # Autenticação
├── email/          # Envio de emails
└── build.gradle.kts
```

### Gradle Tasks Úteis

```bash
# Listar todas as tasks
./gradlew tasks

# Verificação completa (build + testes + cobertura)
./gradlew clean build jacocoAggregatedReport

# Análise completa com SonarQube
./gradlew clean test integrationTest sonar
```