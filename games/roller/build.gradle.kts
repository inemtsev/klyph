// Rooftop Roller. `run` plays it from the debug build and `distribute` zips the release build with
// assets/ and SDL3; KLYPH_AUTOPILOT, KLYPH_FRAMES, KLYPH_SECONDS and KLYPH_VALIDATION come from
// the shell or from -Pklyph.autopilot, -Pklyph.frames and so on.
plugins {
    id("com.eventslooped.klyph")
    id("klyph.lint")
}

klyph {
    entryPoint = "klyph.roller.main"
}

kotlin {
    sourceSets.nativeMain.dependencies {
        implementation(project(":klyph"))
    }
}
