FROM mcr.microsoft.com/playwright:v1.64.0-noble
WORKDIR /app
COPY deploy/media-browser/package*.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY --chown=pwuser:pwuser deploy/media-browser/*.mjs ./
COPY deploy/media-browser/LICENSE.playwright.txt ./
USER pwuser
ENV HOME=/tmp
EXPOSE 8080
CMD ["node", "server.mjs"]
