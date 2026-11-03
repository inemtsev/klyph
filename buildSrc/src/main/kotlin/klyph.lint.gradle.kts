// Convention plugin for every module of this repository: the rule that native interop stays
// inside klyph-platform. The engine modules get it through klyph.native; the executables, which
// build with the com.eventslooped.klyph plugin, apply it next to that plugin.

plugins {
    base
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
