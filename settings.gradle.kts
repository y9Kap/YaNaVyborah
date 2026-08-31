pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "YaNaVyborah"
include(":app")
include(":core:common")
include(":core:content")
include(":core:crypto")
include(":core:database")
include(":core:files")
include(":core:model")
include(":core:navigation")
include(":core:search")
include(":core:ui")
include(":feature:observer")
include(":feature:settings")
include(":feature:voter")
include(":feature:workpressure")
