FROM node:22.23.3-alpine3.24 AS build
WORKDIR /workspace
COPY admin/package.json admin/package-lock.json admin/.npmrc ./
RUN --mount=type=cache,target=/workspace/.npm-cache npm ci --no-audit --no-fund
COPY admin/ ./
ARG VITE_APP_BASE_PATH=/admin/
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_APP_BASE_PATH=${VITE_APP_BASE_PATH}
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM nginx:1.30.5-alpine3.24
COPY deploy/nginx/nginx.conf /etc/nginx/nginx.conf
COPY deploy/nginx/common/ /etc/nginx/toolbox/
COPY deploy/nginx/templates/local.conf.template /etc/nginx/templates/default.conf.template
COPY deploy/site/ /usr/share/nginx/html/
COPY admin/public/favicon.svg /usr/share/nginx/html/favicon.svg
COPY --from=build /workspace/dist/ /usr/share/nginx/html/admin/
ENV NGINX_ENVSUBST_FILTER="^(WEB_DOMAIN|API_DOMAIN)$"
EXPOSE 80 443
