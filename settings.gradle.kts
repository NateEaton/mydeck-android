pluginManagement {
    repositories {
        // 1. Put google() first and REMOVE the content filter block
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
        maven { url "https://jitpack.io" }
    }
}

rootProject.name = "MyDeck"
include ":app"