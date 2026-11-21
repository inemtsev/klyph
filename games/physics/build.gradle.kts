// The physics playground: one scene per feature of klyph-physics, switched with the arrow keys or
// the number keys. `run` plays it from the debug build, `runRelease` from the release build, which
// is the one to time steps with; KLYPH_FRAMES, KLYPH_SECONDS and KLYPH_VALIDATION come from the
// shell or from -Pklyph.frames and so on.
plugins {
    id("com.eventslooped.klyph")
    id("klyph.lint")
}

klyph {
    entryPoint = "klyph.physics.demo.main"
}

kotlin {
    sourceSets.nativeMain.dependencies {
        implementation(project(":klyph-physics"))
    }
    sourceSets.nativeTest.dependencies {
        implementation(kotlin("test"))
    }
}

// HeadlessTest, MouseTest and SceneTest run every scene for hundreds of steps on the debug build,
// about ten minutes, so `check` leaves them out; -Pklyph.physics.sceneTests=1 runs them too.
val sceneTests = providers.gradleProperty("klyph.physics.sceneTests").isPresent
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    if (!sceneTests) {
        filter.excludeTestsMatching("klyph.physics.demo.HeadlessTest")
        filter.excludeTestsMatching("klyph.physics.demo.MouseTest")
        filter.excludeTestsMatching("klyph.physics.demo.SceneTest")
    }
}
