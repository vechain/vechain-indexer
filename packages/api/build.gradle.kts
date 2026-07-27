import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("plugin.serialization") version "2.4.10"
}

dependencies {
    implementation(project(":packages:common"))
    implementation("org.springframework.boot:spring-boot-starter-web:3.5.16") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-validation:3.5.16")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Caffeine cache
    implementation("org.springframework.boot:spring-boot-starter-cache:3.5.16")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring3x:4.33.0")
}

tasks.getByName<BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<Jar>("jar") {
    enabled = false
}

dependencyLocking {
    lockAllConfigurations()
}
