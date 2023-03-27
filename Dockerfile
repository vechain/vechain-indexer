FROM eclipse-temurin:19 AS builder

ARG VEWORLD_PACKAGE

WORKDIR /opt/app

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

FROM eclipse-temurin:19

ARG VEWORLD_PACKAGE
ENV VEWORLD_PACKAGE $VEWORLD_PACKAGE

WORKDIR /opt/app

COPY --from=builder /opt/app/packages/$VEWORLD_PACKAGE/build/libs/$VEWORLD_PACKAGE*-SNAPSHOT.jar /opt/app/app.jar

ENTRYPOINT ["java", "-Djdk.tls.client.protocols=TLSv1.2", "-jar", "./app.jar", "-XX:+UseContainerSupport"]
