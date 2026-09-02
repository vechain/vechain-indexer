# syntax=docker/dockerfile:1

FROM amazoncorretto:21.0.12-alpine3.23 AS builder

ARG PACKAGE_NAME

RUN apk --no-cache upgrade && apk add --no-cache curl

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
RUN test -n "$PACKAGE_NAME" || (echo "PACKAGE_NAME build arg must be set" >&2 && exit 1)
ENV PACKAGE_NAME=$PACKAGE_NAME

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    --mount=type=secret,id=gradle_props,target=/root/.gradle/gradle.properties,required=false \
    ./gradlew packages:$PACKAGE_NAME:build -x test

FROM amazoncorretto:21.0.12-alpine3.23 AS prod

ARG PACKAGE_NAME
# Traceability only: the content_hash.sh value this image was built from.
ARG CONTENT_HASH=""

RUN apk --no-cache upgrade && apk add --no-cache curl

LABEL org.vechain.indexer.content-hash=$CONTENT_HASH

ENV PACKAGE_NAME=$PACKAGE_NAME

WORKDIR /usr/app

COPY --from=builder /usr/app/packages/$PACKAGE_NAME/build/libs/$PACKAGE_NAME*.jar /usr/app/app.jar

# APP_VERSION comes from the runtime, so a new version is not new image content.
CMD ["java", "-jar", "/usr/app/app.jar"]
