pluginManagement {
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
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        gradlePluginPortal()
    }
}
rootProject.name = "vechain-indexer"

include("packages:api")
include("packages:indexer")
include("packages:common")
include("packages:e2e")
