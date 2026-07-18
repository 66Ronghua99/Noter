FROM node:20-bookworm-slim AS dependencies

WORKDIR /build/server/noter-api
COPY server/noter-api/package.json server/noter-api/package-lock.json ./
RUN npm ci

FROM dependencies AS build

COPY server/noter-api/ ./
RUN npm run build

FROM node:20-bookworm-slim AS runtime

ENV NODE_ENV=production
WORKDIR /app

COPY --from=dependencies /build/server/noter-api/package.json /build/server/noter-api/package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /build/server/noter-api/dist ./dist

USER node
EXPOSE 3000
CMD ["node", "dist/index.js"]
