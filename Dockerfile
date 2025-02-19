FROM amazoncorretto:21-alpine3.20 AS builder

RUN apk update && apk upgrade
RUN apk add --no-cache curl

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

FROM amazoncorretto:21-alpine3.20 AS prod

RUN apk update && apk upgrade
RUN apk add --no-cache curl

ARG PACKAGE_NAME
ENV PACKAGE_NAME=$PACKAGE_NAME

WORKDIR /usr/app

COPY --from=builder /usr/app/packages/$PACKAGE_NAME/build/libs/$PACKAGE_NAME*.jar /usr/app/app.jar

CMD ["java", "-jar", "/usr/app/app.jar"]
