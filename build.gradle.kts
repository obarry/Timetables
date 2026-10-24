// Gradle build script for Timetables.
// Step 1: a single module, keeping the current source layout (src/com/timetables/...).

plugins {
    // Java compilation + the "run" task + packaging of a runnable application.
    application
}

repositories {
    // Where Gradle downloads dependencies from.
    mavenCentral()
}

dependencies {
    // Replaces the local jar C:/Users/olivi/Development/JSON/org.json-20120521.jar.
    // "implementation" = needed to compile and to run this module.
    implementation("org.json:json:20260814")
}

// Our sources live directly in "src" (Gradle's convention would be src/main/java).
sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
    }
}

tasks.withType<JavaCompile> {
    // Compile as Java 17 even though the JDK is 26: guarantees we never use newer
    // APIs, which keeps the code reusable later in an Android module.
    options.release.set(17)
    options.encoding = "UTF-8"
}

application {
    // Main class started by "gradlew run" (the Swing application).
    mainClass.set("com.timetables.TimetablesApp")
}

// "run" starts a separate process: give it the keyboard (useful for the console version).
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// A second launcher: "gradlew runConsole" starts the console version.
tasks.register<JavaExec>("runConsole") {
    group = "application"
    description = "Runs the console version (TimetablesConsole)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.timetables.TimetablesConsole")
    standardInput = System.`in`
}
