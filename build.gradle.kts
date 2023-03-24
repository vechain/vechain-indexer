import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.0.5"
    id("io.spring.dependency-management") version "1.1.0"
    id("maven-publish")
    kotlin("jvm") version "1.7.22"
    kotlin("plugin.spring") version "1.7.22"
    jacoco
}


java.sourceCompatibility = JavaVersion.VERSION_17

allprojects {

    apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.springframework.boot")
        plugin("io.spring.dependency-management")
        plugin("maven-publish")
        plugin("jacoco")
    }

    group = "org.vechain"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging.showStandardStreams = true
    }

    dependencies {

        // Common dependencies
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
        implementation("org.springframework.boot:spring-boot-starter-webflux")

        implementation("org.jetbrains.kotlin:kotlin-reflect")

        implementation("org.bouncycastle:bcprov-jdk15on:1.70")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")

        // Test dependencies
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.testcontainers:testcontainers:1.17.6")
        testImplementation("org.testcontainers:junit-jupiter:1.17.6")
        testImplementation("org.testcontainers:mongodb:1.17.6")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    }
}
