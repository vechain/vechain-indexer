dependencies {
    implementation(project(":packages:thor-model"))


    // Other dependencies...
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")

}


tasks.getByName<Jar>("jar") {
    enabled = true
}
