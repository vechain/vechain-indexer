import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {

    implementation(project(":packages:common"))
    implementation(project(":packages:thor-model"))

    implementation("org.springframework.boot:spring-boot-starter-web:3.1.0")
    implementation("org.springframework.boot:spring-boot-starter-validation:3.1.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0")
}

tasks.getByName<BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<Jar>("jar") {
    enabled = false
}
