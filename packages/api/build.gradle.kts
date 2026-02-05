import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("plugin.serialization") version "2.1.21"
}

dependencies {
    implementation(project(":packages:common"))
    implementation("org.springframework.boot:spring-boot-starter-web:3.4.5") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-validation:3.4.5")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Caffeine cache
    implementation("org.springframework.boot:spring-boot-starter-cache:3.4.5")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.7")
}

tasks.getByName<BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<Jar>("jar") {
    enabled = false
}
