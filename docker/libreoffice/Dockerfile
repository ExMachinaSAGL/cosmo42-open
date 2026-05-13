FROM debian:bookworm-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libreoffice-writer \
        libreoffice-calc \
        libreoffice-impress \
        fonts-liberation \
        fonts-liberation2 \
        fonts-dejavu \
        python3 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m -s /bin/bash libreoffice
USER libreoffice
WORKDIR /tmp

COPY server.py /app/server.py

EXPOSE 3000
CMD ["python3", "/app/server.py"]