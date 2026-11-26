# syntax=docker/dockerfile:1

FROM --platform=$BUILDPLATFORM golang:1.27-alpine AS builder
WORKDIR /src

COPY go.mod go.sum* ./
RUN go mod download

COPY . .
ARG TARGETOS=linux
ARG TARGETARCH=amd64
ARG VERSION=1.0.0

RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath \
    -ldflags="-s -w -X github.com/fragge1337/kluddra/server/internal/config.Version=${VERSION}" \
    -o /out/kluddra ./cmd/kluddra && \
    mkdir -p /out/data

FROM gcr.io/distroless/static-debian12:nonroot
LABEL org.opencontainers.image.title="Kluddra Server"
LABEL org.opencontainers.image.description="Self-hosted notes, beautifully simple"
LABEL org.opencontainers.image.source="https://github.com/fragge1337/kluddra"

COPY --from=builder /out/kluddra /kluddra
COPY --from=builder --chown=65532:65532 /out/data /data

ENV KLUDDRA_PORT=8080 \
    KLUDDRA_DATA_DIR=/data

EXPOSE 8080
VOLUME ["/data"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD ["/kluddra", "healthcheck"]

ENTRYPOINT ["/kluddra"]
