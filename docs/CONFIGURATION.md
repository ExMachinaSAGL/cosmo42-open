# Configuration

All configuration lives in `backend/src/main/resources/application.properties`. Spring Boot maps each property to an
environment variable using its standard rules (e.g. `cosmo42.model.llm.name` → `COSMO42_MODEL_LLM_NAME`), so anything
below can be overridden at runtime without rebuilding.

## Inference (LLM + embeddings)

| Property                                     | Env variable                   | Default                 | Description                                                                                              |
|----------------------------------------------|--------------------------------|-------------------------|----------------------------------------------------------------------------------------------------------|
| `spring.ai.openai.base-url`                  | `COSMO42_LLM_BASE_URL`         | http://localhost:8000   | OpenAI-compatible chat-completions endpoint. Must include `/v1`. **Required.**                           |
| `spring.ai.openai.api-key`                   | `COSMO42_LLM_API_KEY`          | ON-PREMISE-LLM-FAKE-KEY | API key sent as `Authorization: Bearer`. On-premise servers usually ignore it. **Required.**             |
| `spring.ai.openai.timeout`                   | `SPRING_AI_OPENAI_TIMEOUT`     | `PT10M`                 | HTTP timeout for chat calls.                                                                             |
| `spring.ai.openai.embedding.base-url`        | `COSMO42_EMBEDDING_BASE_URL`   | http://localhost:8001   | OpenAI-compatible embeddings endpoint. **Required.**                                                     |
| `spring.ai.openai.embedding.embeddings-path` | `COSMO42_EMBEDDING_PATH`       | /embeddings             | Path appended to `embedding.base-url`. **Required.**                                                     |
| `cosmo42.model.llm.name`                     | `COSMO42_MODEL_LLM_NAME`       | `gemma-4-26B`           | Model identifier sent to the chat endpoint. Must match what the server exposes.                          |
| `cosmo42.model.embedding.name`               | `COSMO42_MODEL_EMBEDDING_NAME` | `BAAI/bge-m3`           | Model identifier sent to the embeddings endpoint. Must produce 1024-dim vectors (or migrate the schema). |

See [MODELS.md](MODELS.md) for tested combinations.

## Database (MariaDB)

| Property                                  | Default                                  | Description                                          |
|-------------------------------------------|------------------------------------------|------------------------------------------------------|
| `spring.datasource.url`                   | `jdbc:mariadb://localhost:33006/cosmo42` | JDBC URL. MariaDB 11.8+ required (vector support).   |
| `spring.datasource.username`              | `cosmo42`                                | DB user.                                             |
| `spring.datasource.password`              | `changeme`                               | DB password. **Change in production.**               |
| `spring.datasource.hikari.max-lifetime`   | `600000`                                 | HikariCP max connection lifetime (ms).               |
| `spring.datasource.hikari.keepalive-time` | `120000`                                 | HikariCP keepalive (ms).                             |
| `spring.flyway.enabled`                   | `true`                                   | Run Flyway migrations on startup.                    |
| `spring.flyway.baseline-on-migrate`       | `true`                                   | Allow Flyway to baseline an existing schema.         |
| `spring.jpa.hibernate.ddl-auto`           | `validate`                               | Schema is owned by Flyway; Hibernate only validates. |

## Document ingestion

| Property                                          | Default  | Description                                                                               |
|---------------------------------------------------|----------|-------------------------------------------------------------------------------------------|
| `cosmo42.chunking.pool.size`                      | `1`      | Parallel page-chunking threads. Increase carefully,  each thread holds an LLM connection. |
| `cosmo42.chunking.max-tokens`                     | `24000`  | Max tokens the LLM may use per page response.                                             |
| `cosmo42.ingestion.recovery.interval-ms`          | `300000` | How often the recovery loop re-checks stuck pages.                                        |
| `cosmo42.ingestion.max-page-attempts`             | `3`      | Retry budget per page before marking it failed.                                           |
| `cosmo42.ingestion.page-chunking-timeout-seconds` | `600`    | Per-page wall-clock timeout.                                                              |
| `cosmo42.ingestion.executor.core-pool-size`       | `2`      | Background ingestion executor minimum threads.                                            |
| `cosmo42.ingestion.executor.max-pool-size`        | `4`      | Maximum threads.                                                                          |
| `cosmo42.ingestion.executor.queue-capacity`       | `50`     | Bounded queue size before rejection.                                                      |
| `spring.servlet.multipart.max-file-size`          | `10MB`   | Upload size cap. Raise for large PDFs.                                                    |

## Chat

| Property                           | Env variable                       | Default | Description                                                                                                                                     |
|------------------------------------|------------------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `cosmo42.chat.memory.max-messages` | `COSMO42_CHAT_MEMORY_MAX_MESSAGES` | `25`    | Maximum number of messages kept in the sliding window for each conversation. Older messages are discarded. Keep low on constrained VRAM (4 GB). |

## File storage

| Property                            | Default        | Description                                                                                                  |
|-------------------------------------|----------------|--------------------------------------------------------------------------------------------------------------|
| `cosmo42.fs.storage.path`           | `/tmp/cosmo42` | Local directory for original uploaded files and rendered page PNGs. Mount a persistent volume in production. |
| `cosmo42.fs.service.implementation` | `local`        | Storage backend. Currently only `local` is implemented.                                                      |

## LibreOffice sidecar

| Property                       | Default                 | Description                                                                            |
|--------------------------------|-------------------------|----------------------------------------------------------------------------------------|
| `cosmo42.libreoffice.base-url` | `http://localhost:3000` | URL of the LibreOffice converter. In Docker Compose this is `http://libreoffice:3000`. |

## Web server

| Property                               | Default            | Description             |
|----------------------------------------|--------------------|-------------------------|
| `server.compression.enabled`           | `true`             | Enable gzip.            |
| `server.compression.mime-types`        | `application/json` | Mime types to compress. |
| `server.compression.min-response-size` | `10KB`             | Compression threshold.  |

## Observability

| Property                    | Default            | Description                         |
|-----------------------------|--------------------|-------------------------------------|
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI URL.                     |
| `springdoc.api-docs.path`   | `/api-docs`        | OpenAPI JSON URL.                   |
| `logging.level.*`           | varies             | Standard Spring Boot logging knobs. |

## Environment variable overrides (cheat sheet)

The Docker Compose file in `docker/docker-compose.yml` already wires the most common ones:

```bash
COSMO42_DB_PASSWORD=...
COSMO42_LLM_BASE_URL=http://llm-host:8000/v1
COSMO42_LLM_API_KEY=...
COSMO42_LLM_MODEL=gemma-4-26B
COSMO42_EMBEDDING_BASE_URL=http://embedding-host:8001
COSMO42_EMBEDDING_MODEL=BAAI/bge-m3
COSMO42_EMBEDDING_PATH=/embeddings
COSMO42_CHAT_MEMORY_MAX_MESSAGES=25
```

For anything not pre-mapped, use Spring Boot's relaxed binding (uppercase, dots → underscores):

```bash
SPRING_AI_OPENAI_TIMEOUT=PT15M
COSMO42_CHUNKING_POOL_SIZE=4
COSMO42_INGESTION_EXECUTOR_MAX_POOL_SIZE=8
```
