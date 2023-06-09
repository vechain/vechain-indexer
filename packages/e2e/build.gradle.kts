dependencies {
    implementation(project(":packages:common"))
    implementation(project(":packages:api"))
}

task<Exec>("preE2e") {
    environment("INDEXER_ENV_FILE_NAME", "packages/indexer/.env.example")
    environment("API_ENV_FILE_NAME", "packages/api/.env.example")
    workingDir(rootDir)
    commandLine("make", "clean", "start")
}

task<Exec>("postE2e") {
    environment("INDEXER_ENV_FILE_NAME", "packages/indexer/.env.example")
    environment("API_ENV_FILE_NAME", "packages/api/.env.example")
    workingDir(rootDir)
    //Not cleaning data in case we need to spin up the containers again and debug
    commandLine("make", "down")
}

tasks.test {
    dependsOn("preE2e")
    finalizedBy("postE2e")
}
