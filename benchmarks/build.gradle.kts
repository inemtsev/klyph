import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("klyph.native")
}

// Names registered in klyph.benchmarks.Main; each gets a `benchmark<Name>` task. Windowed
// benchmarks also get `benchmark<Name>Offscreen`; headless ones open no window, so they do not.
val benchmarkNames = listOf("sprites", "hierarchy")
val headlessBenchmarkNames = listOf("mixer")

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            executable {
                entryPoint = "klyph.benchmarks.main"
            }
        }

        // Benchmarks always run from the release build. Environment comes from the shell or from
        // Gradle properties: klyph.sprites, klyph.children, klyph.textures, klyph.frames, klyph.offscreen, klyph.vsync,
        // klyph.validation, klyph.music. `benchmark` picks the benchmark with -Pklyph.benchmark=<name>.
        binaries.withType<Executable>().matching { it.buildType == NativeBuildType.RELEASE }.configureEach {
            val environment = RunEnvironment.of(project)
            val selected = providers.gradleProperty("klyph.benchmark").getOrElse(benchmarkNames.first())

            tasks.register<Exec>("benchmark") {
                group = "benchmarks"
                description = "Runs the benchmark named by -Pklyph.benchmark (default ${benchmarkNames.first()}); release build."
                dependsOn(linkTaskProvider)
                executable(outputFile)
                args(selected)
                environment(environment)
            }
            tasks.register<Exec>("benchmarkList") {
                group = "benchmarks"
                description = "Lists the available benchmarks."
                dependsOn(linkTaskProvider)
                executable(outputFile)
            }
            for (name in benchmarkNames) {
                val taskName = "benchmark" + name.replaceFirstChar { it.uppercase() }
                tasks.register<Exec>(taskName) {
                    group = "benchmarks"
                    description = "Runs the '$name' benchmark in a window; release build."
                    dependsOn(linkTaskProvider)
                    executable(outputFile)
                    args(name)
                    environment(environment)
                }
                tasks.register<Exec>("${taskName}Offscreen") {
                    group = "benchmarks"
                    description = "Runs the '$name' benchmark offscreen with a GPU wait each frame, 2000 frames unless klyph.frames is set."
                    dependsOn(linkTaskProvider)
                    executable(outputFile)
                    args(name)
                    environment(RunEnvironment.of(project, mapOf("KLYPH_FRAMES" to "2000")) + mapOf("KLYPH_OFFSCREEN" to "1"))
                }
            }
            for (name in headlessBenchmarkNames) {
                tasks.register<Exec>("benchmark" + name.replaceFirstChar { it.uppercase() }) {
                    group = "benchmarks"
                    description = "Runs the '$name' benchmark headless, without a window or a device; release build."
                    dependsOn(linkTaskProvider)
                    executable(outputFile)
                    args(name)
                    environment(environment)
                }
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(project(":klyph"))
            implementation(project(":klyph-platform"))
        }
    }
}
