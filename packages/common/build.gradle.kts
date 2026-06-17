import org.gradle.jvm.tasks.Jar
import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Required by ContractUtils for ABI encoding (createClause) and keccak signature derivation.
    implementation("com.github.vechain:thor-devkit.java:v1.0.0")
    implementation("org.web3j:utils:4.12.1")

    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring3x:4.33.0")
}

tasks.getByName<BootJar>("bootJar") { enabled = false }

tasks.getByName<Jar>("jar") { enabled = true }

dependencyLocking {
    lockAllConfigurations()
}
