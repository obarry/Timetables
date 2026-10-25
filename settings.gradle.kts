// The name of the build and the modules it contains.
rootProject.name = "Timetables"

// Step 3: two modules, each one a folder with its own build.gradle.kts.
//   core    = pure Java: SNCF API access and the data model (no Swing).
//   desktop = Swing + console front-ends, depends on core.
// An "android" module will be added here later.
include("core", "desktop")
