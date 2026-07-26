# Multi-arch build: the final image is static and can run on x86 or ARM64.
FROM node:20-alpine AS build

WORKDIR /workspace
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
