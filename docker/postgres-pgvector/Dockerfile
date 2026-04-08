# =============================================================================
# Custom Postgres image for Railway: postgres-ssl:17 + pgvector
# =============================================================================
# Use this when you want to enable Cezeri v2 RAG features on an existing
# Railway Postgres service without losing any data. Railway's default
# postgres-ssl image does NOT ship pgvector, so we extend it.
#
# Data safety: this image uses the same PostgreSQL 17 binary as the official
# Railway postgres-ssl:17 template, so the on-disk data directory format is
# identical. Simply repoint the Railway service at this Dockerfile, redeploy,
# and the existing volume (prod data) will mount cleanly with the new
# extension files available. No pg_dump / pg_restore required.
#
# Deploy steps on Railway:
#   1. Commit this file to your repo.
#   2. In Railway, open your Postgres service → Settings → Source → change
#      from "Docker image" to "Dockerfile", pointing at
#      docker/postgres-pgvector/Dockerfile.
#   3. Redeploy. The volume stays bound to the service.
#   4. Wait for the deploy to finish. Your data is preserved.
#   5. In the admin app: dashboard → "RAG Şemayı Etkinleştir" button
#      (or: POST /api/admin/assistant/rag/init). This creates the vector
#      columns and product_embedding table idempotently on your existing DB.
#   6. Run "Ürünleri Yeniden İndeksle" to populate the product vectors.
# =============================================================================

FROM ghcr.io/railwayapp-templates/postgres-ssl:17

USER root

# Build deps + pgvector at the latest stable tag.
# apt-get caches are cleaned in the same layer to keep the image small.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        build-essential \
        postgresql-server-dev-17 \
        ca-certificates \
        git \
 && rm -rf /var/lib/apt/lists/* \
 && git clone --branch v0.8.0 --depth 1 https://github.com/pgvector/pgvector.git /tmp/pgvector \
 && cd /tmp/pgvector \
 && make OPTFLAGS="" \
 && make install \
 && rm -rf /tmp/pgvector \
 && apt-get purge -y --auto-remove build-essential postgresql-server-dev-17 git \
 && rm -rf /var/lib/apt/lists/*

# Back to the default Postgres user that the base image uses.
USER postgres
