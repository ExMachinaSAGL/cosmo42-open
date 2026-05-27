# Contributing to Cosmo42

Thanks for your interest in Cosmo42. This document describes how to build the project, run the tests, and submit
changes.

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating you agree to uphold it.

## Licensing of contributions

Cosmo42 is licensed under the [MIT License](LICENSE). By submitting a pull request you agree that your contribution is
licensed under the same terms. No separate CLA is required.

## Reporting bugs and requesting features

- **Bugs**: open a GitHub issue using the *Bug report* template. Include version, reproduction steps, expected vs actual
  behaviour, and logs.
- **Features**: open a GitHub issue using the *Feature request* template. Describe the use case before the proposed
  solution.
- **Security vulnerabilities**: do **not** open a public issue, see [SECURITY.md](SECURITY.md).

## Development environment

### Prerequisites

- JDK 25
- Maven (bundled wrapper `./mvnw`)
- Node.js 20+ and npm
- Docker 24+ and Docker Compose v2
- An OpenAI-compatible LLM and embedding endpoint (see [docs/MODELS.md](docs/MODELS.md))

### First-time setup

```bash
# Start infrastructure (MariaDB + LibreOffice)
cd docker && docker compose -f docker-compose-no-webapp.yml up -d
```

### Build and run

```bash
# Backend
cd backend
./mvnw clean package -DskipTests
./mvnw spring-boot:run

# Frontend (separate shell)
cd frontend
npm install
npm run dev
```

### Tests

```bash
# Backend full suite
cd backend && ./mvnw test

# Single test
./mvnw test -Dtest=ClassName#methodName

# Frontend
cd frontend && npm test
```

## Code style

- **Java**: Spotless is configured in `backend/pom.xml`. Run `./mvnw spotless:apply` before committing.
- **TypeScript / React**: ESLint config in `frontend/eslint.config.js`. Run `npm run lint`.
- Prefer small, focused commits. Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`) are
  appreciated but not required.

## Pull request process

1. Fork the repository and create a topic branch from `main` (`git checkout -b feat/short-description`).
2. Make your changes. Add or update tests for any new behaviour.
3. Make sure `./mvnw verify` and `npm run build` pass locally.
4. Open a pull request against `main` using the PR template. Link any related issue.
5. CI must be green before review. A maintainer will review, request changes if needed, and merge.

## Project layout

```
backend/      Spring Boot 4 application (Java 25, Maven)
frontend/     React 19 + Vite UI (TypeScript)
docker/       Docker Compose files and LibreOffice sidecar
docs/         Architecture, configuration, deployment, models
usage_scripts/  Convenience curl scripts for the REST API
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for a tour of the codebase.
