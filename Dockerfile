FROM eclipse-temurin:17 AS builder

ARG VEWORLD_PACKAGE

WORKDIR /usr/app

COPY gradle ./gradle
COPY gradlew ./

RUN ./gradlew

COPY build.gradle.kts ./
COPY settings.gradle.kts ./
COPY system.properties ./
COPY packages ./packages

# Placing this after the COPY commands so we can cache builds
RUN test -n "VEWORLD_PACKAGE"
ENV VEWORLD_PACKAGE $VEWORLD_PACKAGE

RUN ./gradlew packages:$VEWORLD_PACKAGE:build publishToMavenLocal -x test

# Use distroless for prod and eclipse to debug inside the container
#FROM eclipse-temurin:17
FROM gcr.io/distroless/java17@sha256:78d2c280d0914978844d2a2dd2b5315acd437e33c6905b6c562dca97ae34d9b3

ARG VEWORLD_PACKAGE
ENV VEWORLD_PACKAGE $VEWORLD_PACKAGE

WORKDIR /usr/app

COPY --from=builder /usr/app/packages/$VEWORLD_PACKAGE/build/libs/$VEWORLD_PACKAGE*-SNAPSHOT.jar /usr/app/app.jar

CMD ["/usr/app/app.jar"]
