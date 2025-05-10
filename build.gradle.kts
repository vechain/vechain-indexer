import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("jacoco-report-aggregation")
    id("com.diffplug.spotless") version "6.25.0"
    id("org.sonarqube") version "6.0.1.5171"
    jacoco
}

sonar {
    properties {
        property("sonar.projectKey", "vechain_veworld-indexer")
        property("sonar.organization", "vechain")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

java.sourceCompatibility = JavaVersion.VERSION_21

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
                "com.google.protobuf:protobuf-java:3.25.5",
                "org.java-websocket:Java-WebSocket:1.5.3",
            )
        }
    }

    spotless {
        kotlin {
            ktfmt().googleStyle().configure {
                it.setBlockIndent(4)
                it.setContinuationIndent(4)
            }
        }
    }

    group = "org.vechain"
    version = "1.0.0"

    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.vechain") }
        }
        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
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

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        dependsOn("installGitHooks")
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
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
        implementation("org.springframework.boot:spring-boot-starter:3.4.1")
        implementation("org.springframework.boot:spring-boot-starter-data-mongodb:3.4.1")
        implementation("org.springframework.boot:spring-boot-starter-webflux:3.4.1")
        implementation("org.springframework:spring-webflux") {
            version {
                strictly("6.2.1")
            }
        }
        implementation("org.springframework:spring-core") {
            version {
                strictly("6.2.1")
            }
        }
        implementation("org.springframework:spring-web") {
            version {
                strictly("6.2.1")
            }
        }
        implementation("io.netty:netty-codec-http") {
            version {
                strictly("4.1.118.Final")
            }
        }
        implementation("io.netty:netty-common") {
            version {
                strictly("4.1.118.Final")
            }
        }
        implementation("io.netty:netty-handler") {
            version {
                strictly("4.1.118.Final")
            }
        }

        implementation("org.springframework.boot:spring-boot-starter-actuator:3.4.1")

        implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.21")

        implementation("org.web3j:abi:4.9.8")
        implementation("org.web3j:contracts:4.9.8")
        implementation("org.bouncycastle:bcprov-jdk15on:1.70")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
        implementation("commons-codec:commons-codec:1.15")

        // Core indexer dependency
        implementation("org.vechain:indexer-core:4.0.0")

        // Test dependencies
        testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.1")
        testImplementation("org.testcontainers:testcontainers:1.20.4")
        testImplementation("org.testcontainers:junit-jupiter:1.20.4")
        testImplementation("org.testcontainers:mongodb:1.20.4")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
        testImplementation("io.mockk:mockk:1.13.14")
        testImplementation("io.strikt:strikt-core:0.35.1")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    }
}

dependencies {
    testImplementation(project(":packages:common"))
    testImplementation(project(":packages:indexer"))
    testImplementation(project(":packages:api"))
}
