FROM golang:1.24.8-bookworm@sha256:4ed690d6649d63c312b99a6120025ec79ce3b542968a37da53d6236c7c61a848 AS build
WORKDIR /src
RUN git clone --depth 1 --branch RELEASE.2025-10-15T17-29-55Z https://github.com/minio/minio.git . \
    && test "$(git rev-parse HEAD)" = "9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a"
ENV CGO_ENABLED=0 GOTOOLCHAIN=local MINIO_RELEASE=RELEASE
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go build -p 2 -mod=readonly -tags kqueue -trimpath \
    -ldflags "$(go run ./buildscripts/gen-ldflags.go 2025-10-15T17:29:55Z)" -o /out/minio .

FROM alpine:3.24@sha256:294b683cb724975bec92580e1e685676bd4b50bda910ddb8c51d4cabeaec77e6
RUN apk add --no-cache ca-certificates curl \
    && addgroup -S -g 10001 toolbox \
    && adduser -S -D -u 10001 -G toolbox toolbox \
    && mkdir -p /data && chown toolbox:toolbox /data
COPY --from=build /out/minio /usr/local/bin/minio
COPY --from=build /src/LICENSE /usr/share/licenses/minio/LICENSE
LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.version="RELEASE.2025-10-15T17-29-55Z" \
      org.opencontainers.image.revision="9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a" \
      org.opencontainers.image.licenses="AGPL-3.0-only"
USER toolbox
WORKDIR /data
EXPOSE 9000 9001
ENTRYPOINT ["/usr/local/bin/minio"]
