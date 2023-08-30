import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("plugin.serialization") version "1.5.10"
}

dependencies {

    implementation(project(":packages:common"))
    implementation(project(":packages:indexer-core"))

    implementation("org.springframework.boot:spring-boot-starter-web:3.1.0")
    implementation("org.springframework.boot:spring-boot-starter-validation:3.1.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}

tasks.getByName<BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<Jar>("jar") {
    enabled = false
}
