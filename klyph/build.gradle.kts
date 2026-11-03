plugins {
    id("klyph.native")
    id("klyph.publish")
}

description = "A 2D game engine in pure Kotlin/Native on SDL3 and SDL_GPU."

kotlin {
    explicitApi()

    sourceSets {
        nativeMain.dependencies {
            api(project(":klyph-platform"))
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// HLSL in src/shaders is compiled by SDL_shadercross into a checked-in Kotlin file. The task is
// skipped, with a warning, when the tool is not installed, and the checked-in file is used as is.
val shadercrossPath: String = providers.gradleProperty("klyph.shadercross")
    .getOrElse(System.getProperty("user.home") + "/.local/shadercross/bin/shadercross")

val compileShaders = tasks.register<CompileShadersTask>("compileShaders") {
    description = "Compiles src/shaders/*.hlsl with SDL_shadercross into generated Kotlin."
    group = "build"
    sources.from(fileTree("src/shaders") { include("*.hlsl") })
    tool.set(shadercrossPath)
    packageName.set("klyph.render.generated")
    output.set(file("src/nativeMain/kotlin/klyph/render/generated/Shaders.kt"))
    workDir.set(layout.buildDirectory.dir("shaders"))
    // Plain locals only: the configuration cache cannot serialize references to the build script.
    val toolFile = file(shadercrossPath)
    val toolPath = shadercrossPath
    onlyIf("SDL_shadercross is installed at $toolPath") { task ->
        toolFile.canExecute().also { present ->
            if (!present) task.logger.warn("Skipping compileShaders: no shadercross at $toolPath; using the checked-in Shaders.kt.")
        }
    }
}

// Everything that reads src/nativeMain runs after the shaders are generated into it: the Kotlin
// compilations of every target, the shared metadata compilation and the published sources jars.
tasks.matching {
    it.name.startsWith("compileKotlin") || it.name.endsWith("KotlinMetadata") ||
        it.name == "sourcesJar" || it.name.endsWith("SourcesJar")
}.configureEach {
    dependsOn(compileShaders)
}
