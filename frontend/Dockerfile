# Frontend Dockerfile
FROM node:18-alpine as build

WORKDIR /app

# Copy package files (+ .npmrc so legacy-peer-deps applies to npm ci/install)
COPY package*.json .npmrc ./

# Install dependencies (prefer lockfile, fallback to install if out of sync)
RUN npm ci --omit=dev || npm install --omit=dev

# Copy source code
COPY . .

# Create React App inlines every REACT_APP_* variable at BUILD time — there is no
# runtime lookup, so a value supplied only to the running container never reaches the
# bundle. The local .env is git-ignored and absent from a CI build context, which is
# why a production image built without these args shipped an empty client id and a
# "Google ile giriş şu an kullanılamıyor" button. Pass them with --build-arg.
ARG REACT_APP_GOOGLE_CLIENT_ID=""
ARG REACT_APP_API_URL=""
ARG REACT_APP_SENTRY_DSN=""
ARG REACT_APP_SENTRY_ENV=""
ENV REACT_APP_GOOGLE_CLIENT_ID=$REACT_APP_GOOGLE_CLIENT_ID     REACT_APP_API_URL=$REACT_APP_API_URL     REACT_APP_SENTRY_DSN=$REACT_APP_SENTRY_DSN     REACT_APP_SENTRY_ENV=$REACT_APP_SENTRY_ENV

# Build the application
RUN npm run build

# Production stage
FROM nginx:alpine

# Install envsubst (gettext)
RUN apk add --no-cache gettext

# Copy built app to nginx
COPY --from=build /app/build /usr/share/nginx/html

# Copy nginx configuration template
COPY nginx.conf /etc/nginx/templates/default.conf.template

# Entry point to substitute env vars into nginx config at runtime.
# STORE_HOST / ADMIN_HOST are used by the legacy /store/* → / redirect
# (cross-domain bounce when an admin host receives a storefront URL).
CMD ["/bin/sh", "-c", ": ${API_ORIGIN:=http://backend:8080}; : ${STORE_HOST:=localhost}; : ${ADMIN_HOST:=localhost}; : ${PORT:=80}; NGINX_RESOLVER=$(awk '/^nameserver/{print $2; exit}' /etc/resolv.conf); : ${NGINX_RESOLVER:=127.0.0.11}; case $NGINX_RESOLVER in *:*) NGINX_RESOLVER=[$NGINX_RESOLVER];; esac; export API_ORIGIN STORE_HOST ADMIN_HOST PORT NGINX_RESOLVER; envsubst '${API_ORIGIN} ${STORE_HOST} ${ADMIN_HOST} ${PORT} ${NGINX_RESOLVER}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]

# Expose port
EXPOSE ${PORT:-80}

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost/ || exit 1
