import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.0.5"
    id("io.spring.dependency-management") version "1.1.0"
    id("maven-publish")
    kotlin("jvm") version "1.7.22"
    kotlin("plugin.spring") version "1.7.22"
    id("jacoco-report-aggregation")
    jacoco
}


java.sourceCompatibility = JavaVersion.VERSION_17

allprojects {

    apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.springframework.boot")
        plugin("io.spring.dependency-management")
        plugin("maven-publish")
        plugin("jacoco")
        plugin("jacoco-report-aggregation")
    }

    group = "org.vechain"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)
    }

    tasks.clean {
        doFirst {
            delete(
                "build",
                "packages/api/build",
                "packages/indexer/build",
                "packages/common/build",
                "packages/e2e/build",
            )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging.showStandardStreams = true
        finalizedBy(tasks.jacocoTestReport)

        val failedTests = mutableListOf<Pair<TestDescriptor, Throwable?>>()
        val skippedTests = mutableListOf<Pair<TestDescriptor, Throwable?>>()

        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                when (result.resultType) {
                    TestResult.ResultType.FAILURE -> failedTests.add(Pair(testDescriptor, result.exception))
                    TestResult.ResultType.SKIPPED -> skippedTests.add(Pair(testDescriptor, result.exception))
                    else -> {}
                }
            }

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                logger.lifecycle("----")
                logger.lifecycle("Test result: ${result.resultType}")
                logger.lifecycle(
                    "Test summary: ${result.testCount} tests, " +
                            "${result.successfulTestCount} succeeded, " +
                            "${result.failedTestCount} failed, " +
                            "${result.skippedTestCount} skipped"
                )
                if (failedTests.isNotEmpty()) {
                    logger.lifecycle("\tFailed Tests:")
                    failedTests.forEach {
                        logger.lifecycle("\t\t${it.first.className} - ${it.first.name}", it.second)
                    }
                }

                if (skippedTests.isNotEmpty()) {
                    logger.lifecycle("\tSkipped Tests:")
                    skippedTests.forEach {
                        logger.lifecycle("\t\t${it.first.className} - ${it.first.name}")
                    }
                }
            }
        })
    }

    dependencies {

        // Common dependencies
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
        implementation("org.springframework.boot:spring-boot-starter-webflux")
        implementation("org.springframework.boot:spring-boot-starter-actuator")

        implementation("org.jetbrains.kotlin:kotlin-reflect")

        implementation("org.bouncycastle:bcprov-jdk15on:1.70")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
        implementation("com.google.code.gson:gson:2.10.1")

        // Test dependencies
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.testcontainers:testcontainers:1.17.6")
        testImplementation("org.testcontainers:junit-jupiter:1.17.6")
        testImplementation("org.testcontainers:mongodb:1.17.6")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
        testImplementation("io.mockk:mockk:1.13.4")
        testImplementation("io.strikt:strikt-core:0.34.1")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    }
}

dependencies {
    testImplementation(project(":packages:common"))
    testImplementation(project(":packages:indexer"))
    testImplementation(project(":packages:api"))
}