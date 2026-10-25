// Module "desktop": Swing application + console version, built on top of core.

plugins {
    // Java compilation + the "run" task + packaging of a runnable application.
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // desktop uses core's classes (APISNCF, BoardEntry...).
    // org.json comes along at run time only, through core's own dependencies.
    implementation(project(":core"))
}

tasks.withType<JavaCompile> {
    options.release.set(17)
    options.encoding = "UTF-8"
}

application {
    // Main class started by "gradlew :desktop:run" (the Swing application).
    mainClass.set("com.timetables.TimetablesApp")
}

// The programs read resources/Timetables.properties, which lives at the root of the
// repository. By default a run task starts in the module folder (desktop/), so we
// point it at the root folder instead.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    // Give the program the keyboard (useful for the console version).
    standardInput = System.`in`
}

// A second launcher: "gradlew :desktop:runConsole" starts the console version.
tasks.register<JavaExec>("runConsole") {
    group = "application"
    description = "Runs the console version (TimetablesConsole)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.timetables.TimetablesConsole")
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}
