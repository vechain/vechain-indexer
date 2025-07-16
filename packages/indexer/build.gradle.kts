import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation(project(":packages:common"))
    implementation("org.web3j:utils:4.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.pemistahl:lingua:1.2.2")
    implementation("com.github.vechain:thor-devkit.java:v1.0.0")
}

tasks.getByName<BootJar>("bootJar") { enabled = true }

tasks.getByName<Jar>("jar") { enabled = false }
