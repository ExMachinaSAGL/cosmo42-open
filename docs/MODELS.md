# Models

Cosmo42 calls two external services over the OpenAI-compatible HTTP API:

- a **chat completions** endpoint backed by a vision-capable LLM
- an **embeddings** endpoint that returns 1024-dimensional vectors

Any server implementing those APIs will work. Below are the configurations that have been used while developing Cosmo42.

## Requirements at a glance

| Role      | Capability needed                                        |
|-----------|----------------------------------------------------------|
| LLM       | Chat completions, **vision** (image input), tool calling |
| Embedding | OpenAI `/embeddings` schema, output dimension 1024       |

> The vector column in the database is `VECTOR(1024)`. Changing the embedding model to one with a different dimension requires editing the Flyway migration `V2__kb_document_chunk.sql` and re-embedding every chunk.

## Tested LLM models

| Model | Quant | Approx VRAM | Throughput | Use case |
|-------|-------|-------------|------------|----------|
| `gemma-4-26B` (alias of `cyankiwi/gemma-4-26B-A4B-it-AWQ-4bit`) | AWQ 4-bit | ~24 GB | small team | **Default in `application.properties`.** Production-ready. |
| `gemma-4-E2B-UD-Q4_K_XL` | GGUF Q4_K_XL | ~4 GB | 1 concurrent user | Tested for development on a 4 GB GPU (see commit `3ecfea2`). |

Set the active model via `cosmo42.model.llm.name` (or `COSMO42_MODEL_LLM_NAME`).

## Tested embedding model

| Model | Dimension | VRAM | Notes |
|-------|-----------|------|-------|
| `BAAI/bge-m3` | 1024 | ~2 GB | Default. Works well on CPU too if latency is acceptable. |

Set via `cosmo42.model.embedding.name` (or `COSMO42_MODEL_EMBEDDING_NAME`).

## Serving the LLM

### vLLM (recommended for the default model)

```bash
pip install vllm

vllm serve cyankiwi/gemma-4-26B-A4B-it-AWQ-4bit \
  --served-model-name gemma-4-26B \
  --host 0.0.0.0 --port 8000 \
  --enable-auto-tool-choice --tool-call-parser hermes
```

Configure Cosmo42:

```properties
spring.ai.openai.base-url=http://<vllm-host>:8000/v1
cosmo42.model.llm.name=gemma-4-26B
```

### llama.cpp server (low-VRAM preset)

```bash
llama-server \
  -m gemma-4-E2B-UD-Q4_K_XL.gguf \
  --host 0.0.0.0 --port 8000 \
  --ctx-size 8192 \
  --jinja
```

```properties
spring.ai.openai.base-url=http://<llamacpp-host>:8000/v1
cosmo42.model.llm.name=gemma-4-E2B-UD-Q4_K_XL
```

### Ollama

```bash
ollama serve
ollama pull gemma-4-e2b
```

```properties
spring.ai.openai.base-url=http://<ollama-host>:11434/v1
cosmo42.model.llm.name=gemma-4-e2b
```

Ollama pulls its own GGUF build of Gemma 4 E2B (default quantization `Q4_K_M`), so size, latency and output quality differ slightly from the `Q4_K_XL` build used in the llama.cpp example above.

> Ollama's OpenAI compatibility is partial; verify that vision and tool calling work with the model you chose before committing to it.

## Serving the embedding model

`BAAI/bge-m3` can be served by either of the runtimes below. TEI is Hugging Face's dedicated embeddings server, lighter and simpler when embeddings are the only workload. vLLM in `--task embed` mode reuses the same runtime as the chat server, so a single stack can handle both LLM and embeddings.

### Hugging Face `text-embeddings-inference` (TEI)

```bash
docker run -p 8001:80 --gpus all \
  ghcr.io/huggingface/text-embeddings-inference:latest \
  --model-id BAAI/bge-m3
```

```properties
spring.ai.openai.embedding.base-url=http://<tei-host>:8001
spring.ai.openai.embedding.embeddings-path=/embeddings
cosmo42.model.embedding.name=BAAI/bge-m3
```

### vLLM (embeddings mode)

```bash
vllm serve BAAI/bge-m3 \
  --task embed \
  --host 0.0.0.0 --port 8001
```

```properties
spring.ai.openai.embedding.base-url=http://<vllm-embed-host>:8001/v1
spring.ai.openai.embedding.embeddings-path=/embeddings
```

## Bringing your own model

Any model is fine if it satisfies the requirements at the top. Practical checklist:

1. **Chat endpoint** returns OpenAI-shaped responses at `<base-url>/chat/completions`.
2. The model accepts **image inputs** (vision). Cosmo42's ingestion pipeline relies on it for page parsing.
3. The model supports **tool calling** in OpenAI's schema. Required by the chat pipeline so the LLM can call `KBDocumentSimilaritySearchTool`.
4. **Embedding endpoint** returns vectors with dimension **1024**, otherwise migrate the schema (`VECTOR(1024)` in `V2__kb_document_chunk.sql`) and re-embed everything.

If any of those is missing, ingestion or retrieval will fail at runtime,  usually with a clear error message in the backend logs.
