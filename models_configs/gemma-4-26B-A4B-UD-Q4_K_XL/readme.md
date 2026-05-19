# Offline Multimodal AI Server: Gemma 4 26B (llama.cpp)

This repository contains the configuration to deploy an air-gapped, offline, multimodal AI server using **Gemma 4 26B**.
It is heavily optimized for a **24GB VRAM GPU to support up to 3 concurrent users with a 24K context window each**.
It utilizes `llama.cpp` for native GGUF execution, offering 100% compatibility with the OpenAI API standard, hardware acceleration (NVIDIA CUDA), and concurrent request handling via continuous batching.

## 1. Prerequisites & Asset Downloads

Before starting the server, you need to download the model weights and the visual projector.

### 📦 Download the Model
**Gemma 4 26B (Q4_K_XL Quantization)**
*   **Source:** [unsloth/gemma-4-26B-A4B-it-GGUF](https://huggingface.co/unsloth/gemma-4-26B-A4B-it-GGUF)
*   **Direct File:** [gemma-4-26B-A4B-it-UD-Q4_K_XL.gguf](https://huggingface.co/unsloth/gemma-4-26B-A4B-it-GGUF?show_file_info=gemma-4-26B-A4B-it-UD-Q4_K_XL.gguf)

### 👁️ Download the Visual Projector (Required for Image parsing)
*   **Source:** [unsloth/gemma-4-26B-A4B-it-GGUF/tree/main](https://huggingface.co/unsloth/gemma-4-26B-A4B-it-GGUF/tree/main)
*   **Direct File:** `mmproj-BF16.gguf` *(Rename to `gemma-4-26B-mmproj-BF16.gguf`)*


## 2. Server Directory Structure

Copy the downloaded assets to your server and organize them into a `models` directory in the root of your project:

```text
/project-root
 ├── docker-compose.yml
 └── /models
      ├── gemma-4-26B-A4B-it-UD-Q4_K_XL.gguf
      └── gemma-4-26B-mmproj-BF16.gguf
```

## 3. Configuration (llama.cpp)

The included `docker-compose.yml` configures `llama.cpp` as an API server.
It is heavily optimized for a 24GB VRAM GPU to support up to 3 concurrent users with a 24K context window each.

### `docker-compose.yml`

```yaml
services:
  llamacpp-gemma4:
    image: ghcr.io/ggml-org/llama.cpp:server-cuda
    container_name: llamacpp-gemma-4
    restart: unless-stopped

    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: ["gpu"]

    ports:
      - "8000:8000"

    volumes:
      - ./models:/models

    command:
      - "-m"
      - "/models/gemma-4-26B-A4B-it-UD-Q4_K_XL.gguf"
      - "--mmproj"
      - "/models/gemma-4-26B-mmproj-BF16.gguf"
      - "--alias"
      - "gemma-4-26B"
      - "--host"
      - "0.0.0.0"
      - "--port"
      - "8000"
      - "--parallel"
      - "3"
      - "--cont-batching"
      - "-c"
      - "73728"            # Total context (24.576 x 3 users)
      - "-ngl"
      - "99"               # Use 100% of VRAM
      - "-fa"              # Flash Attention
      - "on"
      - "-ctk"
      - "q8_0"             # Cache Key at 8-bit
      - "-ctv"
      - "q8_0"             # Cache Value at 8-bit

    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 30s
      timeout: 5s
      retries: 5
      start_period: 60s

```

### Key Parameters Explained

* **`--alias "gemma-4-26B"`**: Exposes the model name for API calls (OpenAI drop-in replacement).
* **`--parallel 3` & `--cont-batching**`: Allocates exactly 3 simultaneous processing slots. Any 4th concurrent request will be gracefully queued.
* **`-c 73728`**: Total context window allocated. `llama.cpp` divides this strictly by the number of parallel slots (24,576 tokens × 3 users = 73,728).
* **`-ngl 99`**: Offloads all layers to the GPU for maximum inference speed.
* **`-fa on`**: Enables Flash Attention to reduce memory footprint.
* **`-ctk q8_0` / `-ctv q8_0**`: Compresses the KV Cache to 8-bit, essential to prevent Out-Of-Memory (OOM) crashes when managing large context windows on a 24GB GPU.

