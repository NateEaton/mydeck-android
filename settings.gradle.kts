pluginManagement {
    repositories {
        // Put google() first. 
        // We removed the 'content { ... }' filter so it can find all needed artifacts.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            // Kotlin DSL requires 'url = uri(...)'
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "MyDeck"
include(":app")