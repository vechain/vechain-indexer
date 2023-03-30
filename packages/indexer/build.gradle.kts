import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation(project(":packages:common"))
    implementation("org.web3j:utils:4.9.7")

}

tasks.getByName<BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<Jar>("jar") {
    enabled = false
}