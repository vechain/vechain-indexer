# syntax=docker/dockerfile:1
FROM amazoncorretto:21-alpine3.20 AS builder

RUN apk update && apk upgrade
RUN apk add --no-cache curl

ARG PACKAGE_NAME
ARG APP_VERSION

WORKDIR /usr/app

COPY gradle ./gradle
COPY gradlew ./

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew

COPY build.gradle.kts ./
COPY settings.gradle.kts ./
COPY system.properties ./
COPY packages ./packages

# Placing this after the COPY commands so we can cache builds
RUN test -n "PACKAGE_NAME"
ENV PACKAGE_NAME=$PACKAGE_NAME
ENV APP_VERSION=$APP_VERSION

# Ensure the version is in the form v.X.Y.Z
RUN echo "$APP_VERSION" | grep -Eq '^v\.[0-9]+\.[0-9]+\.[0-9]+(-dev)?$' || (echo "APP_VERSION $APP_VERSION is not of the form v.X.Y.Z or v.X.Y.Z-dev" && exit 1)

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    --mount=type=secret,id=gradle_props,target=/root/.gradle/gradle.properties \
    ./gradlew packages:$PACKAGE_NAME:build -x test

FROM amazoncorretto:21-alpine3.20 AS prod

RUN apk update && apk upgrade
RUN apk add --no-cache curl

ARG PACKAGE_NAME
ARG APP_VERSION

ENV PACKAGE_NAME=$PACKAGE_NAME
ENV APP_VERSION=$APP_VERSION

WORKDIR /usr/app

COPY --from=builder /usr/app/packages/$PACKAGE_NAME/build/libs/$PACKAGE_NAME*.jar /usr/app/app.jar

CMD java -Dapp.version=$APP_VERSION -jar /usr/app/app.jar
