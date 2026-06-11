# syntax=docker/dockerfile:1

# Build the Go co-process used by the ValidatorV2 indexer's PoS schedule reconstruction.
FROM golang:1.26-alpine AS go-builder
WORKDIR /src
RUN apk add --no-cache gcc musl-dev
COPY tools/thor-scheduler/go.mod tools/thor-scheduler/go.sum* ./
RUN --mount=type=cache,target=/root/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    GOTOOLCHAIN=auto go mod download
COPY tools/thor-scheduler/ ./
RUN --mount=type=cache,target=/root/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=1 GOTOOLCHAIN=auto go build -trimpath -o /out/thor-scheduler .

FROM amazoncorretto:21-alpine3.23 AS builder

ARG PACKAGE_NAME
ARG APP_VERSION

# Reference APP_VERSION so this layer's cache key changes per release,
# forcing apk to re-run against the current Alpine repos on every new
# version. Same-version rebuilds still hit cache. --no-cache keeps the
# apk index out of the final image.
RUN echo "Building $APP_VERSION" \
    && apk --no-cache upgrade \
    && apk add --no-cache curl

WORKDIR /usr/app

COPY gradle ./gradle
COPY gradlew ./

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew

COPY build.gradle.kts ./
COPY settings.gradle.kts ./
COPY system.properties ./
COPY third_party ./third_party
COPY packages ./packages

# Placing this after the COPY commands so we can cache builds
RUN test -n "PACKAGE_NAME"
ENV PACKAGE_NAME=$PACKAGE_NAME
ENV APP_VERSION=$APP_VERSION

# Ensure the version is in the form v.X.Y.Z or v.X.Y.Z-<suffix>
RUN echo "$APP_VERSION" | grep -Eq '^v\.[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+(\.[a-zA-Z0-9]+)*)?$' || (echo "APP_VERSION $APP_VERSION is not of the form v.X.Y.Z or v.X.Y.Z-suffix" && exit 1)

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    --mount=type=secret,id=gradle_props,target=/root/.gradle/gradle.properties,required=false \
    ./gradlew packages:$PACKAGE_NAME:build -x test

FROM amazoncorretto:21-alpine3.23 AS prod

ARG PACKAGE_NAME
ARG APP_VERSION

# Reference APP_VERSION so this layer's cache key changes per release,
# forcing apk to re-run against the current Alpine repos on every new
# version. Same-version rebuilds still hit cache. --no-cache keeps the
# apk index out of the final image.
RUN echo "Building $APP_VERSION" \
    && apk --no-cache upgrade \
    && apk add --no-cache curl

ENV PACKAGE_NAME=$PACKAGE_NAME
ENV APP_VERSION=$APP_VERSION

WORKDIR /usr/app

COPY --from=builder /usr/app/packages/$PACKAGE_NAME/build/libs/$PACKAGE_NAME*.jar /usr/app/app.jar
COPY --from=go-builder /out/thor-scheduler /usr/app/tools/thor-scheduler/thor-scheduler

CMD java -Dapp.version=$APP_VERSION -jar /usr/app/app.jar
