import org.gradle.jvm.tasks.Jar
import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    // Required by ContractUtils for ABI encoding (createClause) and keccak signature derivation.
    implementation("com.github.vechain:thor-devkit.java:v1.0.0")
    implementation("org.web3j:utils:4.14.1")

    // Postgres: versions come from the Spring Boot BOM.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring3x:4.33.0")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.getByName<BootJar>("bootJar") { enabled = false }

tasks.getByName<Jar>("jar") { enabled = true }

dependencyLocking {
    lockAllConfigurations()
}
