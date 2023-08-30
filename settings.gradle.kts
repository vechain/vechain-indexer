pluginManagement {
    repositories {
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
        gradlePluginPortal()
    }
}
rootProject.name = "vechain-indexer"

include("packages:api")
include("packages:common")
include("packages:indexer-core")
include("packages:indexer")
include("packages:e2e")
