import java.io.ByteArrayOutputStream

dependencies {
    implementation(project(":packages:common"))
}

val contractAddress = project.extra

val dbSetup = tasks.register<Exec>("dbSetup") {
    workingDir(rootDir)
    commandLine("make", "db-all")
}

val startThor = tasks.register<Exec>("startThor") {
    dependsOn("dbSetup")
    workingDir(rootDir)
    commandLine("docker", "compose", "-f", "packages/e2e/thor/docker-compose.yaml", "up", "--build", "-d", "--wait")
}

val extractContractAddress = tasks.register("extractContractAddress") {
    dependsOn(startThor)

    doLast {
        val containerName = "thor-thor-tx-script-1"

        // Wait for the container to finish
        println("⏳ Waiting for $containerName to finish seeding...")
        exec {
            workingDir(rootDir)
            commandLine("docker", "wait", containerName)
        }
        println("✅ Seeder container has exited")

        // Now extract the contract address from its logs
        val output = ByteArrayOutputStream()
        exec {
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
        contractAddress["BLACKLIST_CONTRACT_ADDRESS"] = address
    }
}

val startApp = tasks.register<Exec>("startApp") {
    dependsOn(extractContractAddress)
    workingDir(rootDir)

    doFirst {
        val address = contractAddress["BLACKLIST_CONTRACT_ADDRESS"] as? String
            ?: throw GradleException("Contract address is missing in project.extra")

        println("ℹ️  Setting BLACKLIST_CONTRACT_ADDRESS=$address")
        environment("BLACKLIST_CONTRACT_ADDRESS", address)
        environment("API_ENV_FILE_NAME", "packages/e2e/api.env")
        environment("INDEXER_ENV_FILE_NAME", "packages/e2e/indexer.env")
    }

    commandLine(
        "docker", "compose",
        "-f", "docker-compose.yaml",
        "-f", "packages/e2e/docker-compose.yaml",
        "up", "--build", "-d", "--wait"
    )
}

tasks.register("preE2e") {
    dependsOn(startApp)
}

task<Exec>("postE2e") {
    workingDir(rootDir)
    // Not cleaning data in case we need to spin up the containers again and debug
    commandLine("make", "down")
}

tasks.test {
    dependsOn("preE2e")
    finalizedBy("postE2e")
}