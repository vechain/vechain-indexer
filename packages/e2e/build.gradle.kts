dependencies {

}

task<Exec>("preE2e") {
    environment("ENV_FILE_NAME", ".env.example")
    workingDir(rootDir)
    commandLine("make", "clean", "start")
}

task<Exec>("postE2e") {
    workingDir(rootDir)
    commandLine("make", "down")
}

tasks.test {
    dependsOn("preE2e")
    finalizedBy("postE2e")
}
