FROM eclipse-temurin:19 AS builder

WORKDIR /opt/app

COPY ./gradle ./gradle
COPY ./gradlew ./

RUN ./gradlew

COPY ./build.gradle.kts ./
COPY ./settings.gradle.kts ./
COPY ./system.properties ./
COPY ./src ./src

RUN ./gradlew build publishToMavenLocal

FROM eclipse-temurin:19

WORKDIR /opt/app

COPY --from=builder /opt/app/build/libs/vechain-indexer*.jar /opt/app/app.jar

ENTRYPOINT ["java", "-Djdk.tls.client.protocols=TLSv1.2", "-jar", "./app.jar", "-XX:+UseContainerSupport"]
