// Module "core": SNCF (Navitia) API access, data model, platform providers.
// Pure Java, no Swing/AWT, so that the Android app can reuse it later.

plugins {
    // "java-library" = "java" + the "api" dependency configuration, for modules
    // that other modules depend on.
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // "implementation" = used inside core only. Modules that depend on core do NOT
    // see org.json (it is a private detail). Use "api(...)" instead if core's public
    // methods exposed org.json types (they don't).
    implementation("org.json:json:20260814")
}

tasks.withType<JavaCompile> {
    // Compile as Java 17 even though the JDK is 26: guarantees we never use newer
    // APIs, which keeps the code reusable later in an Android module.
    options.release.set(17)
    options.encoding = "UTF-8"
}

// ---- Tests ----------------------------------------------------------------------
dependencies {
    // A "BOM" (bill of materials) fixes the versions of all JUnit artifacts together,
    // so we only write the version number once.
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Gradle 9 no longer provides the JUnit launcher itself: it must be declared.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    // Tell Gradle the tests are JUnit Platform tests (JUnit 5 and later).
    useJUnitPlatform()
    testLogging {
        // Print one line per test in the console (by default only failures are shown).
        events("passed", "failed", "skipped")
    }
}
