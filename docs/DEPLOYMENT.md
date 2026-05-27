# Deployment

This guide covers running Cosmo42 in production with Docker Compose. For a development setup, see the [README](../README.md).

## Hardware requirements

Cosmo42 itself is lightweight (Spring Boot + nginx + MariaDB). The dominant cost is the **LLM and embedding inference server**, which you run separately.

### Application stack (without inference)

| Component   | CPU  | RAM | Disk | Notes |
|-------------|------|-----|------|-------|
| Backend     | 2 vCPU | 2 GB | 5 GB | JVM, default heap. |
| Frontend    | 0.5 vCPU | 128 MB | 100 MB | Static files behind nginx. |
| MariaDB     | 1 vCPU | 1 GB | depends on corpus | ~10–50 KB per chunk including embedding. |
| LibreOffice | 1 vCPU | 1 GB | 1 GB | Single-threaded. |

### Inference server (separate host recommended)

| Model preset | GPU VRAM | Concurrency | Notes |
|--------------|----------|-------------|-------|
| Low-VRAM (`gemma-4-E2B-UD-Q4_K_XL`) | 4 GB | 1 user | Tested for development and demos. |
| Default (`gemma-4-26B` AWQ 4-bit) | ~24 GB | small team | Tested in production. |
| Embeddings (`BAAI/bge-m3`) | ~2 GB | many | Run alongside the LLM or on CPU. |

See [MODELS.md](MODELS.md) for serving instructions.

## Production compose

The compose file at `docker/docker-compose.yml` defines the full stack: `mariadb`, `libreoffice`, `backend`, `frontend`.

### 1. Build the LibreOffice sidecar image (first time only)

```bash
docker/libreoffice/build.sh
```

### 2. Configure environment

Create an `.env` file next to `docker/docker-compose.yml`:

```bash
COSMO42_DB_PASSWORD=<strong-password>

COSMO42_LLM_BASE_URL=http://llm-host:8000/v1
COSMO42_LLM_API_KEY=<optional>
COSMO42_LLM_MODEL=gemma-4-26B

COSMO42_EMBEDDING_BASE_URL=http://embedding-host:8001
COSMO42_EMBEDDING_MODEL=BAAI/bge-m3
```

### 3. Launch

```bash
cd docker
docker compose up -d --build
```

Services exposed on the host:

| Port  | Service       |
|-------|---------------|
| 8081  | Frontend (nginx, proxies `/api` to the backend) |
| 8080  | Backend (Spring Boot, REST + SSE) |
| 33006 | MariaDB (consider removing this port mapping in production) |

The LibreOffice sidecar is reachable only on the internal Docker network.

### 4. Verify

```bash
curl http://localhost:8080/actuator/health   # if actuator is enabled
curl http://localhost:8080/swagger-ui.html
```

Then open <http://localhost:8081> for the UI.

## Persistent data

Two named volumes are created:

| Volume                 | Mount in container        | Contents |
|------------------------|---------------------------|----------|
| `cosmo42-mariadb-data` | `/var/lib/mysql`          | Database files. |
| `cosmo42-storage-data` | `/var/lib/cosmo42`        | Uploaded originals + rendered page images. |

Back both up. The MariaDB volume is the source of truth (documents can be re-ingested from originals, but conversations cannot be reconstructed).

## Putting it behind a reverse proxy

The frontend container speaks plain HTTP on port 80. Terminate TLS in front of it (Traefik, Caddy, nginx, an ingress controller, ...). Two things to watch:

- **SSE**: disable response buffering for `/api/v1/chat/stream`. The bundled nginx already does this for the frontend container; replicate the same in your outer proxy.
- **Upload size**: align `client_max_body_size` (nginx) or equivalent with `spring.servlet.multipart.max-file-size`.

## Scaling notes

- **Backend** is stateless,  it can be replicated horizontally as long as all replicas share the storage volume and the database.
- **MariaDB** is a single instance. For HA, use Galera or a managed MariaDB service. Cosmo42 only requires vector support (11.8+).
- **LibreOffice** is single-threaded by design. Scale by running multiple sidecar containers and load-balancing them; the backend currently treats `cosmo42.libreoffice.base-url` as a single endpoint, so put the load balancer between.
- **Chunking concurrency** (`cosmo42.chunking.pool.size`) directly maps to concurrent LLM calls per upload,  increase only if the inference server can take it.

## Upgrading

1. Pull the new release.
2. `docker compose pull` (or rebuild).
3. `docker compose up -d`. Flyway migrations run automatically on backend startup.
4. Check backend logs for `Successfully applied N migrations` before sending traffic.

A re-embedding job is required only if the embedding model dimension changes (currently fixed at 1024 by `VECTOR(1024)` in `V2__kb_document_chunk.sql`).
