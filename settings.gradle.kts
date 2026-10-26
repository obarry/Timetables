pluginManagement {
    // Where Gradle looks for plugins: Google's repository hosts the Android Gradle plugin.
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Libraries come from these repositories only: modules must not declare their own.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// The name of the build and the modules it contains.
rootProject.name = "Timetables"

//   core    = pure Java: SNCF API access and the data model (no Swing, no Android).
//   desktop = Swing + console front-ends, depends on core.
//   android = Android app, depends on core.
include("core", "desktop")

// Eclipse (Buildship) does not understand the Android plugin: it passes
// -Ptimetables.skipAndroid=true (Preferences > Gradle > Arguments) to leave the module out.
if (!providers.gradleProperty("timetables.skipAndroid").isPresent) {
    include("android")
}
