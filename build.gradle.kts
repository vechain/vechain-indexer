import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

buildscript {
	repositories {
		mavenLocal()
		mavenCentral()
	}
	dependencies {
		classpath("org.springframework.boot:spring-boot-gradle-plugin:3.0.4")
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
	}
}

plugins {
	id("org.springframework.boot") version "3.0.4"
	id("io.spring.dependency-management") version "1.1.0"
	id("maven-publish")
	id("org.jetbrains.kotlin.jvm") version "1.8.10"
	kotlin("plugin.spring") version "1.8.10"
	jacoco
}

group = "org.vechain"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

tasks.withType<JavaCompile> {
	options.encoding = "UTF-8"
}

repositories {
	mavenLocal()
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
	maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-cache")

	implementation("org.jetbrains.kotlin:kotlin-reflect")

	implementation("org.bouncycastle:bcprov-jdk15on:1.70")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.testcontainers:testcontainers:1.17.6")
	testImplementation("org.testcontainers:junit-jupiter:1.17.6")
	testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
	testImplementation("org.testcontainers:mongodb:1.17.6")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.4.2")

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
	finalizedBy(tasks.jacocoTestReport)
}


tasks.getByName<BootJar>("bootJar") {
	enabled = true
}

tasks.getByName<Jar>("jar") {
	enabled = false
}