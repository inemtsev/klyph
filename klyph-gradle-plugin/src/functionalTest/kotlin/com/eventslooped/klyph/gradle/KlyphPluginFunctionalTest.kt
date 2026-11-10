package com.eventslooped.klyph.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Applies the plugin to a one-file project with a fake SDL3 prefix. Nothing is compiled: these
 * tests check the tasks the plugin registers and that configuration is cache-safe.
 */
class KlyphPluginFunctionalTest {
    private val projectDir: File = createTempDirectory("klyph-plugin-test").toFile()
    private val sdl3Prefix = File(projectDir, "fake-sdl3")

    private val hostOs = System.getProperty("os.name")
    private val hostTarget = when {
        hostOs == "Mac OS X" -> "macosArm64"
        hostOs.startsWith("Windows") -> "mingwX64"
        System.getProperty("os.arch") == "aarch64" -> "linuxArm64"
        else -> "linuxX64"
    }
    private val hostTargetTitle = hostTarget.replaceFirstChar { it.uppercase() }

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    private fun writeProject(klyphBlock: String) {
        File(sdl3Prefix, "include/SDL3").mkdirs()
        File(sdl3Prefix, "include/SDL3/SDL.h").writeText("// fake\n")
        File(sdl3Prefix, "lib").mkdirs()
        File(projectDir, "settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "hello"
            """.trimIndent(),
        )
        File(projectDir, "gradle.properties").writeText("org.gradle.configuration-cache=true\n")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins { id("com.eventslooped.klyph") }
            $klyphBlock
            """.trimIndent(),
        )
        File(projectDir, "src/nativeMain/kotlin").mkdirs()
        File(projectDir, "src/nativeMain/kotlin/Main.kt").writeText("package hello\n\nfun main() = println(\"hello\")\n")
    }

    private fun prefixBlock(): String =
        """
        klyph {
            entryPoint = "hello.main"
            sdl3Prefix = "${sdl3Prefix.invariantSeparatorsPath}"
        }
        """.trimIndent()

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()

    @Test
    fun registersRunCopyAssetsAndDistribute() {
        writeProject(prefixBlock())
        val output = runner("tasks", "--all").build().output
        for (task in listOf(
            "run",
            "runRelease",
            "copyAssets",
            "copyAssetsDebugExecutable$hostTargetTitle",
            "copyAssetsReleaseExecutable$hostTargetTitle",
            "distribute",
            "distribute$hostTargetTitle",
            "prepareDistribution$hostTargetTitle",
            "linkDebugExecutable$hostTargetTitle",
        )) {
            assertTrue(Regex("^$task - ", RegexOption.MULTILINE).containsMatchIn(output), "Task $task is missing:\n$output")
        }
        assertFalse(Regex("^fetchSdl3", RegexOption.MULTILINE).containsMatchIn(output), "fetchSdl3 is registered although the prefix has SDL3.")
    }

    @Test
    fun configurationCacheIsReused() {
        writeProject(prefixBlock())
        val first = runner("help", "--configuration-cache").build().output
        assertContains(first, "Configuration cache entry stored.")
        val second = runner("help", "--configuration-cache").build().output
        assertContains(second, "Configuration cache entry reused.")
    }

    @Test
    fun missingEntryPointFails() {
        writeProject("klyph { sdl3Prefix = \"${sdl3Prefix.invariantSeparatorsPath}\" }")
        val output = runner("help").buildAndFail().output
        assertContains(output, "Set klyph { entryPoint = \"my.game.main\" }.")
    }

    @Test
    fun libraryHasNoExecutableOrItsTasks() {
        writeProject("klyph { sdl3Prefix = \"${sdl3Prefix.invariantSeparatorsPath}\" }")
        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace("com.eventslooped.klyph\")", "com.eventslooped.klyph.library\")") + """

            tasks.register("describe") {
                val host = klyph.hostTarget
                val configured = klyph.configuredTargets
                val include = klyph.sdl3IncludeDir.map { it.asFile.invariantSeparatorsPath }
                doLast { println("host: " + host.get() + ", targets: " + configured.get() + ", include: " + include.get()) }
            }
            """.trimIndent(),
        )
        val output = runner("tasks", "--all").build().output
        assertTrue(hasTask(output, "linkDebugTest$hostTargetTitle"), "The test binary's link task is missing:\n$output")
        for (task in listOf("run", "runRelease", "copyAssets", "distribute", "linkDebugExecutable$hostTargetTitle")) {
            assertFalse(hasTask(output, task), "A library registers $task:\n$output")
        }
        val described = runner("describe").build().output
        assertContains(described, "host: $hostTarget, targets: [$hostTarget], include: ${sdl3Prefix.invariantSeparatorsPath}/include")
        assertContains(runner("describe").build().output, "Configuration cache entry reused.")
    }

    @Test
    fun noDownloadPropertyStopsWithoutSdl3() {
        writeProject("klyph { entryPoint = \"hello.main\" }")
        val output = runner("help", "-Pklyph.sdl3.ignoreSystem=true", "-Pklyph.sdl3.download=false").buildAndFail().output
        assertContains(output, "Cannot find SDL3: no include/SDL3/SDL.h under")
    }

    /** A target other than the host's, whose executables cannot link against the host's SDL3. */
    private val foreignTarget = if (hostTarget == "mingwX64") "linuxX64" else "mingwX64"
    private val foreignTargetTitle = foreignTarget.replaceFirstChar { it.uppercase() }

    private fun hasTask(output: String, task: String): Boolean = Regex("^$task - ", RegexOption.MULTILINE).containsMatchIn(output)

    @Test
    fun foreignExecutablesNextToTheHostCompileButDoNotLink() {
        writeProject(
            prefixBlock() + """

            tasks.register("linkEnabled") {
                val enabled = provider {
                    listOf("$hostTargetTitle", "$foreignTargetTitle").map { it to tasks.getByName("linkReleaseExecutable" + it).enabled }
                }
                doLast { println("link enabled: " + enabled.get()) }
            }
            """.trimIndent(),
        )
        val arguments = arrayOf("-Pklyph.targets=host,$foreignTarget")
        val tasks = runner("tasks", "--all", *arguments).build().output
        assertTrue(hasTask(tasks, "linkReleaseExecutable$foreignTargetTitle"), "The $foreignTarget link task is missing:\n$tasks")
        assertTrue(hasTask(tasks, "distribute$hostTargetTitle"), "distribute$hostTargetTitle is missing:\n$tasks")
        assertFalse(hasTask(tasks, "distribute$foreignTargetTitle"), "distribute$foreignTargetTitle is registered although its link is off:\n$tasks")
        val enabled = runner("linkEnabled", "--no-configuration-cache", *arguments).build().output
        assertContains(enabled, "link enabled: [($hostTargetTitle, true), ($foreignTargetTitle, false)]")
    }

    @Test
    fun crossBuildWithoutTheHostLinksEverything() {
        writeProject(prefixBlock())
        val output = runner("tasks", "--all", "-Pklyph.target=$foreignTarget").build().output
        assertTrue(hasTask(output, "distribute$foreignTargetTitle"), "distribute$foreignTargetTitle is missing:\n$output")
        assertFalse(hasTask(output, "run"), "run is registered for a target that cannot run here:\n$output")
    }

    @Test
    fun bothTargetPropertiesFail() {
        writeProject(prefixBlock())
        val output = runner("help", "-Pklyph.targets=host", "-Pklyph.target=$foreignTarget").buildAndFail().output
        assertContains(output, "Set klyph.targets or klyph.target, not both: klyph.targets=host, klyph.target=$foreignTarget.")
    }

    @Test
    fun assetSyncLeavesTheReadmeOut() {
        writeProject(prefixBlock())
        File(projectDir, "assets/sounds").mkdirs()
        File(projectDir, "assets/README.md").writeText("Where the art comes from.\n")
        File(projectDir, "assets/ship.png").writeText("not really a png\n")
        File(projectDir, "assets/sounds/shot.wav").writeText("not really a wav\n")
        val copied = File(projectDir, "build/bin/$hostTarget/debugExecutable/assets")
        val sync = "copyAssetsDebugExecutable$hostTargetTitle"

        runner(sync).build()
        assertTrue(File(copied, "ship.png").isFile, "ship.png was not copied.")
        assertTrue(File(copied, "sounds/shot.wav").isFile, "sounds/shot.wav was not copied.")
        assertFalse(File(copied, "README.md").exists(), "README.md was copied although assetExcludes leaves it out.")

        File(projectDir, "build.gradle.kts").appendText("\nklyph { assetExcludes = listOf(\"*.png\") }\n")
        runner(sync).build()
        assertTrue(File(copied, "README.md").isFile, "README.md was not copied once assetExcludes no longer names it.")
        assertFalse(File(copied, "ship.png").exists(), "ship.png was copied although assetExcludes names *.png.")
    }

    @Test
    fun blankPrefixPropertyIsIgnoredAndOthersNameTheirSource() {
        writeProject(prefixBlock())
        val blank = runner("help", "-Psdl3.prefix=").build().output
        assertFalse(blank.contains("has no include/SDL3/SDL.h"), "A blank -Psdl3.prefix logged a warning:\n$blank")
        val missing = File(projectDir, "no-sdl3").invariantSeparatorsPath
        val wrong = runner("help", "-Psdl3.prefix=$missing").build().output
        assertContains(wrong, "klyph: SDL3 prefix $missing from -Psdl3.prefix has no include/SDL3/SDL.h; looking further.")
    }

    @Test
    fun oneFetchForEveryProjectOfTheBuild() {
        if (hostTarget.startsWith("linux")) return // Linux has no SDL3 release to download.
        File(projectDir, "settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "games"
            include("first", "second")
            """.trimIndent(),
        )
        File(projectDir, "gradle.properties").writeText("org.gradle.configuration-cache=true\n")
        for (game in listOf("first", "second")) {
            File(projectDir, "$game/src/nativeMain/kotlin").mkdirs()
            File(projectDir, "$game/src/nativeMain/kotlin/Main.kt").writeText("package $game\n\nfun main() = println(\"$game\")\n")
            File(projectDir, "$game/build.gradle.kts").writeText(
                """
                plugins { id("com.eventslooped.klyph") }
                klyph { entryPoint = "$game.main" }
                """.trimIndent(),
            )
        }
        val output = runner("tasks", "--all", "-Pklyph.sdl3.ignoreSystem=true").build().output
        val fetches = Regex("^(\\S*:)?fetchSdl3 - ", RegexOption.MULTILINE).findAll(output).map { it.value }.toList()
        assertEquals(listOf("fetchSdl3 - "), fetches, "Expected one fetchSdl3, on the root project:\n$output")
        assertTrue(hasTask(output, "first:linkDebugExecutable$hostTargetTitle"), "first has no link task:\n$output")
        assertTrue(hasTask(output, "second:linkDebugExecutable$hostTargetTitle"), "second has no link task:\n$output")
    }

    @Test
    fun noSdl3RegistersFetchOrNamesLinuxPackages() {
        writeProject(
            """
            klyph { entryPoint = "hello.main" }
            """.trimIndent(),
        )
        val arguments = arrayOf("tasks", "--all", "-Pklyph.sdl3.ignoreSystem=true")
        if (hostTarget.startsWith("linux")) {
            val output = runner(*arguments).buildAndFail().output
            assertContains(output, "libsdl3-dev")
        } else {
            val output = runner(*arguments).build().output
            assertTrue(Regex("^fetchSdl3 - ", RegexOption.MULTILINE).containsMatchIn(output), "fetchSdl3 is missing:\n$output")
        }
    }
}
