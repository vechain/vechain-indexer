import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("plugin.serialization") version "1.9.25"
}

dependencies {
    implementation(project(":packages:common"))
    implementation("org.springframework.boot:spring-boot-starter-web:3.4.2") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty:3.4.2")
    implementation("org.springframework.boot:spring-boot-starter-validation:3.4.2")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Ensure Netty Version Consistency
    implementation("io.netty:netty-handler:4.1.118.Final")
    implementation("io.netty:netty-codec-http:4.1.118.Final")
    implementation("io.netty:netty-common:4.1.118.Final")
    implementation("io.netty:netty-buffer:4.1.118.Final")
    implementation("io.netty:netty-transport:4.1.118.Final")
    implementation("io.netty:netty-resolver:4.1.118.Final")
    implementation("io.netty:netty-codec:4.1.118.Final")
    implementation("io.netty:netty-codec-http2:4.1.118.Final")
    implementation("io.netty:netty-resolver-dns:4.1.118.Final")
    implementation("io.netty:netty-resolver-dns-native-macos:4.1.118.Final")
    implementation("io.netty:netty-transport-native-epoll:4.1.118.Final")
    implementation("io.netty:netty-transport-native-unix-common:4.1.118.Final")
    implementation("io.netty:netty-codec-dns:4.1.118.Final")
    implementation("io.netty:netty-resolver-dns-classes-macos:4.1.118.Final")
    implementation("io.netty:netty-handler-proxy:4.1.118.Final")
    implementation("io.netty:netty-codec-socks:4.1.118.Final")
    implementation("io.netty:netty-transport-classes-epoll:4.1.118.Final")
}

tasks.getByName<BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<Jar>("jar") {
    enabled = false
}
