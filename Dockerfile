FROM eclipse-temurin:21-jdk-jammy AS builder

ARG PACKAGE_NAME

WORKDIR /usr/app

COPY gradle ./gradle
COPY gradlew ./

RUN ./gradlew

COPY build.gradle.kts ./
COPY settings.gradle.kts ./
COPY system.properties ./
COPY packages ./packages

# Placing this after the COPY commands so we can cache builds
RUN test -n "PACKAGE_NAME"
ENV PACKAGE_NAME=$PACKAGE_NAME

RUN ./gradlew packages:$PACKAGE_NAME:build -x test

FROM eclipse-temurin:21-jre-jammy AS prod

ARG PACKAGE_NAME
ENV PACKAGE_NAME=$PACKAGE_NAME

# Upgrade required system packages to fix vulnerabilities
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends \
        libgssapi-krb5-2 \
        libk5crypto3 \
        libkrb5-3 \
        libkrb5support0 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /usr/app

COPY --from=builder /usr/app/packages/$PACKAGE_NAME/build/libs/$PACKAGE_NAME*.jar /usr/app/app.jar

CMD ["java", "-jar", "/usr/app/app.jar"]
