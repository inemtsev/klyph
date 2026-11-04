package klyph.benchmarks

import klyph.app.App
import klyph.app.AppConfig
import klyph.app.Phase
import klyph.event.KeyPressed
import klyph.platform.KeyCode
import kotlin.time.Duration

/** A named benchmark that [Main][main] can run from the command line. */
internal class Benchmark(
    /** The command-line argument that selects this benchmark. */
    val name: String,
    /** One line shown in the list of available benchmarks. */
    val description: String,
    /** Configures and runs a klyph app with [BenchmarkSettings], then prints its results. */
    val run: (BenchmarkSettings) -> Unit,
)

/** The settings every benchmark reads from the environment. */
internal class BenchmarkSettings(
    /** `KLYPH_SPRITES`, default 10000. */
    val spriteCount: Int,
    /** `KLYPH_CHILDREN`, default 8: children per parent in the hierarchy benchmark. */
    val childCount: Int,
    /** `KLYPH_TEXTURES`, default 1: textures the sprites benchmark spreads its sprites over. */
    val textureCount: Int,
    /**
     * `KLYPH_INTERPOLATE=1`: the sprites benchmark moves its sprites in `FIXED_UPDATE` and marks
     * them `Interpolated` instead of moving them in `UPDATE`.
     */
    val interpolate: Boolean,
    /**
     * `KLYPH_MUSIC`: an Ogg Vorbis file the mixer benchmark streams in place of its synthetic
     * music, to measure the decoding cost; `null` keeps the synthetic stream.
     */
    val musicPath: String?,
    /**
     * The app configuration every benchmark runs with: a window titled after the benchmark,
     * immediate presentation and the `KLYPH_CELL` grid cell size (default 64) as the base, with
     * every setting `AppConfig.fromEnvironment` reads (`KLYPH_FRAMES`, `KLYPH_SECONDS`,
     * `KLYPH_VSYNC`, `KLYPH_OFFSCREEN`, `KLYPH_VALIDATION`, `KLYPH_OVERLAY`, `KLYPH_FULLSCREEN`,
     * `KLYPH_SCREENSHOT`) applied on top.
     */
    val config: AppConfig,
) {
    /** `KLYPH_FRAMES`, which the engine quits after; `null` runs until the window closes or escape is pressed. */
    val frameLimit: Int? get() = config.frameLimit

    /**
     * `KLYPH_SECONDS`: the engine quits once `Time.elapsed` reaches it; `null` sets no limit.
     * Either limit ends the run.
     */
    val timeLimit: Duration? get() = config.timeLimit

    /** `KLYPH_CELL`, default 64: the collision grid's cell size in world units. */
    val cellSize: Float get() = config.collisionCellSize

    override fun toString(): String =
        "BenchmarkSettings(spriteCount=$spriteCount, childCount=$childCount, textureCount=$textureCount, interpolate=$interpolate, " +
            "musicPath=$musicPath, config=$config)"
}

/**
 * Adds what every benchmark app shares: a startup line naming the present mode and render target,
 * quitting on escape, and [report] fed with each frame's stats and printed at shutdown. The engine
 * itself quits after [BenchmarkSettings.frameLimit] frames or [BenchmarkSettings.timeLimit].
 */
internal fun App.addBenchmarkSystems(settings: BenchmarkSettings, report: StatsReport) {
    addSystem(Phase.STARTUP) { app ->
        println(
            "Present mode: ${app.presentMode} (requested: ${app.config.presentMode}), " +
                "validation: ${app.config.validation}, target: ${app.config.target}",
        )
    }
    addSystem(Phase.INPUT) { app ->
        // app.stats describes the previous frame, which does not exist yet during frame 0.
        if (app.time.frameCount > 0) report.record(app.stats)
        app.events.forEach<KeyPressed> { if (it.key == KeyCode.ESCAPE) app.quit() }
    }
    addSystem(Phase.SHUTDOWN) { app ->
        if (app.time.frameCount > 0) report.record(app.stats)
        report.printSummary()
    }
}
