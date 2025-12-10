import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation(project(":packages:common"))
    implementation("org.web3j:utils:4.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.pemistahl:lingua:1.2.2")
    implementation("com.github.vechain:thor-devkit.java:v1.0.0")
    implementation("io.prometheus:prometheus-metrics-core:1.4.3")
    implementation("io.prometheus:prometheus-metrics-instrumentation-jvm:1.4.3")
    implementation("io.prometheus:prometheus-metrics-exporter-httpserver:1.4.3")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
}

tasks.getByName<BootJar>("bootJar") { enabled = true }

tasks.getByName<Jar>("jar") { enabled = false }
