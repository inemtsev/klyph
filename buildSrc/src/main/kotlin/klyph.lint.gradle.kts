// Convention plugin for every module of this repository: the rule that native interop stays
// inside klyph-platform, and detekt. The engine modules get it through klyph.native; the
// executables, which build with the com.eventslooped.klyph plugin, apply it next to that plugin.

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask

plugins {
    base
    id("dev.detekt")
}

// One configuration for the whole repository, config/detekt/detekt.yml, and one baseline per
// module next to its build file, which `detektBaseline` rewrites. `check` runs the plain `detekt`
// task over every Kotlin file under src/. Kotlin/Native source sets have no type resolution in
// detekt, so the rules that need it skip these modules; the per-source-set tasks the plugin also
// registers are left out of `check`.
detekt {
    config.setFrom(layout.settingsDirectory.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    baseline = layout.projectDirectory.file("detekt-baseline.xml")
    basePath = layout.settingsDirectory
    source.setFrom(layout.projectDirectory.dir("src"))
    parallel = true
}

// The libraries rule set (ForbiddenPublicDataClass) ships outside the detekt distribution, at the
// version of the plugin itself.
dependencies.addProvider("detektPlugins", detekt.toolVersion.map { "dev.detekt:detekt-rules-libraries:$it" })

// The compiled shaders (compileShaders) are generated into src.
tasks.withType<Detekt>().configureEach {
    exclude("**/generated/**")
    dependsOn(tasks.matching { it.name == "compileShaders" })
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    exclude("**/generated/**")
    dependsOn(tasks.matching { it.name == "compileShaders" })
}

val checkNoInteropImports = tasks.register("checkNoInteropImports") {
    description = "Fails if a source file outside klyph-platform imports native interop packages."
    group = "verification"
    val sources = fileTree("src") { include("**/*.kt") }
    inputs.files(sources)
    // Generated sources (compileShaders) land in src, so scan after they are written.
    dependsOn(tasks.matching { it.name == "compileShaders" })
    // Plain values only: the configuration cache cannot serialize references to the script itself.
    val root = projectDir
    val interopImport = Regex("""^\s*import\s+(kotlinx\.cinterop|sdl3|cnames|platform|stb|vorbis)\.""")
    doLast {
        val offenders = sources.files.filter { file ->
            file.useLines { lines -> lines.any { interopImport.containsMatchIn(it) } }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Native interop imports are only allowed in klyph-platform:\n" +
                    offenders.joinToString("\n") { "  " + it.relativeTo(root) },
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkNoInteropImports)
}
