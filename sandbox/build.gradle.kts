// `run` opens the sandbox window from the debug build; KLYPH_FRAMES, KLYPH_VALIDATION and
// KLYPH_OVERLAY come from the shell or from -Pklyph.frames, -Pklyph.validation, -Pklyph.overlay.
plugins {
    id("com.eventslooped.klyph")
    id("klyph.lint")
}

klyph {
    entryPoint = "klyph.sandbox.main"
}

kotlin {
    sourceSets.nativeMain.dependencies {
        implementation(project(":klyph"))
    }
}
