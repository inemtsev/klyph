import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("klyph.native")
}

// Copies assets/ next to every squish executable, for the binaries of every target.
val copyAssets = tasks.register("copyAssets") {
    group = "application"
    description = "Copies squish/assets/ into the output folder of every squish executable."
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            executable {
                entryPoint = "klyph.squish.main"
            }
        }

        // The game loads its art from the assets/ folder next to the executable, so every link
        // is followed by a sync of squish/assets/ into <output folder>/assets/. The sync runs
        // after the link, not before, so a link restored from the build cache cannot wipe it.
        binaries.withType<Executable>().configureEach {
            val binaryAssets = tasks.register<Sync>("copyAssets" + linkTaskName.removePrefix("link")) {
                description = "Copies squish/assets/ next to the $name binary."
                from(layout.projectDirectory.dir("assets")) {
                    exclude("README.md")
                }
                into(outputDirectory.resolve("assets"))
            }
            val link = linkTaskProvider
            binaryAssets.configure { mustRunAfter(link) }
            link.configure { finalizedBy(binaryAssets) }
            copyAssets.configure { dependsOn(binaryAssets) }

            // `run` plays the game from the debug build. Environment: KLYPH_FRAMES,
            // KLYPH_SECONDS, KLYPH_VALIDATION, KLYPH_AUTOPILOT, or the Gradle properties
            // klyph.frames, klyph.seconds, klyph.validation and klyph.autopilot.
            if (buildType == NativeBuildType.DEBUG) {
                val environment = RunEnvironment.of(project)
                tasks.register<Exec>("run") {
                    group = "application"
                    description = "Runs the squish game (debug build)."
                    dependsOn(link, binaryAssets)
                    executable(outputFile)
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
