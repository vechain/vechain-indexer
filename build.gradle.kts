import com.diffplug.spotless.LineEnding
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    id("jacoco-report-aggregation")
    id("com.diffplug.spotless") version "8.10.0"
    jacoco
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

allprojects {
    apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.springframework.boot")
        plugin("io.spring.dependency-management")
        plugin("maven-publish")
        plugin("jacoco")
        plugin("jacoco-report-aggregation")
        plugin("com.diffplug.spotless")
    }

    configurations.all {
        resolutionStrategy {
            force(
                "com.google.protobuf:protobuf-java:3.25.9",
                "org.java-websocket:Java-WebSocket:1.6.0",
                "org.bouncycastle:bcprov-jdk18on:1.85.2",
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "org.bouncycastle:bcutil-jdk18on:1.85",
            )
            dependencySubstitution {
                // The jdk15on line ended at 1.70 and receives no security fixes.
                substitute(module("org.bouncycastle:bcprov-jdk15on"))
                    .using(module("org.bouncycastle:bcprov-jdk18on:1.85.2"))
                substitute(module("org.bouncycastle:bcpkix-jdk15on"))
                    .using(module("org.bouncycastle:bcpkix-jdk18on:1.85"))
                substitute(module("org.bouncycastle:bcutil-jdk15on"))
                    .using(module("org.bouncycastle:bcutil-jdk18on:1.85"))
            }
        }
    }

    spotless {
        kotlin {
            ktfmt().googleStyle().configure {
                it.setBlockIndent(4)
                it.setContinuationIndent(4)
            }
            lineEndings = LineEnding.UNIX
        }
    }

    group = "org.vechain"
    version = "1.0.0"

    repositories {
        mavenLocal()
        // Keep this vendored fallback ahead of JitPack so Docker builds do not depend on JitPack
        // availability for thor-devkit.java.
        maven {
            url = uri("${rootDir}/third_party/maven")
            content { includeGroup("com.github.vechain") }
        }
        // GitHub Packages publishes indexer-core immediately on release; Maven Central can lag
        // by hours, so try GitHub Packages first.
        val gprUser =
            project.findProperty("gpr.user") as String?
                ?: System.getenv("GITHUB_ACTOR")
        val gprKey =
            project.findProperty("gpr.key") as String?
                ?: System.getenv("GITHUB_TOKEN")
        if (gprUser != null && gprKey != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/vechain/indexer-core")
                content { includeGroup("org.vechain") }
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        }
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.vechain") }
        }
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }

    tasks.register("installGitHooks") {
        doLast {
            val hooksDir = File("${rootDir.path}/.git/hooks")
            val scriptsDir = File("${rootDir.path}/git-scripts")
            scriptsDir.listFiles()?.forEach { script ->
                val hook = File(hooksDir, script.name)
                hook.writeText(script.readText())
                hook.setExecutable(true)
            }
        }
    }

    tasks.register("resolveAndLockAll") {
        notCompatibleWithConfigurationCache("Resolves all configurations at execution time")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) {
                "${this.path} must be run with --write-locks (e.g. via `make update-locks`)"
            }
        }
        doLast { configurations.filter { it.isCanBeResolved }.forEach { it.resolve() } }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        dependsOn("installGitHooks")
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.set(listOf("-Xjsr305=strict"))
            jvmTarget.set(JvmTarget.JVM_21)
        }
        dependsOn("installGitHooks")
    }

    tasks.jacocoTestReport { dependsOn(tasks.test) }

    tasks.clean {
        val dirs =
            mutableListOf(
                layout.buildDirectory
                    .get()
                    .asFile.path,
                "${rootDir.path}/bin",
            )
        dirs.addAll(
            subprojects.flatMap {
                listOf(
                    it.layout.buildDirectory
                        .get()
                        .asFile.path,
                    "${it.projectDir.path}/bin",
                )
            },
        )

        doFirst { delete(dirs) }
    }

    tasks.register<JacocoReport>("codeCoverageReport") {
        subprojects {
            val subproject = this
            subproject.plugins.withType<JacocoPlugin>().configureEach {
                subproject.tasks
                    .matching { it.extensions.findByType<JacocoTaskExtension>() != null }
                    .configureEach {
                        val testTask = this
                        sourceSets(subproject.sourceSets.main.get())
                        executionData(testTask)
                    }
            }
        }

        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging.showStandardStreams = true
        finalizedBy(tasks.jacocoTestReport)

        val failedTests = mutableListOf<Pair<TestDescriptor, Throwable?>>()
        val skippedTests = mutableListOf<Pair<TestDescriptor, Throwable?>>()

        addTestListener(
            object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {}

                override fun beforeTest(testDescriptor: TestDescriptor) {}

                override fun afterTest(
                    testDescriptor: TestDescriptor,
                    result: TestResult,
                ) {
                    when (result.resultType) {
                        TestResult.ResultType.FAILURE ->
                            failedTests.add(Pair(testDescriptor, result.exception))
                        TestResult.ResultType.SKIPPED ->
                            skippedTests.add(Pair(testDescriptor, result.exception))
                        else -> {}
                    }
                }

                override fun afterSuite(
                    suite: TestDescriptor,
                    result: TestResult,
                ) {
                    logger.lifecycle("----")
                    logger.lifecycle("Test result: ${result.resultType}")
                    logger.lifecycle(
                        "Test summary: ${result.testCount} tests, " +
                            "${result.successfulTestCount} succeeded, " +
                            "${result.failedTestCount} failed, " +
                            "${result.skippedTestCount} skipped",
                    )
                    if (failedTests.isNotEmpty()) {
                        logger.lifecycle("\tFailed Tests:")
                        failedTests.forEach {
                            logger.lifecycle(
                                "\t\t${it.first.className} - ${it.first.name}",
                                it.second,
                            )
                        }
                        failedTests.clear()
                    }

                    if (skippedTests.isNotEmpty()) {
                        logger.lifecycle("\tSkipped Tests:")
                        skippedTests.forEach {
                            logger.lifecycle("\t\t${it.first.className} - ${it.first.name}")
                        }
                        skippedTests.clear()
                    }
                }
            },
        )
    }

    dependencies {

        // Common dependencies
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
        implementation("org.springframework.boot:spring-boot-starter-webflux")
        implementation("org.springframework.boot:spring-boot-starter-jetty")
        implementation("org.springframework:spring-webflux")
        implementation("org.springframework:spring-core")
        implementation("org.springframework:spring-web")

        implementation("org.springframework.boot:spring-boot-starter-actuator")

        implementation("org.jetbrains.kotlin:kotlin-reflect")

        implementation("org.web3j:abi:4.14.0")
        implementation("org.web3j:contracts:4.14.0")
        implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        implementation("commons-codec:commons-codec:1.22.1")

        // Monitoring dependencies
        implementation("io.micrometer:micrometer-registry-prometheus")
        implementation("net.logstash.logback:logstash-logback-encoder:8.1")

        // Core indexer dependency
        implementation("org.vechain:indexer-core:11.0.0")

        // Test dependencies
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.junit.jupiter:junit-jupiter-api")
        testImplementation("io.mockk:mockk:1.14.11")
        testImplementation("io.strikt:strikt-core:0.35.1")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

        // Declared so the native transports stay on the runtime classpath; versions
        // come from the Spring Boot BOM's netty.version so they cannot drift apart.
        implementation("io.netty:netty-handler")
        implementation("io.netty:netty-codec-http")
        implementation("io.netty:netty-common")
        implementation("io.netty:netty-buffer")
        implementation("io.netty:netty-transport")
        implementation("io.netty:netty-resolver")
        implementation("io.netty:netty-codec")
        implementation("io.netty:netty-codec-http2")
        implementation("io.netty:netty-resolver-dns")
        implementation("io.netty:netty-resolver-dns-native-macos")
        implementation("io.netty:netty-transport-native-epoll")
        implementation("io.netty:netty-transport-native-unix-common")
        implementation("io.netty:netty-codec-dns")
        implementation("io.netty:netty-resolver-dns-classes-macos")
        implementation("io.netty:netty-handler-proxy")
        implementation("io.netty:netty-codec-socks")
        implementation("io.netty:netty-transport-classes-epoll")

        // Override the okhttp3 4.3.1 transitively pulled by web3j:core:5.0.0 (via
        // thor-devkit.java:v1.0.0). 4.3.1 has known CVEs (e.g. CVE-2021-0341, fixed in 4.9.2).
        // 4.12.0 is the final 4.x release and API-compatible with web3j's expectations.
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
        implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    }
}

dependencies {
    testImplementation(project(":packages:common"))
    testImplementation(project(":packages:indexer"))
    testImplementation(project(":packages:api"))
}

dependencyLocking {
    lockAllConfigurations()
}
