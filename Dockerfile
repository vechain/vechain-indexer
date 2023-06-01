FROM eclipse-temurin:17-jdk-jammy AS builder

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

FROM eclipse-temurin:17-jre-jammy AS prod

ARG VEWORLD_PACKAGE
ENV VEWORLD_PACKAGE $VEWORLD_PACKAGE

WORKDIR /usr/app

COPY --from=builder /usr/app/packages/$VEWORLD_PACKAGE/build/libs/$VEWORLD_PACKAGE*.jar /usr/app/app.jar

CMD ["java", "-jar", "/usr/app/app.jar"]