// Standalone build for the pure-Kotlin domain module, so its tests run with only a JDK
// (no Android SDK needed): `gradle -p domain test`. The root build also includes this module.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "domain"
