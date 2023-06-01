dependencies {
    implementation(project(":packages:thor-model"))
}


tasks.getByName<Jar>("jar") {
    enabled = true
}
