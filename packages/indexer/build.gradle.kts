import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation(project(":packages:thor-model"))
    implementation(project(":packages:common"))
    implementation(project(":packages:indexer-core"))
    implementation("org.web3j:utils:4.9.8")
    implementation("com.github.vechain:thor-devkit.java:v1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")

}

tasks.getByName<BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<Jar>("jar") {
    enabled = false
}
