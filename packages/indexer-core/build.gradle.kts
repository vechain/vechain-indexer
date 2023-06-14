dependencies {
    implementation(project(":packages:thor-model"))
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("org.slf4j:slf4j-api:1.7.32")

    // Other dependencies...
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")

}

tasks.getByName<Jar>("jar") {
    enabled = true
}
