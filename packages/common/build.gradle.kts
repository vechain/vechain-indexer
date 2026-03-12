import org.gradle.jvm.tasks.Jar
import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring3x:4.23.0")
}

tasks.getByName<BootJar>("bootJar") { enabled = false }

tasks.getByName<Jar>("jar") { enabled = true }
