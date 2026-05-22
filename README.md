<p align="center">
  <img src="frontend/src/assets/Cosmo42logo_128x128.jpg" alt="Cosmo42 logo" width="128" height="128">
</p>

<h1 align="center">Cosmo42</h1>

<p align="center">
  On-premise Retrieval-Augmented Generation (RAG) platform.<br>
  Spring Boot 4 · Spring AI 2 · Java 25 · MariaDB Vector · React 19.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/java-25-orange.svg" alt="Java 25">
  <img src="https://img.shields.io/badge/spring--boot-4.0-brightgreen.svg" alt="Spring Boot 4">
  <img src="https://img.shields.io/badge/spring--ai-2.0-brightgreen.svg" alt="Spring AI 2">
</p>

---

## What is Cosmo42?

Cosmo42 is a self-hosted RAG backend with a built-in chat UI. You upload documents (PDF, DOCX, XLSX), Cosmo42 normalizes them to page images, sends each page to a vision-LLM to produce typed chunks (text / table / image), embeds them, and stores both content and vectors in MariaDB. A streaming chat endpoint then answers user questions using cosine-similarity retrieval over the indexed chunks.

Everything runs on-premise against any OpenAI-compatible inference server (vLLM, llama.cpp server, Ollama, LM Studio, ...). No data leaves your network.

### Key features

- **Vision-based ingestion**,  pages rendered to images and parsed by a vision LLM, preserving tables and figure context.
- **Streaming chat**,  SSE endpoint with conversational memory (JDBC-backed, sliding window configurable via `COSMO42_CHAT_MEMORY_MAX_MESSAGES`, default 25).
- **Vector search in MariaDB**,  `VECTOR(1024)` column type, cosine similarity, no extra vector DB to operate.
- **Pluggable models**,  point `spring.ai.openai.base-url` at any OpenAI-compatible endpoint.
- **Web UI included**,  React 19 + Vite frontend with chat and knowledge-base management.

---

## Quick start (Docker, full stack)

Requirements: Docker 24+, Docker Compose v2, and an OpenAI-compatible LLM endpoint reachable from the host (see [docs/MODELS.md](docs/MODELS.md)).

```bash
git clone https://github.com/ExMachinaSAGL/cosmo42.git
cd cosmo42

# Configure LLM endpoint
export COSMO42_LLM_BASE_URL=http://<your-llm-host>:8000/v1
export COSMO42_EMBEDDING_BASE_URL=http://<your-embedding-host>:8001
export COSMO42_LLM_MODEL=gemma-4-26B
export COSMO42_EMBEDDING_MODEL=BAAI/bge-m3

# Launch full stack: mariadb + libreoffice + backend + frontend
docker compose -f docker/docker-compose.yml up -d
```

Then open:

- Web UI: <http://localhost:8081>
- REST API: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

---

## Quick start (local dev)

```bash
# Infrastructure only (MariaDB + LibreOffice)
cd docker && docker compose -f docker-compose-no-webapp.yml up -d

# Backend
cd backend && ./mvnw spring-boot:run

# Frontend (in another shell)
cd frontend && npm install && npm run dev
```

Backend: <http://localhost:8080> · Frontend dev: <http://localhost:5173> (Vite proxies `/api` → `:8080`).

---

## Architecture (TLDR version)

`KBDocumentService` accepts an upload, `FileConverter` calls the LibreOffice sidecar to normalize non-PDF to PDF and renders each page to a 300dpi grayscale PNG, `KBDocumentChunker` sends each page to the vision LLM in parallel and stores the returned `Chunk`s (text / table / image) with their embeddings in `kb_document_chunk`. On chat, `POST /api/v1/chat/stream` returns an SSE flux: `UuidProcessor` emits the conversation UUID, `ConversationProcessor` calls the LLM with `KBDocumentSimilaritySearchTool` available as a tool, the tool runs cosine similarity in MariaDB (threshold 0.5, top 10) and the LLM streams tokens back as `CHUNK` events.

Full pipeline and component map: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Documentation

| Doc | What's in it |
|-----|--------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Ingestion pipeline, chat pipeline, SSE event types, component diagram |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | Every `cosmo42.*` and `spring.ai.*` property |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Production compose, HW requirements, scaling notes |
| [docs/MODELS.md](docs/MODELS.md) | Tested LLM / embedding models, how to serve them with vLLM / llama.cpp / Ollama |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Build, test, code style, PR process |
| [SECURITY.md](SECURITY.md) | How to report vulnerabilities |

---

## Tech stack

| Layer | Tech |
|-------|------|
| Backend | Spring Boot 4.0, Spring AI 2.0, Java 25, Maven |
| Persistence | MariaDB 11.8 (`VECTOR(1024)`), Flyway, Hibernate |
| Document conversion | LibreOffice headless (sidecar), Apache PDFBox |
| Frontend | React 19, TypeScript, Vite, React Router 7 |
| Inference | OpenAI-compatible HTTP API (vLLM, llama.cpp, Ollama, ...) |

---

## License

Cosmo42 is released under the [MIT License](LICENSE).

Copyright © 2026 [Ex Machina SAGL](https://exmachina.ch).
