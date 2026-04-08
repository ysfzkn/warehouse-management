# Postgres + pgvector custom image for Railway

This folder contains a custom Docker build that adds the [pgvector](https://github.com/pgvector/pgvector)
extension to Railway's official `ghcr.io/railwayapp-templates/postgres-ssl:17`
template. It is used to enable Cezeri v2 RAG features (product semantic search,
FAQ document retrieval) **without losing your existing production data**.

## Why a custom image?

Railway's default Postgres template does not ship pgvector. Vanilla
`pgvector/pgvector:pg17` is available on Docker Hub but lacks Railway's
SSL integration and config wiring. Extending Railway's template is the
cleanest path that keeps everything working.

## Data safety

Both the base image and this custom image use **PostgreSQL 17**, so the
on-disk data directory format is identical. Swapping the image on the
same Railway volume is binary-compatible and requires no dump/restore.

## How to deploy

1. **Push to your repo** — make sure this `docker/postgres-pgvector/`
   directory is committed.
2. **Railway dashboard** → open your Postgres service → `Settings` →
   `Source` → switch from `Docker image` to `Dockerfile`.
3. Set the `Root Directory` to `docker/postgres-pgvector` (Railway will
   find the Dockerfile automatically) or set the Dockerfile Path to
   `docker/postgres-pgvector/Dockerfile`.
4. Click **Deploy**. Railway will build the image and restart the
   Postgres service, re-attaching your existing volume.
5. **Wait for the health check to go green** — your data is preserved.
6. In the admin web UI, go to `/admin/assistant/dashboard` and click
   **"RAG Şemayı Etkinleştir"**. This idempotently creates the
   `product_embedding` table and adds the `embedding` column to
   `assistant_document_chunk`. It also re-runs the runtime pgvector
   detection so RAG becomes available without an app restart.
7. Click **"Ürünleri Yeniden İndeksle"** to compute embeddings for all
   active products. Uses Azure OpenAI's `text-embedding-3-small`;
   ~$0.02 per million tokens.

## Rollback

Your data is not modified by this process. If anything goes wrong, you
can switch the service source back to `ghcr.io/railwayapp-templates/postgres-ssl:17`
and the volume will mount cleanly. The `product_embedding` table and
`assistant_document_chunk.embedding` column will simply become unused
(the app detects their absence at boot and disables RAG).

## Verifying the build locally

```bash
docker build -t local/postgres-pgvector:17 docker/postgres-pgvector
docker run --rm -e POSTGRES_PASSWORD=test -p 5432:5432 local/postgres-pgvector:17
# in another shell
psql -h localhost -U postgres -c "CREATE EXTENSION vector; SELECT 'ok';"
```
