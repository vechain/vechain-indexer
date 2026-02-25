import java.io.ByteArrayOutputStream
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations

dependencies {
    implementation(project(":packages:common"))
}

val execOperations = serviceOf<ExecOperations>()
val blacklistContractAddress = objects.property<String>()

val dbSetup = tasks.register<Exec>("dbSetup") {
    workingDir(rootDir)
    commandLine("make", "db-all")
}

val startThor = tasks.register<Exec>("startThor") {
    dependsOn(dbSetup)
    workingDir(rootDir)
    commandLine("docker", "compose", "-f", "packages/e2e/thor/docker-compose.yaml", "up", "--build", "-d", "--wait")
}

val extractContractAddress = tasks.register("extractContractAddress") {
    dependsOn(startThor)

    doLast {
        val containerName = "thor-thor-tx-script-1"

        // Wait for the container to finish
        println("⏳ Waiting for $containerName to finish seeding...")
        execOperations.exec {
            workingDir(rootDir)
            commandLine("docker", "wait", containerName)
        }
        println("✅ Seeder container has exited")

        // Now extract the contract address from its logs
        val output = ByteArrayOutputStream()
        execOperations.exec {
            workingDir(rootDir)
            commandLine("docker", "logs", containerName)
            standardOutput = output
            errorOutput = output
        }

        val logs = output.toString()
        val regex = Regex("Deployed NFTBlacklist @ (0x[a-fA-F0-9]{40})")
        val match = regex.find(logs)
        val address = match?.groupValues?.get(1)
            ?: throw GradleException("❌ Failed to extract contract address from logs")

        println("✅ Extracted contract address: $address")
        blacklistContractAddress.set(address)
    }
}

val buildJars = tasks.register<Exec>("buildJars") {
    dependsOn(extractContractAddress)
    workingDir(rootDir)
    commandLine("./gradlew", "packages:api:build", "packages:indexer:build", "-x", "test")
}

val startApp = tasks.register<Exec>("startApp") {
    dependsOn(buildJars)
    workingDir(rootDir)

    doFirst {
        val address = blacklistContractAddress.orNull
            ?: throw GradleException("Contract address is missing from the Gradle property")

        println("ℹ️  Setting BLACKLIST_CONTRACT_ADDRESS=$address")
        environment("BLACKLIST_CONTRACT_ADDRESS", address)
        environment("API_ENV_FILE_NAME", "packages/e2e/api.env")
        environment("INDEXER_ENV_FILE_NAME", "packages/e2e/indexer.env")
    }

    commandLine(
        "docker", "compose",
        "-f", "docker-compose.yaml",
        "-f", "packages/e2e/docker-compose.yaml",
        "-f", "docker-compose.prebuilt.yaml",
        "up", "--build", "-d", "--wait"
    )
}

val stopApp = tasks.register<Exec>("stopApp") {
    workingDir(rootDir)
    commandLine(
        "docker", "compose",
        "-f", "docker-compose.yaml",
        "-f", "packages/e2e/docker-compose.yaml",
        "-f", "docker-compose.prebuilt.yaml",
        "down"
    )
}

val preE2e = tasks.register("preE2e") {
    dependsOn(startApp)
}

val postE2e = tasks.register<Exec>("postE2e") {
    dependsOn(stopApp)
    workingDir(rootDir)
    commandLine("make", "db-clean")
}

tasks.named<Test>("test") {
    dependsOn(preE2e)
//    finalizedBy(postE2e)
}
