package klyph.benchmarks

import klyph.app.App
import klyph.app.AppConfig
import klyph.app.GpuSync
import klyph.app.Phase
import klyph.app.RenderTarget
import klyph.event.KeyPressed
import klyph.platform.GpuValidation
import klyph.platform.KeyCode
import klyph.platform.PresentMode
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
    /** `KLYPH_FRAMES`; `null` runs until the window closes or escape is pressed. */
    val frameLimit: Int?,
    /**
     * `KLYPH_SECONDS`: the app quits once `Time.elapsed` reaches it; `null` sets no limit. Either
     * limit ends the run.
     */
    val timeLimit: Duration?,
    /**
     * `KLYPH_OFFSCREEN=1`: draw into a texture and wait for the GPU each frame instead of
     * presenting, so the display is not in the loop.
     */
    val offscreen: Boolean,
    /** `KLYPH_VSYNC=1` presents at the display rate; otherwise frames present immediately. */
    val presentMode: PresentMode,
    /** `KLYPH_VALIDATION=1` turns on the GPU validation layer. */
    val validation: GpuValidation,
    /**
     * `KLYPH_MUSIC`: an Ogg Vorbis file the mixer benchmark streams in place of its synthetic
     * music, to measure the decoding cost; `null` keeps the synthetic stream.
     */
    val musicPath: String?,
) {
    /** Returns the app configuration for a benchmark whose window is titled [title]. */
    fun appConfig(title: String): AppConfig =
        AppConfig(
            title = title,
            presentMode = presentMode,
            target = if (offscreen) RenderTarget.OFFSCREEN else RenderTarget.WINDOW,
            gpuSync = if (offscreen) GpuSync.WAIT_EACH_FRAME else GpuSync.NONE,
            validation = validation,
        )

    override fun toString(): String =
        "BenchmarkSettings(spriteCount=$spriteCount, childCount=$childCount, textureCount=$textureCount, interpolate=$interpolate, frameLimit=$frameLimit, timeLimit=$timeLimit, " +
            "offscreen=$offscreen, presentMode=$presentMode, validation=$validation, musicPath=$musicPath)"
}

/**
 * Adds what every benchmark app shares: a startup line naming the present mode and render target,
 * quitting on escape, after [BenchmarkSettings.frameLimit] frames or after
 * [BenchmarkSettings.timeLimit], and [report] fed with each
 * frame's stats and printed at shutdown.
 */
internal fun App.addBenchmarkSystems(settings: BenchmarkSettings, report: StatsReport) {
    addSystem(Phase.STARTUP) { app ->
        println(
            "Present mode: ${app.presentMode} (requested: ${app.config.presentMode}), " +
                "validation: ${settings.validation}, target: ${app.config.target}",
        )
    }
    addSystem(Phase.INPUT) { app ->
        // app.stats describes the previous frame, which does not exist yet during frame 0.
        if (app.time.frame > 0) report.record(app.stats)
        app.events.forEach<KeyPressed> { if (it.key == KeyCode.ESCAPE) app.quit() }
        // time.frame counts completed frames, so this frame is number frame + 1.
        val limit = settings.frameLimit
        if (limit != null && app.time.frame + 1 >= limit) app.quit()
        val timeLimit = settings.timeLimit
        if (timeLimit != null && app.time.elapsed >= timeLimit) app.quit()
    }
    addSystem(Phase.SHUTDOWN) { app ->
        if (app.time.frame > 0) report.record(app.stats)
        report.printSummary()
    }
}
