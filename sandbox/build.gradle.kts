import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("klyph.native")
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            executable {
                entryPoint = "klyph.sandbox.main"
            }
        }

        // `run` opens the sandbox window from the debug build. Environment: KLYPH_FRAMES,
        // KLYPH_VALIDATION, or the Gradle properties klyph.frames and klyph.validation.
        binaries.withType<Executable>().matching { it.buildType == NativeBuildType.DEBUG }.configureEach {
            val environment = RunEnvironment.of(project)
            tasks.register<Exec>("run") {
                group = "application"
                description = "Runs the sandbox smoke test (debug build)."
                dependsOn(linkTaskProvider)
                executable(outputFile)
                environment(environment)
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
