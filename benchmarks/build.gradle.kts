import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("com.eventslooped.klyph")
    id("klyph.lint")
}

klyph {
    entryPoint = "klyph.benchmarks.main"
}

kotlin {
    sourceSets.nativeMain.dependencies {
        implementation(project(":klyph"))
    }
    // Drives the platform plumbing directly, as the engine does; games never opt in.
    sourceSets.all {
        languageSettings.optIn("klyph.platform.KlyphPlatformApi")
    }
}

// Names registered in klyph.benchmarks.Main; each gets a `benchmark<Name>` task. Windowed
// benchmarks also get `benchmark<Name>Offscreen`; headless ones open no window, so they do not.
val benchmarkNames = listOf("sprites", "hierarchy", "collisions", "particles")
val headlessBenchmarkNames = listOf("mixer")

// Benchmarks always run the host's release executable, which the klyph plugin creates once this
// script has run. The environment is the plugin's KLYPH_* passthrough (-Pklyph.sprites and the
// rest); `benchmark` picks the benchmark with -Pklyph.benchmark=<name>.
kotlin.targets.withType<KotlinNativeTarget>().matching { it.name == KlyphTargets.host }.configureEach {
    binaries.withType<Executable>().matching { it.buildType == NativeBuildType.RELEASE }.configureEach {
        val binary = this
        val environment = klyph.runEnvironment()
        val selected = providers.gradleProperty("klyph.benchmark").getOrElse(benchmarkNames.first())
        fun Exec.runs(vararg arguments: String, environment: Map<String, String>) {
            group = "benchmarks"
            dependsOn(binary.linkTaskProvider)
            executable(binary.outputFile)
            args(*arguments)
            environment(environment)
        }

        tasks.register<Exec>("benchmark") {
            description = "Runs the benchmark named by -Pklyph.benchmark (default ${benchmarkNames.first()}); release build."
            runs(selected, environment = environment)
        }
        tasks.register<Exec>("benchmarkList") {
            description = "Lists the available benchmarks."
            runs(environment = emptyMap())
        }
        for (name in benchmarkNames) {
            val taskName = "benchmark" + name.replaceFirstChar { it.uppercase() }
            tasks.register<Exec>(taskName) {
                description = "Runs the '$name' benchmark in a window; release build."
                runs(name, environment = environment)
            }
            tasks.register<Exec>("${taskName}Offscreen") {
                description = "Runs the '$name' benchmark offscreen with a GPU wait each frame, 2000 frames unless klyph.frames is set."
                runs(name, environment = klyph.runEnvironment(mapOf("KLYPH_FRAMES" to "2000")) + ("KLYPH_OFFSCREEN" to "1"))
            }
        }
        for (name in headlessBenchmarkNames) {
            tasks.register<Exec>("benchmark" + name.replaceFirstChar { it.uppercase() }) {
                description = "Runs the '$name' benchmark headless, without a window or a device; release build."
                runs(name, environment = environment)
            }
        }
    }
}
