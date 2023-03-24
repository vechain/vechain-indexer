import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation(project(":packages:common"))
}

tasks.getByName<BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<Jar>("jar") {
    enabled = false
}