# syntax=docker/dockerfile:1.7
# Multi-stage Dockerfile for Next.js (simplified)
# Supports runtime environment configuration

FROM node:24-bookworm-slim AS deps
WORKDIR /app

COPY package.json package-lock.json .npmrc ./
COPY scripts ./scripts

RUN npm ci

FROM node:24-bookworm-slim AS builder
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Build-time public env (baked into the client bundle)
ARG NEXT_PUBLIC_APP_ENV=production
ARG NEXT_PUBLIC_API_BASE_URL=/api
ARG NEXT_PUBLIC_BACKEND_SIGNATURE=prod

COPY --from=deps /app/node_modules ./node_modules
COPY . .

RUN set -eux; \
  export NEXT_PUBLIC_APP_ENV="$NEXT_PUBLIC_APP_ENV"; \
  export NEXT_PUBLIC_API_BASE_URL="$NEXT_PUBLIC_API_BASE_URL"; \
  export NEXT_PUBLIC_BACKEND_SIGNATURE="$NEXT_PUBLIC_BACKEND_SIGNATURE"; \
  npm run build

FROM node:24-bookworm-slim AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
COPY --from=builder --chown=node:node /app/public ./public
COPY --from=builder --chown=node:node /app/.next/standalone ./
COPY --from=builder --chown=node:node /app/.next/static ./.next/static

USER node
EXPOSE 3000

CMD ["node", "server.js"]
