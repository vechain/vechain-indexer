dependencies {
    implementation(project(":packages:common"))
}

task<Exec>("preE2e") {
    environment("ENV_FILE_NAME", ".env.example")
    workingDir(rootDir)
    commandLine("make", "clean", "start")
}

task<Exec>("postE2e") {
    environment("ENV_FILE_NAME", ".env.example")
    workingDir(rootDir)
    //Not cleaning data in case we need to spin up the containers again and debug
    commandLine("make", "down")
}

tasks.test {
//    dependsOn("preE2e")
//    finalizedBy("postE2e")
}
