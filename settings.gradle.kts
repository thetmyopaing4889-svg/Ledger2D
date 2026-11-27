pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google-hosted Maven Central mirror for Robolectric test artifacts.
        // Shared GitHub Actions egress is receiving Maven Central HTTP 429.
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
            content { includeGroup("org.robolectric") }
        }
        mavenCentral()
        if (providers.gradleProperty("ocrBenchmark").orNull == "true") {
            maven { url = uri("https://jitpack.io"); content { includeGroup("cz.adaptech.tesseract4android") } }
        }
    }
}
rootProject.name = "Ledger2D"
include(":app")

if (providers.gradleProperty("ocrBenchmark").orNull == "true") include(":ocrbenchmark")
