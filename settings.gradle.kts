pluginManagement {
    repositories {
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
    }
}

rootProject.name = "CircuitEQ"

include(
    ":app",
    ":engine",
    ":core:model",
    ":core:dsp",
    ":core:formats",
    ":core:data",
    ":core:autoeq",
)
